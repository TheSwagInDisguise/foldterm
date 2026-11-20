package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.data.Weather
import dev.foldterm.launcher.data.weatherGlyph
import dev.foldterm.launcher.data.weatherText
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.KV
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.TwoPane
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeatherScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val weather by vm.weather.collectAsStateWithLifecycle()
    val status by vm.weatherStatus.collectAsStateWithLifecycle()
    val w = weather
    if (w == null) {
        Panel(Modifier.fillMaxSize(), title = "wttr") {
            Empty(status.ifBlank { "no forecast yet" }, "refresh") { vm.reloadWeather() }
            if (vm.perms.value["location"] != true) {
                TButton("allow location") { vm.requestPermission("location") }
                Spacer(Modifier.height(8.dp))
                T("or set a lat,lon override in Settings → ~/.foldtermrc", color = t.dim, size = 12.sp)
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            TwoPane(Modifier.fillMaxSize(), leftFraction = 0.5f, left = { m -> NowPanel(vm, w, status, m) }, right = { m -> WeekPanel(w, m) })
        } else {
            Column(Modifier.fillMaxSize()) {
                NowPanel(vm, w, status, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(10.dp))
                WeekPanel(w, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun NowPanel(vm: LauncherViewModel, w: Weather, status: String, modifier: Modifier) {
    val t = LocalTerm.current
    Panel(
        modifier, title = "wttr${w.place?.let { " · $it" } ?: ""}",
        trailing = { T(status.ifBlank { "updated ${Fmt.time(w.fetchedAt)}" }, color = t.dim, size = 12.sp) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(w.glyph, color = t.warn, size = 48.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    T("${w.temp.toInt()}°${w.unit}", size = 40.sp, weight = FontWeight.Bold)
                    T("${w.description}${w.feelsLike?.let { " · feels ${it.toInt()}°" } ?: ""}", color = t.dim)
                }
            }
            Spacer(Modifier.height(10.dp))
            val alert = severeLine(w)
            if (alert != null) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(t.err.copy(alpha = 0.15f)).padding(10.dp)) {
                    T("⚠ $alert", color = t.err, weight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(10.dp))
            }
            T("next 12h · chance of rain", color = t.dim, size = 12.sp)
            Spacer(Modifier.height(6.dp))
            val hours = w.hourly.take(12)
            Row(Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                hours.forEach { h ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (h.rainProb >= 20) T("${h.rainProb}", color = t.dim, size = 9.sp)
                        Box(
                            Modifier.fillMaxWidth().height((4 + h.rainProb * 0.7f).dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (h.rainProb >= 50) t.accent2 else t.accent2.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                hours.forEachIndexed { i, h ->
                    T(if (i % 3 == 0) h.hour else "", color = t.dim, size = 10.sp, align = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                hours.forEachIndexed { i, h ->
                    T(if (i % 3 == 0) "${h.temp.toInt()}°" else "", size = 10.sp, align = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Divider()
            KV("today", "H ${w.high.toInt()}° · L ${w.low.toInt()}°")
            w.windSpeed?.let { KV("wind", "${it.toInt()} ${w.windUnit}${w.windCompass?.let { c -> " $c" } ?: ""}") }
            w.uvMax?.let { KV("uv max", "%.0f · %s".format(it, uvLabel(it))) }
            if (w.sunrise != null || w.sunset != null) KV("sun", "↑ ${w.sunrise ?: "?"}  ↓ ${w.sunset ?: "?"}")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TButton("↻ refresh") { vm.reloadWeather() }
                TButton("°C / °F") { vm.prefs.setFahrenheit(!vm.prefs.fahrenheit.value); vm.reloadWeather() }
            }
        }
    }
}

@Composable
private fun WeekPanel(w: Weather, modifier: Modifier) {
    val t = LocalTerm.current
    Panel(modifier, title = "7 days") {
        if (w.daily.isEmpty()) { Empty("no daily forecast"); return@Panel }
        val lo = w.daily.minOf { it.min }
        val hi = w.daily.maxOf { it.max }
        val span = (hi - lo).coerceAtLeast(1.0)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            w.daily.forEachIndexed { i, d ->
                val name = runCatching {
                    if (i == 0) "Today" else LocalDate.parse(d.date).dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                }.getOrDefault(d.date)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(name, weight = if (i == 0) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.width(56.dp))
                    T(weatherGlyph(d.code), color = t.warn, size = 16.sp, modifier = Modifier.width(28.dp))
                    T("${d.min.toInt()}°", color = t.dim, align = TextAlign.End, modifier = Modifier.width(40.dp))
                    Spacer(Modifier.width(8.dp))
                    // range bar: where today's low and high sit within the week's range
                    BoxWithConstraints(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(t.chip)) {
                        val start = maxWidth * ((d.min - lo) / span).toFloat()
                        val width = maxWidth * ((d.max - d.min) / span).toFloat()
                        Box(
                            Modifier.padding(start = start).width(width.coerceAtLeast(6.dp)).fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp)).background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(t.accent2, t.warn))),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    T("${d.max.toInt()}°", modifier = Modifier.width(40.dp))
                    T(if (d.rainProb >= 20) "☂${d.rainProb}%" else "", color = t.accent2, size = 12.sp, modifier = Modifier.width(56.dp))
                }
                if (i == 0) T(weatherText(d.code), color = t.dim, size = 12.sp, modifier = Modifier.padding(start = 84.dp))
            }
        }
    }
}

/** A one-line warning when today's forecast is worth flagging, or null. */
fun severeLine(w: Weather): String? {
    val today = w.daily.firstOrNull()
    return when {
        today != null && today.code >= 95 -> "Thunderstorms forecast today"
        today != null && today.code in setOf(65, 67, 75, 82, 86) -> "Heavy ${if (today.code in setOf(75, 86)) "snow" else "rain"} forecast today"
        (w.uvMax ?: 0.0) >= 11 -> "Extreme UV today (${w.uvMax?.toInt()}): cover up"
        w.fahrenheit && w.high >= 100 || !w.fahrenheit && w.high >= 38 -> "Heat warning: high of ${w.high.toInt()}°"
        (w.windSpeed ?: 0.0) >= (if (w.fahrenheit) 40.0 else 60.0) -> "Strong winds right now"
        else -> null
    }
}

private fun uvLabel(uv: Double) = when {
    uv < 3 -> "low"
    uv < 6 -> "moderate"
    uv < 8 -> "high"
    uv < 11 -> "very high"
    else -> "extreme"
}
