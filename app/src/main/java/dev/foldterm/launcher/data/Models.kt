package dev.foldterm.launcher.data

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.UserHandle

data class AppEntry(
    val label: String,
    val packageName: String,
    val className: String,
    val user: UserHandle,
    val isWork: Boolean,
) {
    val key: String get() = "$packageName/$className"
    val component: ComponentName get() = ComponentName(packageName, className)
    val uniqueKey: String get() = "$key#${user.hashCode()}"
}

data class CalEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val location: String,
    val color: Int,
    val allDay: Boolean,
    val calendarName: String,
    val description: String,
) {
    val instanceKey: String get() = "${id}_$begin"
    val isOnline: Boolean
        get() = location.contains("http", true) || location.contains("zoom", true) ||
            location.contains("meet", true) || location.contains("teams", true)
    val url: String? get() = Regex("https?://\\S+").find("$location $description")?.value
}

data class ContactSummary(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val starred: Boolean,
    val company: String?,
    val phone: String?,
    val email: String?,
)

data class ContactDetail(
    val summary: ContactSummary,
    val phones: List<String>,
    val emails: List<String>,
    val company: String?,
    val jobTitle: String?,
    val birthday: String?,
    val addresses: List<String>,
    val accounts: List<String>,
    val note: String?,
)

enum class NotifKind { MAIL, CHAT, OTHER }

data class Notif(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val body: String,
    val time: Long,
    val kind: NotifKind,
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
    val removed: Boolean = false,
)

data class NowPlaying(
    val title: String,
    val artist: String,
    val pkg: String,
    val isPlaying: Boolean,
    val art: Bitmap?,
)

data class Weather(
    val temp: Double,
    val code: Int,
    val high: Double,
    val low: Double,
    val rainInHours: Int?,
    val fahrenheit: Boolean,
    val place: String?,
    val fetchedAt: Long,
    val feelsLike: Double? = null,
    val windSpeed: Double? = null,
    val windDir: Int? = null,
    val windUnit: String = "km/h",
    val uvMax: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val hourly: List<HourPoint> = emptyList(),
    val daily: List<DayPoint> = emptyList(),
) {
    val unit: String get() = if (fahrenheit) "F" else "C"
    /** Compass point for the wind direction, e.g. "SW". */
    val windCompass: String?
        get() = windDir?.let { listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((it + 22) % 360) / 45] }
    val description: String get() = weatherText(code)
    val glyph: String get() = weatherGlyph(code)
}

/** One forecast hour: "HH" label, temperature, chance of rain (%). */
data class HourPoint(val hour: String, val temp: Double, val rainProb: Int)

/** One forecast day: ISO date, weather code, low/high, chance of rain (%). */
data class DayPoint(val date: String, val code: Int, val min: Double, val max: Double, val rainProb: Int)

data class NewsItem(val title: String, val link: String, val source: String, val published: Long?)

data class Photo(val id: Long, val uri: Uri, val dateMs: Long)

data class Note(val id: Long, val text: String, val updated: Long) {
    val title: String get() = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: "(empty note)"
}

/** A hosted widget: its id and the height the user chose, in dp. */
data class WidgetSlot(val id: Int, val heightDp: Int)

data class TaskItem(val id: Long, val text: String, val done: Boolean, val created: Long)

data class StepState(val available: Boolean, val permitted: Boolean, val steps: Int?)

data class DeviceVitals(
    val battery: Int,
    val charging: Boolean,
    val tempC: Float,
    val storageFreeGb: Double,
    val storageTotalGb: Double,
    val ramAvailGb: Double,
    val ramTotalGb: Double,
    val uptimeMs: Long,
)

enum class Posture { NONE, FLAT, BOOK, TABLETOP }

data class FoldState(
    val posture: Posture = Posture.NONE,
    val hinge: Rect? = null,
    val vertical: Boolean = false,
    val separating: Boolean = false,
) {
    val label: String
        get() = when (posture) {
            Posture.NONE -> "no hinge"
            Posture.FLAT -> "flat"
            Posture.BOOK -> "book"
            Posture.TABLETOP -> "tabletop"
        }
}

fun weatherText(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "—"
}

fun weatherGlyph(code: Int): String = when (code) {
    0, 1 -> "☀"
    2 -> "⛅"
    3, 45, 48 -> "☁"
    71, 73, 75, 77, 85, 86 -> "❄"
    95, 96, 99 -> "⚡"
    else -> if (code >= 51) "☂" else "·"
}
