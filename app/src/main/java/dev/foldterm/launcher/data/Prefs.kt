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
    val hidden = MutableStateFlow(sp.getStringSet("hidden", emptySet())!!.toSet())
    val iconStyle = MutableStateFlow(sp.getString("iconStyle", "color") ?: "color")
    val showWallpaper = MutableStateFlow(sp.getBoolean("wallpaper", false))
    /** How much the theme background covers the wallpaper, 0 (clear) to 1 (opaque). */
    val wallpaperDim = MutableStateFlow(sp.getFloat("wallpaperDim", 0.5f))
    /** "theme" follows the theme accent; otherwise a #RRGGBB colour. */
    val iconTint = MutableStateFlow(sp.getString("iconTint", "theme") ?: "theme")
    val usage = MutableStateFlow(loadUsage())
    val notes = MutableStateFlow(loadNotes())
    val aliases = MutableStateFlow(loadAliases())
    val widgets = MutableStateFlow(loadWidgets())
    val recent = MutableStateFlow(sp.getString("recent", "")!!.split('|').filter { it.isNotBlank() })

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
    fun setHidden(key: String, hide: Boolean) {
        val next = hidden.value.toMutableSet().apply { if (hide) add(key) else remove(key) }
        hidden.value = next
        sp.edit().putStringSet("hidden", next).apply()
    }
    fun setIconStyle(v: String) { iconStyle.value = v; sp.edit().putString("iconStyle", v).apply() }
    fun setShowWallpaper(v: Boolean) { showWallpaper.value = v; sp.edit().putBoolean("wallpaper", v).apply() }
    fun setWallpaperDim(v: Float) { wallpaperDim.value = v; sp.edit().putFloat("wallpaperDim", v).apply() }
    fun setIconTint(v: String) { iconTint.value = v; sp.edit().putString("iconTint", v).apply() }

    /** Counts launches per app and keeps a most-recent-first list. */
    fun recordLaunch(key: String) {
        val u = usage.value.toMutableMap().apply { this[key] = (this[key] ?: 0) + 1 }
        usage.value = u
        recent.value = (listOf(key) + recent.value.filter { it != key }).take(12)
        sp.edit()
            .putString("usage", JSONObject(u.mapValues { it.value as Any }).toString())
            .putString("recent", recent.value.joinToString("|"))
            .apply()
    }

    // ── notes ──
    fun saveNotes(list: List<Note>) {
        notes.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text).put("updated", it.updated)) }
        sp.edit().putString("notes", arr.toString()).apply()
    }

    private fun loadNotes(): List<Note> = runCatching {
        val arr = JSONArray(sp.getString("notes", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Note(o.getLong("id"), o.getString("text"), o.optLong("updated"))
        }
    }.getOrDefault(emptyList())

    // ── aliases ──
    fun setAlias(name: String, expansion: String?) {
        val next = aliases.value.toMutableMap().apply { if (expansion == null) remove(name) else put(name, expansion) }
        aliases.value = next
        sp.edit().putString("aliases", JSONObject(next as Map<*, *>).toString()).apply()
    }

    private fun loadAliases(): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString("aliases", "{}")!!)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    // ── widgets ──
    fun saveWidgets(list: List<WidgetSlot>) {
        widgets.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("id", it.id).put("h", it.heightDp)) }
        sp.edit().putString("widgets", arr.toString()).apply()
    }

    private fun loadWidgets(): List<WidgetSlot> = runCatching {
        val arr = JSONArray(sp.getString("widgets", "[]"))
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { WidgetSlot(it.getInt("id"), it.optInt("h", 180)) } }
    }.getOrDefault(emptyList())

    private fun loadUsage(): Map<String, Int> = runCatching {
        val o = JSONObject(sp.getString("usage", "{}")!!)
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())

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
