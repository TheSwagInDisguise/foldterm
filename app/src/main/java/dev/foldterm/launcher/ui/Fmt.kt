package dev.foldterm.launcher.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Fmt {
    var is24 = false

    fun time(ms: Long): String =
        SimpleDateFormat(if (is24) "HH:mm" else "h:mm a", Locale.getDefault()).format(Date(ms))

    fun clock(ms: Long): String =
        SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(ms))

    fun longDate(ms: Long): String = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(ms))
    fun shortDate(ms: Long): String = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(ms))
    fun monthDay(ms: Long): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ms))
    fun monthYear(ms: Long): String = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(ms))

    fun dayLabel(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = startOfDay(ms)
        val today = startOfDay(now)
        return when (d) {
            today -> "Today"
            addDays(today, 1) -> "Tomorrow"
            addDays(today, -1) -> "Yesterday"
            else -> shortDate(ms)
        }
    }

    fun duration(ms: Long): String {
        val mins = (ms / 60_000).toInt()
        val h = mins / 60
        val m = mins % 60
        return when {
            h == 0 -> "${m}m"
            m == 0 -> "${h}h"
            else -> "${h}h${m}m"
        }
    }

    fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
        val d = (now - ms) / 1000
        return when {
            d < 60 -> "now"
            d < 3600 -> "${d / 60}m"
            d < 86_400 -> "${d / 3600}h"
            d < 7 * 86_400 -> "${d / 86_400}d"
            else -> monthDay(ms)
        }
    }

    fun until(ms: Long, now: Long = System.currentTimeMillis()): String {
        val mins = ((ms - now) / 60_000).coerceAtLeast(0)
        return if (mins < 60) "in ${mins}m" else "in ${mins / 60}h${if (mins % 60 > 0) "${mins % 60}m" else ""}"
    }

    fun greeting(now: Long = System.currentTimeMillis()): String {
        val h = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return when (h) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Good night"
        }
    }

    fun startOfDay(ms: Long): Long = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun addDays(ms: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = ms; add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    fun initials(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "?"
            words.size == 1 -> words[0].take(2).replaceFirstChar { it.uppercase() }
            else -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        }
    }

    fun money(v: Double): String = if (v == Math.floor(v) && v < 1e9) "%,.0f".format(v) else "%,.2f".format(v)

    fun gb(v: Double) = String.format(Locale.US, "%.1fG", v)

    fun bar(fraction: Float, width: Int = 20): String {
        val f = fraction.coerceIn(0f, 1f)
        val full = (f * width).toInt()
        return "█".repeat(full) + "░".repeat(width - full)
    }
}
