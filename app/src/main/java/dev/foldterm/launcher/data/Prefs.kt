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

    // ── focus mode ──
    val focusUntil = MutableStateFlow(sp.getLong("focusUntil", 0L))
    val focusLabel = MutableStateFlow(sp.getString("focusLabel", "") ?: "")
    val focusBlock = MutableStateFlow(sp.getStringSet("focusBlock", emptySet())!!.toSet())
    val focusDnd = MutableStateFlow(sp.getBoolean("focusDnd", true))
    fun setFocus(until: Long, label: String) {
        focusUntil.value = until; focusLabel.value = label
        sp.edit().putLong("focusUntil", until).putString("focusLabel", label)
            .putLong("focusStart", if (until > 0) System.currentTimeMillis() else 0L).apply()
    }
    fun focusStart(): Long = sp.getLong("focusStart", 0L)
    fun toggleFocusBlock(key: String) {
        val next = focusBlock.value.toMutableSet().apply { if (!add(key)) remove(key) }
        focusBlock.value = next
        sp.edit().putStringSet("focusBlock", next).apply()
    }
    fun setFocusDnd(v: Boolean) { focusDnd.value = v; sp.edit().putBoolean("focusDnd", v).apply() }
    /** Minutes focused today (resets each day). */
    fun focusToday(): Long {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        return if (sp.getString("focusDay", "") == day) sp.getLong("focusMs", 0L) else 0L
    }
    fun addFocus(ms: Long) {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        sp.edit().putString("focusDay", day).putLong("focusMs", focusToday() + ms).apply()
    }

    // ── gestures: drawer, shade, prompt, alerts, media, lock, torch, none ──
    val gestureUp = MutableStateFlow(sp.getString("gUp", "drawer") ?: "drawer")
    val gestureDown = MutableStateFlow(sp.getString("gDown", "shade") ?: "shade")
    val gestureDouble = MutableStateFlow(sp.getString("gDouble", "none") ?: "none")
    fun setGesture(which: String, action: String) {
        when (which) { "up" -> gestureUp.value = action; "down" -> gestureDown.value = action; else -> gestureDouble.value = action }
        sp.edit().putString(when (which) { "up" -> "gUp"; "down" -> "gDown"; else -> "gDouble" }, action).apply()
    }

    /** App drawer order: "az", "used" or "new". */
    val drawerSort = MutableStateFlow(sp.getString("drawerSort", "az") ?: "az")
    fun setDrawerSort(v: String) { drawerSort.value = v; sp.edit().putString("drawerSort", v).apply() }

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

    val customTheme = MutableStateFlow(sp.getString("customTheme", null) ?: dev.foldterm.launcher.ui.Themes.DEFAULT_CUSTOM)
    fun setCustomTheme(v: String) { customTheme.value = v; sp.edit().putString("customTheme", v).apply() }

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

    /** Every setting as JSON, for backups. */
    fun exportJson(): String {
        val o = JSONObject()
        sp.all.forEach { (k, v) ->
            val e = JSONObject()
            when (v) {
                is String -> e.put("t", "s").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is Float -> e.put("t", "f").put("v", v.toDouble())
                is Boolean -> e.put("t", "b").put("v", v)
                is Set<*> -> e.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> return@forEach
            }
            o.put(k, e)
        }
        return JSONObject().put("app", "foldterm").put("version", 1).put("prefs", o).toString(2)
    }

    /** Replaces every setting with a backup made by [exportJson]. Returns how many were restored. */
    fun importJson(json: String): Int {
        val root = JSONObject(json)
        require(root.optString("app") == "foldterm") { "not a FoldTerm backup" }
        val o = root.getJSONObject("prefs")
        val ed = sp.edit().clear()
        var n = 0
        o.keys().forEach { k ->
            val e = o.getJSONObject(k)
            when (e.getString("t")) {
                "s" -> ed.putString(k, e.getString("v"))
                "i" -> ed.putInt(k, e.getInt("v"))
                "l" -> ed.putLong(k, e.getLong("v"))
                "f" -> ed.putFloat(k, e.getDouble("v").toFloat())
                "b" -> ed.putBoolean(k, e.getBoolean("v"))
                "set" -> { val a = e.getJSONArray("v"); ed.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet()) }
                else -> return@forEach
            }
            n++
        }
        ed.commit()
        return n
    }

    /** The version that last ran, so we can show what's new after an update. */
    fun swapLastVersion(current: String): String? {
        val last = sp.getString("lastVersion", null)
        if (last != current) sp.edit().putString("lastVersion", current).apply()
        return last
    }

    /** Cached USD exchange rates and when they were fetched. */
    fun cachedRates(): Pair<Long, Map<String, Double>>? = runCatching {
        val o = JSONObject(sp.getString("fxRates", null) ?: return null)
        val r = o.getJSONObject("rates")
        o.getLong("at") to r.keys().asSequence().associateWith { r.getDouble(it) }
    }.getOrNull()
    fun saveRates(rates: Map<String, Double>) {
        sp.edit().putString("fxRates", JSONObject().put("at", System.currentTimeMillis()).put("rates", JSONObject(rates as Map<*, *>)).toString()).apply()
    }

    // ── app renames ──
    val labels = MutableStateFlow(loadStringMap("labels"))
    fun setLabel(key: String, label: String?) {
        val next = labels.value.toMutableMap().apply { if (label.isNullOrBlank()) remove(key) else put(key, label.trim()) }
        labels.value = next
        sp.edit().putString("labels", JSONObject(next as Map<*, *>).toString()).apply()
    }
    private fun loadStringMap(key: String): Map<String, String> = runCatching {
        val o = JSONObject(sp.getString(key, "{}")!!)
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    // ── prompt history ──
    fun loadHistory(): List<String> = runCatching {
        val a = JSONArray(sp.getString("history", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())
    fun saveHistory(list: List<String>) { sp.edit().putString("history", JSONArray(list).toString()).apply() }

    // ── step history: day → steps, last 14 days ──
    fun stepHistory(): Map<String, Int> = runCatching {
        val o = JSONObject(sp.getString("stepHistory", "{}")!!)
        o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())
    fun recordStepsLive(day: String, steps: Int) { sp.edit().putString("stepsLastDay", day).putInt("stepsLast", steps).apply() }
    /** When the day rolls over, file yesterday's last count into the history. */
    fun rollStepHistory() {
        val day = sp.getString("stepsLastDay", null) ?: return
        val n = sp.getInt("stepsLast", 0)
        val map = stepHistory().toMutableMap().apply { put(day, maxOf(n, this[day] ?: 0)) }
        val keep = map.toSortedMap().entries.toList().takeLast(14).associate { it.key to it.value }
        sp.edit().putString("stepHistory", JSONObject(keep as Map<*, *>).toString()).apply()
    }

    /** Saved weather places, each "name|lat|lon". */
    val places = MutableStateFlow(
        runCatching { JSONArray(sp.getString("places", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList()),
    )
    fun setPlaces(v: List<String>) { places.value = v; sp.edit().putString("places", JSONArray(v).toString()).apply() }

    // ── news read state / saved stories ──
    fun loadNewsRead(): Set<String> = runCatching {
        val a = JSONArray(sp.getString("newsRead", "[]")); (0 until a.length()).map { a.getString(it) }.toSet()
    }.getOrDefault(emptySet())
    fun saveNewsRead(v: Set<String>) { sp.edit().putString("newsRead", JSONArray(v.toList()).toString()).apply() }
    fun loadNewsSaved(): List<NewsItem> = runCatching {
        val a = JSONArray(sp.getString("newsSaved", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            NewsItem(o.getString("title"), o.getString("link"), o.optString("source"), o.optLong("published", 0L).takeIf { it > 0 })
        }
    }.getOrDefault(emptyList())
    fun saveNewsSaved(v: List<NewsItem>) {
        val a = JSONArray()
        v.take(100).forEach { a.put(JSONObject().put("title", it.title).put("link", it.link).put("source", it.source).put("published", it.published ?: 0L)) }
        sp.edit().putString("newsSaved", a.toString()).apply()
    }

    /** Drawer folders: name → app keys. */
    val folders = MutableStateFlow(
        runCatching {
            val o = JSONObject(sp.getString("folders", "{}")!!)
            o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }
        }.getOrDefault(emptyMap()),
    )
    fun setFolders(v: Map<String, Set<String>>) {
        val clean = v.filterValues { it.isNotEmpty() }
        folders.value = clean
        val o = JSONObject()
        clean.forEach { (k, keys) -> o.put(k, JSONArray(keys.toList())) }
        sp.edit().putString("folders", o.toString()).apply()
    }
    fun toggleInFolder(folder: String, key: String) {
        val cur = folders.value.toMutableMap()
        val set = (cur[folder] ?: emptySet()).toMutableSet().apply { if (!add(key)) remove(key) }
        cur[folder] = set
        setFolders(cur)
    }

    /** Auto theme: when on, [dayTheme] between sunrise and sunset, [nightTheme] otherwise. */
    val autoTheme = MutableStateFlow(sp.getBoolean("autoTheme", false))
    val dayTheme = MutableStateFlow(sp.getString("dayTheme", "pastel") ?: "pastel")
    val nightTheme = MutableStateFlow(sp.getString("nightTheme", "phosphor") ?: "phosphor")
    fun setAutoTheme(v: Boolean) { autoTheme.value = v; sp.edit().putBoolean("autoTheme", v).apply() }
    fun setDayTheme(v: String) { dayTheme.value = v; sp.edit().putString("dayTheme", v).apply() }
    fun setNightTheme(v: String) { nightTheme.value = v; sp.edit().putString("nightTheme", v).apply() }

    val hiddenCalendars = MutableStateFlow(sp.getStringSet("hiddenCalendars", emptySet())!!.toSet())
    fun toggleCalendar(name: String) {
        val next = hiddenCalendars.value.toMutableSet().apply { if (!add(name)) remove(name) }
        hiddenCalendars.value = next; sp.edit().putStringSet("hiddenCalendars", next).apply()
    }
    /** Home summary rows the user switched off (by title). */
    val summaryHidden = MutableStateFlow(sp.getStringSet("summaryHidden", emptySet())!!.toSet())
    fun toggleSummaryRow(title: String) {
        val next = summaryHidden.value.toMutableSet().apply { if (!add(title)) remove(title) }
        summaryHidden.value = next; sp.edit().putStringSet("summaryHidden", next).apply()
    }

    val reduceMotion = MutableStateFlow(sp.getBoolean("reduceMotion", false))
    fun setReduceMotion(v: Boolean) { reduceMotion.value = v; sp.edit().putBoolean("reduceMotion", v).apply() }
    val hideStatusBar = MutableStateFlow(sp.getBoolean("hideStatusBar", false))
    fun setHideStatusBar(v: Boolean) { hideStatusBar.value = v; sp.edit().putBoolean("hideStatusBar", v).apply() }

    val keyClicks = MutableStateFlow(sp.getBoolean("keyClicks", false))
    fun setKeyClicks(v: Boolean) { keyClicks.value = v; sp.edit().putBoolean("keyClicks", v).apply() }

    /** Text size multiplier on top of the system font scale. */
    val textScale = MutableStateFlow(sp.getFloat("textScale", 1f))
    fun setTextScale(v: Float) { textScale.value = v; sp.edit().putFloat("textScale", v).apply() }

    /** World clocks: IANA zone ids. */
    val clocks = MutableStateFlow(sp.getString("clocks", "")!!.split('|').filter { it.isNotBlank() })
    fun setClocks(v: List<String>) { clocks.value = v; sp.edit().putString("clocks", v.joinToString("|")).apply() }

    /** Web search: system, duckduckgo, google, brave, startpage, kagi. */
    val searchEngine = MutableStateFlow(sp.getString("searchEngine", "system") ?: "system")
    fun setSearchEngine(v: String) { searchEngine.value = v; sp.edit().putString("searchEngine", v).apply() }
    val morningBrief = MutableStateFlow(sp.getBoolean("morningBrief", true))
    fun setMorningBrief(v: Boolean) { morningBrief.value = v; sp.edit().putBoolean("morningBrief", v).apply() }
    /** True the first time this is asked on a given day. */
    fun firstOpenToday(): Boolean {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())
        if (sp.getString("briefDay", "") == day) return false
        sp.edit().putString("briefDay", day).apply()
        return true
    }

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
            TaskItem(
                o.getLong("id"), o.getString("text"), o.optBoolean("done"), o.optLong("created"),
                due = o.optLong("due", 0L).takeIf { it > 0 }, starred = o.optBoolean("starred"),
            )
        }
    }.getOrDefault(emptyList())

    fun saveTasks(tasks: List<TaskItem>) {
        val arr = JSONArray()
        tasks.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("text", it.text).put("done", it.done).put("created", it.created)
                    .put("due", it.due ?: 0L).put("starred", it.starred),
            )
        }
        sp.edit().putString("tasks", arr.toString()).apply()
    }

    companion object {
        const val DEFAULT_FEEDS = "https://feeds.bbci.co.uk/news/rss.xml\nhttps://hnrss.org/frontpage"
    }
}
