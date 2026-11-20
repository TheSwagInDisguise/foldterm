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
        /** Hides a notification and brings it back after [ms]. */
        fun snooze(key: String, ms: Long): Boolean = runCatching { instance?.snoozeNotification(key, ms); instance != null }.getOrDefault(false)

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
            actions = n.actions?.filter { a -> a !== reply && a.actionIntent != null && !a.title.isNullOrBlank() }.orEmpty().take(3),
        )
    }
}

object NowPlayingStore {
    val state = MutableStateFlow<NowPlaying?>(null)
    /** Every app with an active media session, playing first. */
    val sessions = MutableStateFlow<List<MediaSessionInfo>>(emptyList())
    private var controllers: List<MediaController> = emptyList()
    private var controller: MediaController? = null
    /** Package the user picked in the media pane; sticks until that session goes away. */
    private var pinnedPkg: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controllers = controllers.filter { runCatching { it.playbackState; true }.getOrDefault(false) }
            if (controller != null && controller !in controllers) controller = null
            publish()
        }
    }

    fun setControllers(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { runCatching { it.registerCallback(callback, handler) } }
        controller = pickController()
        publish()
    }

    private fun pickController(): MediaController? =
        controllers.firstOrNull { it.packageName == pinnedPkg }
            ?: controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controller?.takeIf { it in controllers }
            ?: controllers.firstOrNull()

    fun select(pkg: String) {
        pinnedPkg = pkg
        controller = pickController()
        publish()
    }

    private fun publish() {
        sessions.value = controllers.map { c ->
            val md = c.metadata
            MediaSessionInfo(
                pkg = c.packageName,
                title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.description?.title?.toString() ?: "",
                isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            )
        }.sortedByDescending { it.isPlaying }
        // follow whatever starts playing unless the user pinned a session
        if (pinnedPkg == null || controllers.none { it.packageName == pinnedPkg }) {
            pinnedPkg = null
            controller = pickController()
        }
        val c = controller
        if (c == null) { state.value = null; return }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md?.description?.title?.toString() ?: ""
        if (title.isBlank()) { state.value = null; return }
        val ps = c.playbackState
        state.value = NowPlaying(
            title = title,
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            pkg = c.packageName,
            isPlaying = ps?.state == PlaybackState.STATE_PLAYING,
            art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART),
            album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: "",
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0 } ?: 0L,
            positionMs = ps?.position?.coerceAtLeast(0) ?: 0L,
            positionAt = ps?.lastPositionUpdateTime ?: 0L,
            speed = ps?.playbackSpeed ?: 1f,
            canSeek = (ps?.actions ?: 0L) and PlaybackState.ACTION_SEEK_TO != 0L,
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
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms.coerceAtLeast(0)) }
    fun sessionPackage(): String? = controller?.packageName
}
