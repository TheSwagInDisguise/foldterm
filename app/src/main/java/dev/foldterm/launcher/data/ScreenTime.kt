package dev.foldterm.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import java.util.Calendar

data class AppUsage(val pkg: String, val ms: Long, val opens: Int)
data class UsageDay(val dayStart: Long, val totalMs: Long)
data class UsageReport(val totalMs: Long, val apps: List<AppUsage>, val unlocks: Int, val week: List<UsageDay>)

/** Screen time from UsageStatsManager events (needs the user to grant usage access). */
class ScreenTime(private val ctx: Context) {
    private val usm = ctx.getSystemService(UsageStatsManager::class.java)

    fun granted(): Boolean = runCatching {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun settingsIntent() = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    private fun dayStart(offsetDays: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, -offsetDays)
    }.timeInMillis

    /** Foreground time per app between [from] and [to], built from resume/pause events. */
    private fun scan(from: Long, to: Long, self: String): Triple<Map<String, Long>, Map<String, Int>, Int> {
        val time = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        var unlocks = 0
        val started = HashMap<String, Long>()
        var lastFg: String? = null
        val events = usm.queryEvents(from, to) ?: return Triple(time, opens, 0)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (started[e.packageName] == null) started[e.packageName] = e.timeStamp
                    if (lastFg != e.packageName) { opens[e.packageName] = (opens[e.packageName] ?: 0) + 1; lastFg = e.packageName }
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    started.remove(e.packageName)?.let { s -> time[e.packageName] = (time[e.packageName] ?: 0) + (e.timeStamp - s) }
                }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    // screen off ends whatever was in front
                    started.forEach { (p, s) -> time[p] = (time[p] ?: 0) + (e.timeStamp - s) }
                    started.clear(); lastFg = null
                }
            }
        }
        started.forEach { (p, s) -> time[p] = (time[p] ?: 0) + (to - s) }
        time.remove(self); opens.remove(self)
        return Triple(time, opens, unlocks)
    }

    fun report(): UsageReport? {
        if (!granted()) return null
        val now = System.currentTimeMillis()
        val self = ctx.packageName
        val (time, opens, unlocks) = scan(dayStart(0), now, self)
        val apps = time.filter { it.value >= 30_000 }.map { (p, ms) -> AppUsage(p, ms, opens[p] ?: 0) }.sortedByDescending { it.ms }
        val week = (6 downTo 0).map { d ->
            val s = dayStart(d)
            val e = if (d == 0) now else dayStart(d - 1)
            UsageDay(s, if (d == 0) time.values.sum() else scan(s, e, self).first.values.sum())
        }
        return UsageReport(time.values.sum(), apps, unlocks, week)
    }
}
