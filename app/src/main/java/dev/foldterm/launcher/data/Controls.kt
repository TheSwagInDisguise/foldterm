package dev.foldterm.launcher.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings

/**
 * Quick-settings style device controls. Brightness, rotation and timeout need the user to
 * grant "modify system settings"; Do Not Disturb and silent mode need DND access.
 */
class Controls(private val ctx: Context) {
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    data class Stream(val id: Int, val label: String)

    val streams = listOf(
        Stream(AudioManager.STREAM_MUSIC, "media"),
        Stream(AudioManager.STREAM_RING, "ring"),
        Stream(AudioManager.STREAM_NOTIFICATION, "notifications"),
        Stream(AudioManager.STREAM_ALARM, "alarm"),
    )

    fun volume(stream: Int): Float =
        audio.getStreamVolume(stream).toFloat() / audio.getStreamMaxVolume(stream).coerceAtLeast(1)

    /** Returns an error message, or null on success. */
    fun setVolume(stream: Int, f: Float): String? = runCatching {
        audio.setStreamVolume(stream, (f.coerceIn(0f, 1f) * audio.getStreamMaxVolume(stream)).toInt(), 0)
    }.exceptionOrNull()?.let { "needs Do Not Disturb access" }

    fun ringerMode(): Int = audio.ringerMode

    fun setRingerMode(mode: Int): String? = runCatching { audio.ringerMode = mode }
        .exceptionOrNull()?.let { "needs Do Not Disturb access" }

    fun dndAccess(): Boolean = nm.isNotificationPolicyAccessGranted
    fun dndOn(): Boolean = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    fun setDnd(on: Boolean): String? {
        if (!dndAccess()) return "needs Do Not Disturb access"
        return runCatching {
            nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        }.exceptionOrNull()?.message
    }
    fun dndAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun canWrite(): Boolean = Settings.System.canWrite(ctx)
    fun writeSettingsIntent() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    private val cr get() = ctx.contentResolver

    fun brightness(): Float = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f
    fun autoBrightness(): Boolean =
        Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
    fun autoRotate(): Boolean = Settings.System.getInt(cr, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    fun timeoutMs(): Int = Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT, 30_000)

    private fun put(key: String, v: Int): String? {
        if (!canWrite()) return "needs “modify system settings” permission"
        return runCatching { Settings.System.putInt(cr, key, v) }.exceptionOrNull()?.message
    }

    fun setBrightness(f: Float): String? {
        if (autoBrightness()) put(Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        return put(Settings.System.SCREEN_BRIGHTNESS, (f.coerceIn(0.02f, 1f) * 255).toInt())
    }
    fun setAutoBrightness(on: Boolean) = put(
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        if (on) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    )
    fun setAutoRotate(on: Boolean) = put(Settings.System.ACCELEROMETER_ROTATION, if (on) 1 else 0)
    fun setTimeout(ms: Int) = put(Settings.System.SCREEN_OFF_TIMEOUT, ms)
}
