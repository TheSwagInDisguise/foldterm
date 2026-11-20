package dev.foldterm.launcher.data

import java.time.LocalDate

data class Habit(val id: Long, val name: String, val days: Set<String>) {
    fun doneOn(d: LocalDate) = d.toString() in days

    /** Consecutive days up to today (or yesterday, if today isn't ticked yet). */
    fun streak(today: LocalDate = LocalDate.now()): Int {
        var d = if (doneOn(today)) today else today.minusDays(1)
        var n = 0
        while (doneOn(d)) { n++; d = d.minusDays(1) }
        return n
    }

    fun best(): Int {
        val sorted = days.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (d in sorted) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }

    /** Share of the last [n] days ticked, 0–100. */
    fun rate(n: Int = 30, today: LocalDate = LocalDate.now()): Int =
        (0 until n).count { doneOn(today.minusDays(it.toLong())) } * 100 / n
}
