package dev.foldterm.launcher

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.text.format.DateFormat
import android.view.View
import androidx.compose.ui.graphics.toArgb
import dev.foldterm.launcher.ui.Themes
import java.util.Date
import kotlin.random.Random

/** Screen saver: a terminal clock over slowly falling glyphs, in the launcher's theme colours. */
class TerminalDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        setContentView(DreamView(this))
    }
}

private class DreamView(ctx: Context) : View(ctx) {
    private val sp = ctx.getSharedPreferences("foldterm", Context.MODE_PRIVATE)
    private val theme = (sp.getString("theme", "phosphor") ?: "phosphor").let { n ->
        if (n == "custom") Themes.custom(sp.getString("customTheme", null) ?: Themes.DEFAULT_CUSTOM) else Themes.byName(n)
    }.let { if (it.dark) it else Themes.phosphor } // always dark at night
    private val bg = theme.bg.toArgb()
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = theme.accent.toArgb() }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); color = theme.fg.toArgb(); textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.MONOSPACE; color = theme.dim.toArgb(); textAlign = Paint.Align.CENTER }
    private val chars = "01アイウエオカキクケコ<>/\\{}[]=+*#@$%&;:ABCDEF❯_".toCharArray()
    private var drops = FloatArray(0)
    private var speeds = FloatArray(0)
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 80) } }
    private var shiftX = 0f
    private var shiftY = 0f
    private var lastShift = 0L

    override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.post(tick) }
    override fun onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cell = (w / 28f).coerceIn(24f, 56f)
        glyph.textSize = cell * 0.8f
        val cols = (w / cell).toInt().coerceAtLeast(1)
        if (drops.size != cols) {
            drops = FloatArray(cols) { Random.nextFloat() * h }
            speeds = FloatArray(cols) { 0.4f + Random.nextFloat() * 0.9f }
        }
        canvas.drawColor(bg)
        for (i in 0 until cols) {
            val x = i * cell
            for (k in 0 until 12) {
                val y = drops[i] - k * cell
                if (y < -cell || y > h + cell) continue
                glyph.alpha = (if (k == 0) 200 else 120 - k * 10).coerceIn(10, 220) / 3
                canvas.drawText(chars[(i * 7 + k + (drops[i] / cell).toInt()) % chars.size].toString(), x, y, glyph)
            }
            drops[i] += speeds[i] * cell * 0.25f
            if (drops[i] - 12 * cell > h) drops[i] = -Random.nextFloat() * h * 0.5f
        }
        // move the clock a little every minute to avoid burn-in
        val now = System.currentTimeMillis()
        if (now - lastShift > 60_000) { shiftX = (Random.nextFloat() - 0.5f) * w * 0.2f; shiftY = (Random.nextFloat() - 0.5f) * h * 0.2f; lastShift = now }
        val cx = w / 2 + shiftX; val cy = h / 2 + shiftY
        big.textSize = (w / 5f).coerceAtMost(h / 3f)
        val time = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", Date(now)).toString()
        canvas.drawText(time, cx, cy, big)
        small.textSize = big.textSize / 6f
        canvas.drawText(DateFormat.format("EEEE, MMMM d", Date(now)).toString().lowercase(), cx, cy + small.textSize * 1.8f, small)
        val bat = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        if (level >= 0) canvas.drawText("❯ battery $level%", cx, cy + small.textSize * 3.4f, small)
    }
}
