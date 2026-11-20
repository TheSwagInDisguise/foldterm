package dev.foldterm.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("foldterm", Context.MODE_PRIVATE)

    val theme = MutableStateFlow(sp.getString("theme", "phosphor") ?: "phosphor")
    val dock = MutableStateFlow(sp.getString("dock", null)?.split('|')?.filter { it.isNotBlank() })
    val ice = MutableStateFlow(sp.getStringSet("ice", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
    val feeds = MutableStateFlow(sp.getString("feeds", DEFAULT_FEEDS) ?: DEFAULT_FEEDS)
    val fahrenheit = MutableStateFlow(sp.getBoolean("fahrenheit", Locale.getDefault().country in setOf("US", "LR", "MM")))
    val stepGoal = MutableStateFlow(sp.getInt("stepGoal", 8000))
    val userName = MutableStateFlow(sp.getString("name", "") ?: "")
    val manualLocation = MutableStateFlow(sp.getString("loc", "") ?: "")

    fun setTheme(v: String) { theme.value = v; sp.edit().putString("theme", v).apply() }
    fun setDock(v: List<String>?) {
        dock.value = v
        sp.edit().apply { if (v == null) remove("dock") else putString("dock", v.joinToString("|")) }.apply()
    }
    fun toggleIce(id: Long) {
        val next = ice.value.toMutableSet().apply { if (!add(id)) remove(id) }
        ice.value = next
        sp.edit().putStringSet("ice", next.map { it.toString() }.toSet()).apply()
    }
    fun setFeeds(v: String) { feeds.value = v; sp.edit().putString("feeds", v).apply() }
    fun setFahrenheit(v: Boolean) { fahrenheit.value = v; sp.edit().putBoolean("fahrenheit", v).apply() }
    fun setStepGoal(v: Int) { stepGoal.value = v; sp.edit().putInt("stepGoal", v).apply() }
    fun setUserName(v: String) { userName.value = v; sp.edit().putString("name", v).apply() }
    fun setManualLocation(v: String) { manualLocation.value = v; sp.edit().putString("loc", v).apply() }

    /** True exactly once, on the very first launch. */
    fun consumeFirstRun(): Boolean {
        if (sp.getBoolean("seenFirstRun", false)) return false
        sp.edit().putBoolean("seenFirstRun", true).apply()
        return true
    }

    // Step counter baseline: the sensor counts since boot, so remember today's first reading.
    fun stepBaseline(): Pair<String, Int>? {
        val day = sp.getString("stepDay", null) ?: return null
        return day to sp.getInt("stepBase", 0)
    }
    fun setStepBaseline(day: String, base: Int) {
        sp.edit().putString("stepDay", day).putInt("stepBase", base).apply()
    }

    fun loadTasks(): List<TaskItem> = runCatching {
        val arr = JSONArray(sp.getString("tasks", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            TaskItem(o.getLong("id"), o.getString("text"), o.optBoolean("done"), o.optLong("created"))
        }
    }.getOrDefault(emptyList())

    fun saveTasks(tasks: List<TaskItem>) {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("created", it.created))
        }
        sp.edit().putString("tasks", arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_FEEDS = "https://feeds.bbci.co.uk/news/rss.xml\nhttps://hnrss.org/frontpage"
    }
}
