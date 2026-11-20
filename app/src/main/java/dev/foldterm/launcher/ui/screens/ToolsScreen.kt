package dev.foldterm.launcher.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.TwoPane
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/** Rotation (azimuth) and tilt from the rotation-vector/accelerometer sensors while on screen. */
@Composable
private fun rememberOrientation(): Triple<Float, Float, Float> {
    val ctx = LocalContext.current
    var azimuth by remember { mutableFloatStateOf(0f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            val m = FloatArray(9); val o = FloatArray(3)
            override fun onSensorChanged(e: SensorEvent) {
                when (e.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(m, e.values)
                        SensorManager.getOrientation(m, o)
                        azimuth = ((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                        pitch = Math.toDegrees(atan2(y.toDouble(), sqrt((x * x + z * z).toDouble()))).toFloat()
                        roll = Math.toDegrees(atan2(-x.toDouble(), z.toDouble())).toFloat()
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        rot?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        acc?.let { sm.registerListener(l, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sm.unregisterListener(l) }
    }
    return Triple(azimuth, pitch, roll)
}

private fun compassPoint(deg: Float) = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((deg + 22.5f) % 360) / 45).toInt()]

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolsScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val (azimuth, pitch, roll) = rememberOrientation()
    var dice by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var sides by rememberSaveable { mutableIntStateOf(6) }
    var coin by rememberSaveable { mutableStateOf("") }
    var tally by rememberSaveable { mutableIntStateOf(0) }

    val gauges: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "compass & level") {
            Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                T("${azimuth.toInt()}° ${compassPoint(azimuth)}", size = 34.sp, weight = FontWeight.Bold, color = t.accent)
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.size(200.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(t.border, r - 2, style = Stroke(3f))
                    rotate(-azimuth) {
                        for (i in 0 until 72) {
                            val a = Math.toRadians(i * 5.0)
                            val inner = if (i % 18 == 0) r * 0.78f else if (i % 2 == 0) r * 0.86f else r * 0.9f
                            drawLine(if (i == 0) t.err else t.dim, Offset(center.x + inner * kotlin.math.sin(a).toFloat(), center.y - inner * kotlin.math.cos(a).toFloat()),
                                Offset(center.x + (r - 6) * kotlin.math.sin(a).toFloat(), center.y - (r - 6) * kotlin.math.cos(a).toFloat()), strokeWidth = if (i % 18 == 0) 5f else 2f)
                        }
                        drawLine(t.err, center, Offset(center.x, center.y - r * 0.6f), strokeWidth = 8f)
                        drawLine(t.fg, center, Offset(center.x, center.y + r * 0.45f), strokeWidth = 8f)
                    }
                }
                Spacer(Modifier.height(16.dp))
                val level = kotlin.math.abs(pitch) < 1.5f && kotlin.math.abs(roll) < 1.5f
                T(if (level) "level ✓" else "tilt ${"%.1f".format(roll)}° · ${"%.1f".format(pitch)}°", size = 18.sp, color = if (level) t.ok else t.warn)
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.size(160.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(t.border, r - 2, style = Stroke(3f))
                    drawCircle(t.border, r * 0.25f, style = Stroke(2f))
                    val bx = (roll / 45f).coerceIn(-1f, 1f) * r * 0.8f
                    val by = (-pitch / 45f).coerceIn(-1f, 1f) * r * 0.8f
                    drawCircle(if (level) t.ok else t.accent2, r * 0.16f, Offset(center.x - bx, center.y - by))
                }
                T("lay the phone flat for the bubble level", color = t.dim, size = 11.sp)
            }
        }
    }
    val chance: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "dice · coin · tally") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4, 6, 8, 10, 12, 20).forEach { s -> TButton("d$s", primary = s == sides) { sides = s } }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 2, 3, 5).forEach { n -> TButton("roll $n") { dice = List(n) { Random.nextInt(1, sides + 1) } } }
                }
                if (dice.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    T(dice.joinToString("  ") { "[$it]" }, size = 28.sp, weight = FontWeight.Bold, color = t.accent)
                    if (dice.size > 1) T("total ${dice.sum()}", color = t.dim)
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TButton("flip a coin", primary = true) { coin = if (Random.nextBoolean()) "heads" else "tails" }
                    Spacer(Modifier.width(14.dp))
                    T(coin, size = 24.sp, weight = FontWeight.Bold, color = t.warn)
                }
                Spacer(Modifier.height(18.dp))
                T("tally", color = t.dim, size = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("$tally", size = 44.sp, weight = FontWeight.Bold, modifier = Modifier.width(130.dp))
                    TButton("+1", primary = true) { tally++ }
                    Spacer(Modifier.width(8.dp))
                    TButton("−1") { if (tally > 0) tally-- }
                    Spacer(Modifier.width(8.dp))
                    TButton("reset") { tally = 0 }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) TwoPane(Modifier.fillMaxSize(), left = gauges, right = chance)
        else Column(Modifier.fillMaxSize()) { gauges(Modifier.fillMaxWidth().weight(1f)); Spacer(Modifier.height(10.dp)); chance(Modifier.fillMaxWidth().weight(1f)) }
    }
}
