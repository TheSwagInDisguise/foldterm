package dev.foldterm.launcher.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Feeds the Mail / Chat panes (from notifications of mail & messaging apps) and the
 * dock's now-playing widget (MediaSessionManager requires an enabled listener).
 */
class NotifListener : NotificationListenerService() {
    private var sessionManager: MediaSessionManager? = null
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        NowPlayingStore.setControllers(list ?: emptyList())
    }

    override fun onListenerConnected() {
        instance = this
        NotificationStore.connected.value = true
        NotificationStore.replaceActive(runCatching { activeNotifications?.toList() }.getOrNull() ?: emptyList(), this)
        val msm = getSystemService(MediaSessionManager::class.java)
        sessionManager = msm
        val cn = ComponentName(this, NotifListener::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, cn)
            NowPlayingStore.setControllers(msm.getActiveSessions(cn))
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        NotificationStore.connected.value = false
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationStore.post(sbn, this)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationStore.markRemoved(sbn.key)
    }

    companion object {
        @Volatile var instance: NotifListener? = null
        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }
    }
}

object NotificationStore {
    val connected = MutableStateFlow(false)
    val items = MutableStateFlow<List<Notif>>(emptyList())

    private val mailPkgs = setOf(
        "com.google.android.gm", "com.microsoft.office.outlook", "com.fsck.k9", "eu.faircode.email",
        "ch.protonmail.android", "me.proton.android.mail", "com.yahoo.mobile.client.android.mail",
        "com.samsung.android.email.provider", "com.readdle.spark", "net.thunderbird.android",
        "com.tutanota", "de.tutao.tutanota",
    )
    private val chatPkgs = setOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.whatsapp",
        "org.telegram.messenger", "org.thoughtcrime.securesms", "com.facebook.orca", "com.discord",
        "com.Slack", "com.microsoft.teams", "com.google.android.apps.dynamite", "com.instagram.android",
        "com.viber.voip", "jp.naver.line.android", "com.beeper.android", "im.vector.app",
    )

    fun replaceActive(list: List<StatusBarNotification>, ctx: Context) {
        items.value = list.mapNotNull { toNotif(it, ctx) }.sortedByDescending { it.time }
    }

    fun post(sbn: StatusBarNotification, ctx: Context) {
        val n = toNotif(sbn, ctx) ?: return
        items.update { cur -> (listOf(n) + cur.filter { it.key != n.key }).take(200) }
    }

    fun markRemoved(key: String) {
        items.update { cur -> cur.map { if (it.key == key) it.copy(removed = true) else it } }
    }

    private fun toNotif(sbn: StatusBarNotification, ctx: Context): Notif? {
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return null
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            ?: ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val style = runCatching { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n) }.getOrNull()
        val messages = style?.messages?.joinToString("\n") { m ->
            val who = m.person?.name?.toString() ?: "me"
            "$who: ${m.text}"
        }
        if (title.isBlank() && text.isBlank() && messages.isNullOrBlank()) return null
        val pkg = sbn.packageName
        val kind = when {
            n.category == Notification.CATEGORY_EMAIL || pkg in mailPkgs -> NotifKind.MAIL
            n.category == Notification.CATEGORY_MESSAGE || pkg in chatPkgs || style != null -> NotifKind.CHAT
            else -> NotifKind.OTHER
        }
        val reply = n.actions?.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }
        val pm = ctx.packageManager
        val app = runCatching {
            @Suppress("DEPRECATION")
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrElse { pkg.substringAfterLast('.') }
        return Notif(
            key = sbn.key,
            pkg = pkg,
            app = app,
            title = title,
            text = text,
            body = messages?.takeIf { it.isNotBlank() } ?: big ?: text,
            time = sbn.postTime,
            kind = kind,
            contentIntent = n.contentIntent,
            replyAction = reply,
        )
    }
}

object NowPlayingStore {
    val state = MutableStateFlow<NowPlaying?>(null)
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    fun setControllers(list: List<MediaController>) {
        val pick = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (pick?.sessionToken != controller?.sessionToken) {
            runCatching { controller?.unregisterCallback(callback) }
            controller = pick
            runCatching { pick?.registerCallback(callback, handler) }
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) { state.value = null; return }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.description?.title?.toString() ?: ""
        if (title.isBlank()) { state.value = null; return }
        state.value = NowPlaying(
            title = title,
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            pkg = c.packageName,
            isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
        )
    }

    fun toggle() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun play() { controller?.transportControls?.play() }
    fun pause() { controller?.transportControls?.pause() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
    fun sessionPackage(): String? = controller?.packageName
}
