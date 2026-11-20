package dev.foldterm.launcher.data

import android.Manifest
import android.app.ActivityManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size

/** Photos, step counter and device vitals. */
class DeviceRepository(private val ctx: Context) {
    private val thumbCache = LruCache<Long, Bitmap>(160)

    fun photosGranted(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED ||
            (Build.VERSION.SDK_INT >= 34 &&
                ctx.checkSelfPermission("android.permission.READ_MEDIA_VISUAL_USER_SELECTED") == PackageManager.PERMISSION_GRANTED)
    }

    fun photos(limit: Int = 400): List<Photo> {
        if (!photosGranted()) return emptyList()
        val out = mutableListOf<Photo>()
        val base = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        runCatching {
            ctx.contentResolver.query(
                base, arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_ADDED, MediaStore.Images.Media.BUCKET_DISPLAY_NAME),
                null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val id = c.getLong(0)
                    out += Photo(id, ContentUris.withAppendedId(base, id), c.getLong(1) * 1000, c.getString(2) ?: "")
                }
            }
        }
        return out
    }

    /** Screen-sized version for the viewer (not cached; the pager only keeps nearby pages). */
    fun large(p: Photo, px: Int = 2048): Bitmap? =
        runCatching { ctx.contentResolver.loadThumbnail(p.uri, Size(px, px), null) }.getOrNull()

    fun thumbnail(p: Photo, px: Int = 320): Bitmap? {
        thumbCache.get(p.id)?.let { return it }
        val b = runCatching { ctx.contentResolver.loadThumbnail(p.uri, Size(px, px), null) }.getOrNull() ?: return null
        thumbCache.put(p.id, b)
        return b
    }

    fun stepsPermitted() =
        ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun stepSensorAvailable(): Boolean =
        ctx.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    /** Registers the step counter; returns an unregister function. */
    fun listenSteps(onRaw: (Int) -> Unit): (() -> Unit)? {
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        if (!stepsPermitted()) return null
        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) { onRaw(event.values[0].toInt()) }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        return { sm.unregisterListener(l) }
    }

    fun vitals(): DeviceVitals {
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val stat = StatFs(Environment.getDataDirectory().path)
        val mem = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val gb = 1024.0 * 1024 * 1024
        return DeviceVitals(
            battery = if (level >= 0) level * 100 / scale else -1,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            tempC = temp,
            storageFreeGb = stat.availableBytes / gb,
            storageTotalGb = stat.totalBytes / gb,
            ramAvailGb = mem.availMem / gb,
            ramTotalGb = mem.totalMem / gb,
            uptimeMs = SystemClock.elapsedRealtime(),
        )
    }
}
