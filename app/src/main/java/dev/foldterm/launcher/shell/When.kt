package dev.foldterm.launcher.shell

import java.util.Calendar

/** Parsers for durations ("5m", "1h30m", "90s") and loose times ("tomorrow 3pm", "fri 10:30"). */
object When {
    /** Returns seconds, or null. A bare number means minutes. */
    fun duration(s: String): Int? {
        val t = s.trim().lowercase()
        t.toIntOrNull()?.let { return it * 60 }
        val m = Regex("^(?:(\\d+)h)?\\s*(?:(\\d+)m(?:in)?)?\\s*(?:(\\d+)s)?$").matchEntire(t) ?: return null
        if (m.groupValues.drop(1).all { it.isEmpty() }) return null
        val (h, mi, se) = m.destructured
        return (h.toIntOrNull() ?: 0) * 3600 + (mi.toIntOrNull() ?: 0) * 60 + (se.toIntOrNull() ?: 0)
    }

    fun format(secs: Int): String = buildString {
        val h = secs / 3600; val m = secs % 3600 / 60; val s = secs % 60
        if (h > 0) append("${h}h")
        if (m > 0) append("${m}m")
        if (s > 0 || isEmpty()) append("${s}s")
    }

    /** Parses "7", "7:30", "7pm", "7:30am", "19:30". Returns hour (0-23) and minute. */
    fun clock(s: String): Pair<Int, Int>? {
        val m = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|a|p)?$").matchEntire(s.trim().lowercase()) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        val ap = m.groupValues[3]
        if (min > 59) return null
        if (ap.startsWith("p") && h < 12) h += 12
        if (ap.startsWith("a") && h == 12) h = 0
        if (h > 23) return null
        return h to min
    }

    /**
     * Pulls a due day and a "!" priority marker off a task: "pay rent fri !" →
     * ("pay rent", start of Friday, starred). Day words may lead or trail.
     */
    fun task(input: String, now: Long = System.currentTimeMillis()): Triple<String, Long?, Boolean> {
        val words = input.trim().split(Regex("\\s+")).toMutableList()
        var starred = false
        if (words.lastOrNull() == "!") { starred = true; words.removeAt(words.lastIndex) }
        if (words.firstOrNull() == "!") { starred = true; words.removeAt(0) }
        fun dayOf(w: String): Long? {
            val c = Calendar.getInstance().apply {
                timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            when (w.lowercase()) {
                "today", "tonight" -> return c.timeInMillis
                "tomorrow", "tmr", "tmrw" -> { c.add(Calendar.DAY_OF_YEAR, 1); return c.timeInMillis }
            }
            val idx = days.indexOfFirst { w.length >= 3 && w.lowercase().startsWith(it) }
            if (idx < 0) return null
            var diff = (idx + 1 - c.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (diff == 0) diff = 7
            c.add(Calendar.DAY_OF_YEAR, diff)
            return c.timeInMillis
        }
        var due: Long? = null
        words.lastOrNull()?.let { w -> dayOf(w)?.let { due = it; words.removeAt(words.lastIndex) } }
        if (due == null) words.firstOrNull()?.let { w -> dayOf(w)?.let { due = it; words.removeAt(0) } }
        if (words.lastOrNull()?.lowercase() in setOf("by", "on", "due")) words.removeAt(words.lastIndex)
        return Triple(words.joinToString(" "), due, starred)
    }

    data class Parsed(val begin: Long, val hasTime: Boolean, val title: String)

    private val days = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

    /** "tomorrow 3pm dentist", "fri 10:30 standup", "15:00 call bob", "lunch" (→ next hour). */
    fun event(words: List<String>, now: Long = System.currentTimeMillis()): Parsed {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val rest = words.toMutableList()
        var dayChosen = false
        fun peek() = rest.firstOrNull()?.lowercase()

        when (val w = peek()) {
            "today" -> { rest.removeAt(0); dayChosen = true }
            "tomorrow", "tmr", "tmrw" -> { rest.removeAt(0); cal.add(Calendar.DAY_OF_YEAR, 1); dayChosen = true }
            else -> {
                val idx = days.indexOfFirst { w != null && w.length >= 3 && w.startsWith(it) }
                if (idx >= 0) {
                    rest.removeAt(0)
                    val target = idx + 1 // Calendar.SUNDAY == 1
                    var diff = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    if (diff == 0) diff = 7
                    cal.add(Calendar.DAY_OF_YEAR, diff)
                    dayChosen = true
                }
            }
        }
        if (peek() == "at") rest.removeAt(0)
        // time may be split like "3 pm"
        var time: Pair<Int, Int>? = null
        val first = peek()
        if (first != null) {
            val two = rest.getOrNull(1)?.lowercase()
            if (two == "am" || two == "pm") {
                time = clock(first + two); if (time != null) { rest.removeAt(0); rest.removeAt(0) }
            } else {
                time = clock(first)?.takeIf { first.any { c -> c == ':' } || first.endsWith("m") || first.endsWith("a") || first.endsWith("p") || dayChosen }
                if (time != null) rest.removeAt(0)
            }
        }
        if (time != null) {
            cal.set(Calendar.HOUR_OF_DAY, time.first)
            cal.set(Calendar.MINUTE, time.second)
            if (!dayChosen && cal.timeInMillis < now) cal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (!dayChosen) {
            cal.add(Calendar.HOUR_OF_DAY, 1); cal.set(Calendar.MINUTE, 0)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 9); cal.set(Calendar.MINUTE, 0)
        }
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return Parsed(cal.timeInMillis, time != null, rest.joinToString(" "))
    }
}
