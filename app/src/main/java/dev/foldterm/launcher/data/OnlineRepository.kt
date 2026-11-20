package dev.foldterm.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Xml
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.OffsetDateTime
import java.util.Locale
import kotlin.coroutines.resume

/** Weather (Open-Meteo, no API key) and RSS/Atom news. All calls are blocking: run on IO. */
class OnlineRepository(private val ctx: Context) {

    fun locationGranted() =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun location(manual: String): Pair<Double, Double>? {
        parseLatLon(manual)?.let { return it }
        if (!locationGranted()) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val last: Location? = runCatching {
            lm.getProviders(true).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
        if (last != null) return last.latitude to last.longitude
        if (Build.VERSION.SDK_INT >= 30) {
            val providers = lm.getProviders(true)
            val provider = providers.firstOrNull { it == "fused" } ?: providers.firstOrNull { it == LocationManager.NETWORK_PROVIDER }
                ?: providers.firstOrNull() ?: return null
            val loc = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    runCatching {
                        lm.getCurrentLocation(provider, null, ctx.mainExecutor) { l -> if (cont.isActive) cont.resume(l) }
                    }.onFailure { if (cont.isActive) cont.resume(null) }
                }
            }
            if (loc != null) return loc.latitude to loc.longitude
        }
        return null
    }

    private fun parseLatLon(s: String): Pair<Double, Double>? {
        val parts = s.split(',').map { it.trim().toDoubleOrNull() }
        if (parts.size != 2 || parts[0] == null || parts[1] == null) return null
        return parts[0]!! to parts[1]!!
    }

    fun weather(lat: Double, lon: Double, fahrenheit: Boolean): Weather {
        val unit = if (fahrenheit) "fahrenheit" else "celsius"
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
            "&hourly=precipitation_probability&forecast_days=2&temperature_unit=$unit&timezone=auto"
        val json = JSONObject(get(url))
        val cur = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val hourly = json.getJSONObject("hourly")
        val times = hourly.getJSONArray("time")
        val probs = hourly.getJSONArray("precipitation_probability")
        val nowIso = cur.getString("time").take(13) // yyyy-MM-ddTHH
        var startIdx = 0
        for (i in 0 until times.length()) if (times.getString(i).take(13) == nowIso) { startIdx = i; break }
        var rainIn: Int? = null
        for (h in 0 until 12) {
            val i = startIdx + h
            if (i >= probs.length()) break
            if (probs.optInt(i, 0) >= 50) { rainIn = h; break }
        }
        val place = runCatching {
            @Suppress("DEPRECATION")
            Geocoder(ctx).getFromLocation(lat, lon, 1)?.firstOrNull()?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
        }.getOrNull()
        return Weather(
            temp = cur.getDouble("temperature_2m"),
            code = cur.getInt("weather_code"),
            high = daily.getJSONArray("temperature_2m_max").getDouble(0),
            low = daily.getJSONArray("temperature_2m_min").getDouble(0),
            rainInHours = rainIn,
            fahrenheit = fahrenheit,
            place = place,
            fetchedAt = System.currentTimeMillis(),
        )
    }

    fun news(feeds: List<String>): List<NewsItem> =
        feeds.flatMap { url -> runCatching { parseFeed(url, get(url)) }.getOrDefault(emptyList()) }
            .sortedByDescending { it.published ?: 0L }

    private fun parseFeed(url: String, xml: String): List<NewsItem> {
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        p.setInput(xml.reader())
        val out = mutableListOf<NewsItem>()
        var source = URL(url).host.removePrefix("www.").removePrefix("feeds.")
        var inItem = false
        var title = ""; var link = ""; var date: String? = null
        var sawChannelTitle = false
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "item", "entry" -> { inItem = true; title = ""; link = ""; date = null }
                    "title" -> {
                        val t = p.nextText().trim()
                        if (inItem) title = t else if (!sawChannelTitle && t.isNotBlank()) { source = t; sawChannelTitle = true }
                    }
                    "link" -> if (inItem) {
                        val href = p.getAttributeValue(null, "href")
                        if (href != null) { if (link.isEmpty()) link = href } else link = p.nextText().trim()
                    }
                    "pubDate", "published", "updated", "dc:date" -> if (inItem && date == null) date = p.nextText().trim()
                }
                XmlPullParser.END_TAG -> if (p.name == "item" || p.name == "entry") {
                    inItem = false
                    if (title.isNotBlank()) out += NewsItem(title, link, source, parseDate(date))
                }
            }
        }
        return out.take(30)
    }

    private fun parseDate(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        for (f in listOf("EEE, dd MMM yyyy HH:mm:ss Z", "EEE, dd MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z")) {
            runCatching { return SimpleDateFormat(f, Locale.US).parse(s)?.time }
        }
        return null
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "FoldTerm-Launcher/1.0 (Android)")
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode} for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
