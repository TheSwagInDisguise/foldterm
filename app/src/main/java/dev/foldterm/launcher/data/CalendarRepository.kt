package dev.foldterm.launcher.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import java.util.TimeZone

class CalendarRepository(private val ctx: Context) {
    fun granted() = ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun load(start: Long, end: Long): List<CalEvent> {
        if (!granted()) return emptyList()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val proj = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.EVENT_LOCATION, Instances.DISPLAY_COLOR, Instances.ALL_DAY,
            Instances.CALENDAR_DISPLAY_NAME, Instances.DESCRIPTION,
        )
        val out = mutableListOf<CalEvent>()
        runCatching {
            ctx.contentResolver.query(uri, proj, "${Instances.VISIBLE}=1", null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(6) == 1
                    var b = c.getLong(2)
                    var e = c.getLong(3)
                    if (allDay) {
                        // all-day instances are stored at UTC midnight; shift into local time
                        val tz = TimeZone.getDefault()
                        b -= tz.getOffset(b)
                        e -= tz.getOffset(e)
                    }
                    out += CalEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(no title)",
                        begin = b,
                        end = e,
                        location = c.getString(4) ?: "",
                        color = c.getInt(5),
                        allDay = allDay,
                        calendarName = c.getString(7) ?: "",
                        description = c.getString(8) ?: "",
                    )
                }
            }
        }
        return out.sortedWith(compareBy({ !it.allDay }, { it.begin })).sortedBy { dayKey(it.begin) }
    }

    fun viewIntent(e: CalEvent): Intent =
        Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.end)

    fun editIntent(e: CalEvent): Intent =
        Intent(Intent.ACTION_EDIT, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.id))

    fun newEventIntent(begin: Long? = null): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            if (begin != null) {
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000)
            }
        }

    companion object {
        fun dayKey(ms: Long): Long {
            val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
            return c.get(java.util.Calendar.YEAR) * 1000L + c.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }
}
