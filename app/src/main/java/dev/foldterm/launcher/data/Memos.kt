package dev.foldterm.launcher.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

data class Memo(val file: File, val created: Long, val durationMs: Long)

/** Voice memos recorded to app storage (AAC in .m4a). */
class Memos(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "memos").apply { mkdirs() }
    val list = MutableStateFlow(load())
    val recordingSince = MutableStateFlow(0L)
    val playing = MutableStateFlow<String?>(null)
    private var recorder: MediaRecorder? = null
    private var current: File? = null
    private var player: MediaPlayer? = null

    private fun load(): List<Memo> = (dir.listFiles()?.toList() ?: emptyList()).filter { it.extension == "m4a" }
        .map { f -> Memo(f, f.lastModified(), duration(f)) }.sortedByDescending { it.created }

    private fun duration(f: File): Long = runCatching {
        MediaMetadataRetriever().run { setDataSource(f.absolutePath); val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L; release(); d }
    }.getOrDefault(0L)

    /** Returns an error message, or null when recording started. */
    fun start(): String? {
        if (recorder != null) return null
        stopPlayback()
        val f = File(dir, "memo-${System.currentTimeMillis()}.m4a")
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            recorder = r; current = f; recordingSince.value = System.currentTimeMillis()
            null
        } catch (e: Exception) {
            f.delete(); e.message ?: "couldn't start recording"
        }
    }

    fun stop() {
        val r = recorder ?: return
        runCatching { r.stop() }.onFailure { current?.delete() }
        r.release(); recorder = null; current = null; recordingSince.value = 0
        list.value = load()
    }

    fun play(m: Memo) {
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(m.file.absolutePath)
            setOnCompletionListener { playing.value = null }
            prepare(); start()
        }
        playing.value = m.file.name
    }

    fun stopPlayback() { runCatching { player?.stop(); player?.release() }; player = null; playing.value = null }

    fun delete(m: Memo) { if (playing.value == m.file.name) stopPlayback(); m.file.delete(); list.value = load() }

    fun rename(m: Memo, name: String) {
        val safe = name.replace(Regex("[^A-Za-z0-9 _-]"), "").trim().ifBlank { return }
        m.file.renameTo(File(dir, "$safe.m4a")); list.value = load()
    }

    fun release() { stop(); stopPlayback() }
}
