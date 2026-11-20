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
    val sectionOrder = MutableStateFlow(sp.getString("sectionOrder", "")!!.split('|').filter { it.isNotBlank() })
    val hiddenSections = MutableStateFlow(sp.getStringSet("hiddenSections", emptySet())!!.toSet())

    fun setSectionOrder(ids: List<String>) {
        sectionOrder.value = ids
        sp.edit().putString("sectionOrder", ids.joinToString("|")).apply()
    }
    fun setSectionVisible(id: String, visible: Boolean) {
        val next = hiddenSections.value.toMutableSet().apply { if (visible) remove(id) else add(id) }
        hiddenSections.value = next
        sp.edit().putStringSet("hiddenSections", next).apply()
    }
    fun resetSidebar() {
        setSectionOrder(emptyList())
        hiddenSections.value = emptySet()
        sp.edit().remove("hiddenSections").apply()
    }

    /** Cover-screen Home: on = compact card layout when the window is narrow. */
    val coverMode = MutableStateFlow(sp.getBoolean("coverMode", true))
    /** Enabled cover cards in order. */
    val coverCards = MutableStateFlow(
        (sp.getString("coverCards", null) ?: "clock|next|dial|inbox|media").split('|').filter { it.isNotBlank() },
    )
    fun setCoverMode(v: Boolean) { coverMode.value = v; sp.edit().putBoolean("coverMode", v).apply() }
    fun setCoverCards(v: List<String>) { coverCards.value = v; sp.edit().putString("coverCards", v.joinToString("|")).apply() }

    /** Unfolded Home cards in order, each "id:L" or "id:R" for its column. */
    val homeLayout = MutableStateFlow(
        (sp.getString("homeLayout", null) ?: "happening:L|agenda:L|summary:R").split('|').filter { ':' in it },
    )
    fun setHomeLayout(v: List<String>) { homeLayout.value = v; sp.edit().putString("homeLayout", v.joinToString("|")).apply() }

    /** Apps left out of badges and the Home summary. */
    val mutedApps = MutableStateFlow(sp.getStringSet("mutedApps", emptySet())!!.toSet())
    /** Apps always listed first in the notification centre. */
    val pinnedApps = MutableStateFlow(sp.getStringSet("pinnedApps", emptySet())!!.toSet())

    fun toggleMuted(pkg: String) {
        val next = mutedApps.value.toMutableSet().apply { if (!add(pkg)) remove(pkg) }
        mutedApps.value = next
        sp.edit().putStringSet("mutedApps", next).apply()
    }
    fun togglePinned(pkg: String) {
        val next = pinnedApps.value.toMutableSet().apply { if (!add(pkg)) remove(pkg) }
        pinnedApps.value = next
        sp.edit().putStringSet("pinnedApps", next).apply()
    }

    /** Saved app pairs: name → (left app key, right app key). */
    val pairs = MutableStateFlow(loadPairs())
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
    /** Package of the chosen icon pack (used when iconStyle == "pack"). */
    val iconPack = MutableStateFlow(sp.getString("iconPack", "") ?: "")
    fun setIconPack(v: String) { iconPack.value = v; sp.edit().putString("iconPack", v).apply() }

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

    // ── app pairs ──
    fun setPair(name: String, value: Pair<String, String>?) {
        val next = pairs.value.toMutableMap().apply { if (value == null) remove(name) else put(name, value) }
        pairs.value = next
        val o = JSONObject()
        next.forEach { (k, v) -> o.put(k, "${v.first}|${v.second}") }
        sp.edit().putString("pairs", o.toString()).apply()
    }

    private fun loadPairs(): Map<String, Pair<String, String>> = runCatching {
        val o = JSONObject(sp.getString("pairs", "{}")!!)
        o.keys().asSequence().mapNotNull { k ->
            val parts = o.getString(k).split('|')
            if (parts.size == 2) k to (parts[0] to parts[1]) else null
        }.toMap()
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
