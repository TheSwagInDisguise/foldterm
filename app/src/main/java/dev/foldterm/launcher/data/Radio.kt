package dev.foldterm.launcher.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow

data class Station(val name: String, val url: String)

/**
 * Internet radio. Streams with MediaPlayer and publishes a MediaSession, so the dock,
 * the Media pane and the lock screen can show and control it like any other player.
 */
class Radio(private val ctx: Context) {
    val playing = MutableStateFlow<Station?>(null)
    val status = MutableStateFlow("") // "", "buffering…", "playing", error text
    private var player: MediaPlayer? = null
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener { change -> if (change == AudioManager.AUDIOFOCUS_LOSS) stop() else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause() }
        .build()
    private val session = MediaSession(ctx, "FoldTermRadio").apply {
        setCallback(object : MediaSession.Callback() {
            override fun onPlay() { resume() }
            override fun onPause() { pause() }
            override fun onStop() { stop() }
        })
    }

    fun play(s: Station) {
        stop()
        audio.requestAudioFocus(focus)
        playing.value = s
        status.value = "buffering…"
        publish(PlaybackState.STATE_BUFFERING)
        player = MediaPlayer().apply {
            setAudioAttributes(attrs)
            setOnPreparedListener { it.start(); status.value = "playing"; publish(PlaybackState.STATE_PLAYING) }
            setOnErrorListener { _, what, _ -> status.value = "couldn't play this stream ($what)"; publish(PlaybackState.STATE_ERROR); true }
            runCatching { setDataSource(s.url); prepareAsync() }.onFailure { status.value = "bad stream url" }
        }
    }

    fun pause() {
        runCatching { player?.pause() }
        if (playing.value != null) { status.value = "paused"; publish(PlaybackState.STATE_PAUSED) }
    }

    fun resume() {
        val p = player ?: run { playing.value?.let { play(it) }; return }
        audio.requestAudioFocus(focus)
        runCatching { p.start() }
        status.value = "playing"; publish(PlaybackState.STATE_PLAYING)
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        audio.abandonAudioFocusRequest(focus)
        playing.value = null
        status.value = ""
        publish(PlaybackState.STATE_STOPPED)
        session.isActive = false
    }

    fun isPlaying() = runCatching { player?.isPlaying == true }.getOrDefault(false)

    private fun publish(state: Int) {
        val s = playing.value
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, s?.name ?: "Radio")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "FoldTerm radio")
                .build(),
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_PLAY_PAUSE)
                .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build(),
        )
        session.isActive = state != PlaybackState.STATE_STOPPED
    }

    fun release() { stop(); session.release() }

    companion object {
        val DEFAULTS = listOf(
            Station("Groove Salad", "https://ice1.somafm.com/groovesalad-128-mp3"),
            Station("DEF CON Radio", "https://ice1.somafm.com/defcon-128-mp3"),
            Station("Drone Zone", "https://ice1.somafm.com/dronezone-128-mp3"),
            Station("Secret Agent", "https://ice1.somafm.com/secretagent-128-mp3"),
            Station("Lush", "https://ice1.somafm.com/lush-128-mp3"),
            Station("Space Station Soma", "https://ice1.somafm.com/spacestation-128-mp3"),
            Station("Indie Pop Rocks!", "https://ice1.somafm.com/indiepop-128-mp3"),
            Station("Left Coast 70s", "https://ice1.somafm.com/seventies-128-mp3"),
        )
    }
}
