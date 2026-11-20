package dev.foldterm.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlin.math.roundToInt
import dev.foldterm.launcher.data.LockAdmin
import dev.foldterm.launcher.data.Note
import dev.foldterm.launcher.data.WidgetSlot
import dev.foldterm.launcher.data.Widgets
import android.app.ActivityOptions
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.app.Application
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowLayoutInfo
import dev.foldterm.launcher.data.AppEntry
import dev.foldterm.launcher.data.AppsRepository
import dev.foldterm.launcher.data.CalEvent
import dev.foldterm.launcher.data.CalendarRepository
import dev.foldterm.launcher.data.ContactDetail
import dev.foldterm.launcher.data.ContactSummary
import dev.foldterm.launcher.data.ContactsRepository
import dev.foldterm.launcher.data.DeviceRepository
import dev.foldterm.launcher.data.DeviceVitals
import dev.foldterm.launcher.data.FoldState
import dev.foldterm.launcher.data.NewsItem
import dev.foldterm.launcher.data.Notif
import dev.foldterm.launcher.data.NotifListener
import dev.foldterm.launcher.data.NotificationStore
import dev.foldterm.launcher.data.NowPlayingStore
import dev.foldterm.launcher.data.OnlineRepository
import dev.foldterm.launcher.data.Photo
import dev.foldterm.launcher.data.Posture
import dev.foldterm.launcher.data.Prefs
import dev.foldterm.launcher.data.StepState
import dev.foldterm.launcher.data.TaskItem
import dev.foldterm.launcher.data.Weather
import dev.foldterm.launcher.shell.Line
import dev.foldterm.launcher.shell.LineKind
import dev.foldterm.launcher.shell.Shell
import dev.foldterm.launcher.ui.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Highlights shown in the console after updating to a version. */
private val WHATS_NEW = mapOf(
    "0.48.0" to listOf("Radio pane: internet radio (SomaFM presets, add your own streams); `radio defcon`, `radio stop`"),
    "0.47.0" to listOf("Games pane: serpent, mines and a typing sprint (`cd games`)"),
    "0.46.0" to listOf("`email maya lunch friday?` pre-fills the subject"),
    "0.45.0" to listOf("percentages in plain English: 18% of 240 · 15% off 80 · 64.50 + 15% · what % is 30 of 120"),
    "0.44.0" to listOf("`sms maya running late` pre-fills the message", "`contact add Sam 0400 123 456` creates a contact"),
    "0.43.0" to listOf("the big clock card shows your next event", "long-press the dock's drawer button to open the prompt"),
    "0.42.0" to listOf("pin a note to Home's note card; share notes to any app"),
    "0.41.0" to listOf("notification buttons (archive, mark as read, …) work from Alerts, Mail and Chat"),
    "0.40.0" to listOf("Settings → motion: reduced turns off glows, pane animations and the visualiser", "Settings → status bar: hide it for an immersive home screen"),
    "0.39.0" to listOf("tasks with due dates appear in the agenda (overdue under today) and tick off there", "optional key clicks in the prompt (Settings)"),
    "0.38.0" to listOf("`!!` repeats the last command, `!n` runs history entry n", "`ans` reuses the last calculator result", "`ice` lists emergency contacts; `tip` for tips"),
    "0.37.0" to listOf("Calendar → calendars: hide calendars you don't want to see", "Settings → home layout → summary rows: choose what the summary shows"),
    "0.36.0" to listOf("🎤 in the prompt: say a command (\"timer 5 minutes\", \"open spotify\")", "quick actions card for Home and the cover screen"),
    "0.35.0" to listOf("FoldTerm clock screen saver: `screensaver`, then pick “FoldTerm clock”"),
    "0.34.0" to listOf("type a setting's name (wallpaper, icon pack, bluetooth, developer options…) to jump to it"),
    "0.33.0" to listOf("auto theme: light by day, dark by night (Settings → theme)", "`neofetch` and `ip`"),
    "0.32.0" to listOf("Calendar quick-add field", "\"most used apps\" card for Home and the cover screen", "`wa <name>` and a WhatsApp button on contacts"),
    "0.31.0" to listOf("drawer folders: long-press an app → folders…; folder chips filter the drawer", "`folder <name>` opens the drawer at that folder"),
    "0.30.0" to listOf("text size: Settings → text size", "chain commands with ;  e.g. alias work=dnd on; split slack | chrome", "snooze notifications from Alerts, Mail and Chat"),
    "0.29.0" to listOf("notes: preview mode renders headings, bullets and tappable - [ ] checklists", "world clocks card: `clocks add tokyo`, then enable it in home layout or cover screen"),
    "0.28.0" to listOf("↑/↓ pick a prompt suggestion, ⏎ runs it", "choose your web search engine in Settings", "`brief`: a rundown of your day (also each morning)"),
    "0.27.0" to listOf("People → groups tab shows your contact labels", "News remembers what you've read; ☆ saves stories for later"),
    "0.26.0" to listOf("tasks take a due day and priority: `todo add pay rent fri !`", "overdue tasks turn red and sort first; tap ☆ to star"),
    "0.25.1" to listOf("app shortcuts: long-press an app for its shortcuts; the prompt finds them too (needs FoldTerm as home app)"),
    "0.24.0" to listOf("saved weather places: + add place in the Weather pane", "`weather tokyo` prints anywhere's forecast"),
    "0.23.0" to listOf("Calendar week view: seven day columns on one timeline; tap a slot to add an event"),
    "0.22.0" to listOf(
        "photo viewer: tap a photo, swipe through, share or open it",
        "album chips (Camera, Screenshots, …) filter the Photos grid",
    ),
    "0.21.0" to listOf(
        "custom theme: Settings → theme → custom, then pick accents and background",
        "panels, borders and glows are derived from your colours automatically",
    ),
    "0.20.0" to listOf(
        "rename any app: long-press → rename, or `rename chrome = web`",
        "prompt history survives restarts; `history` lists it",
        "next alarm and time-to-full charge in the Home summary",
        "7-day step history in Health; a widget card for the cover screen",
    ),
    "0.18.0" to listOf(
        "back up and restore every setting (Settings → backup)",
        "contact photos in People, quick dial and cover cards",
        "upcoming birthdays in the Home summary",
        "this note: a short what's-new after each update",
    ),
)

sealed interface UiRequest {
    data class Permissions(val perms: Array<String>) : UiRequest
    data object RoleHome : UiRequest
    data object ExportSettings : UiRequest
    data object Voice : UiRequest
    data object ImportSettings : UiRequest
    /** Ask the user to allow binding a widget (system dialog). */
    data class BindWidget(val id: Int, val info: AppWidgetProviderInfo) : UiRequest
    /** Run the widget's own configuration screen. */
    data class ConfigureWidget(val id: Int) : UiRequest
}

class LauncherViewModel(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    val prefs = Prefs(app)
    val appsRepo = AppsRepository(app)
    val calendarRepo = CalendarRepository(app)
    val contactsRepo = ContactsRepository(app)
    val deviceRepo = DeviceRepository(app)
    private val online = OnlineRepository(app)
    val shell = Shell(this)
    val widgetsHost = Widgets(app)
    val selectedNote = MutableStateFlow<Long?>(null)
    val widgetPickerOpen = MutableStateFlow(false)

    // navigation / chrome
    val section = MutableStateFlow(Section.HOME)
    val drawerOpen = MutableStateFlow(false)
    val shellOpen = MutableStateFlow(false)
    val input = MutableStateFlow("")
    val fold = MutableStateFlow(FoldState())
    val now = MutableStateFlow(System.currentTimeMillis())
    val uiRequests = MutableSharedFlow<UiRequest>(extraBufferCapacity = 4)

    // data
    /** Visible apps (hidden ones removed); [allApps] includes everything. */
    val apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val allApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val torchOn = MutableStateFlow(false)
    val events = MutableStateFlow<List<CalEvent>>(emptyList())
    val calStart = MutableStateFlow(Fmt.startOfDay(System.currentTimeMillis()))
    val selectedEvent = MutableStateFlow<String?>(null)
    val contacts = MutableStateFlow<List<ContactSummary>>(emptyList())
    val selectedContact = MutableStateFlow<ContactDetail?>(null)
    val selectedNotif = MutableStateFlow<String?>(null)
    val weather = MutableStateFlow<Weather?>(null)
    val weatherStatus = MutableStateFlow("")
    val news = MutableStateFlow<List<NewsItem>>(emptyList())
    val newsStatus = MutableStateFlow("")
    val photos = MutableStateFlow<List<Photo>>(emptyList())
    val steps = MutableStateFlow(StepState(available = false, permitted = false, steps = null))
    val vitals = MutableStateFlow<DeviceVitals?>(null)
    val tasks = MutableStateFlow(prefs.loadTasks())
    val perms = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val notifAccess = MutableStateFlow(false)
    val isDefaultHome = MutableStateFlow(false)
    val log = MutableStateFlow<List<Line>>(emptyList())
    val history = MutableStateFlow(prefs.loadHistory())
    val renameTarget = MutableStateFlow<AppEntry?>(null)
    val folderTarget = MutableStateFlow<AppEntry?>(null)
    /** Folder the drawer opens filtered to (from `folder <name>`). */
    val drawerFolder = MutableStateFlow<String?>(null)

    fun rename(app: AppEntry, label: String?) {
        prefs.setLabel(app.key, label)
        renameTarget.value = null
        loadApps()
    }

    /** Next alarm set in any clock app, or null. */
    fun nextAlarm(): Long? = runCatching {
        ctx.getSystemService(android.app.AlarmManager::class.java)?.nextAlarmClock?.triggerTime
    }.getOrNull()

    /** Milliseconds until full while charging, or null when unknown. */
    fun chargeTimeLeft(): Long? = runCatching {
        ctx.getSystemService(android.os.BatteryManager::class.java)?.computeChargeTimeRemaining()?.takeIf { it > 0 }
    }.getOrNull()

    val notifications get() = NotificationStore.items
    val nowPlaying get() = NowPlayingStore.state

    private val appsCallback: LauncherApps.Callback
    private var stepStop: (() -> Unit)? = null
    private var lastWeather = 0L
    private var lastNews = 0L
    private var refreshJob: Job? = null

    init {
        Fmt.is24 = DateFormat.is24HourFormat(app)
        appsCallback = appsRepo.registerCallback { loadApps() }
        loadApps()
        viewModelScope.launch {
            while (true) {
                now.value = System.currentTimeMillis()
                delay(15_000)
                // checked after the delay so it never runs while the ViewModel is still being built
                val until = prefs.focusUntil.value
                if (until in 1..System.currentTimeMillis()) stopFocus(completed = true)
            }
        }
        out("foldterm ${BuildConfig.VERSION_NAME} — type `help`", LineKind.DIM)
        val last = prefs.swapLastVersion(BuildConfig.VERSION_NAME)
        if (last != null && last != BuildConfig.VERSION_NAME) {
            out("updated $last → ${BuildConfig.VERSION_NAME}", LineKind.ACCENT)
            (WHATS_NEW[BuildConfig.VERSION_NAME.substringBefore('-')] ?: listOf("see the GitHub release notes for this version"))
                .forEach { out("  • $it") }
            shellOpen.value = true
        }
        dev.foldterm.launcher.data.CrashLog.read(app)?.let { trace ->
            val cause = trace.lines().drop(4).firstOrNull { it.isNotBlank() }?.trim() ?: "unknown"
            out("last session crashed: $cause", LineKind.ERR)
            out("run `crashlog` to see the details (and copy them)", LineKind.DIM)
            shellOpen.value = true
        }
    }

    override fun onCleared() {
        radio.release()
        appsRepo.unregister(appsCallback)
        stepStop?.invoke()
    }

    // ───────────────────────── refresh ─────────────────────────

    /** Demo mode (screenshots): live data loading is paused so sample data stays put. */
    var demo = false

    fun refresh(force: Boolean = false) {
        controlsTick.value++
        if (demo) { vitals.value = deviceRepo.vitals(); return }
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        if (prefs.morningBrief.value && hour in 4..11 && prefs.firstOpenToday()) {
            viewModelScope.launch { delay(2500); brief(); shellOpen.value = true }
        }
        if (refreshJob?.isActive == true && !force) return
        loadUsage()
        refreshJob = viewModelScope.launch {
            Fmt.is24 = DateFormat.is24HourFormat(ctx)
            refreshPermissions()
            launch { loadEvents() }
            launch { loadContacts() }
            launch { photos.value = withContext(Dispatchers.IO) { deviceRepo.photos() } }
            launch { vitals.value = withContext(Dispatchers.IO) { deviceRepo.vitals() } }
            startSteps()
            val t = System.currentTimeMillis()
            if (force || t - lastWeather > 20 * 60_000) launch { loadWeather() }
            if (force || t - lastNews > 30 * 60_000) launch { loadNews() }
        }
    }

    fun refreshPermissions() {
        if (demo) return
        fun has(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        perms.value = linkedMapOf(
            "calendar" to has(Manifest.permission.READ_CALENDAR),
            "contacts" to has(Manifest.permission.READ_CONTACTS),
            "location" to has(Manifest.permission.ACCESS_COARSE_LOCATION),
            "activity" to has(Manifest.permission.ACTIVITY_RECOGNITION),
            "photos" to deviceRepo.photosGranted(),
        )
        notifAccess.value = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        isDefaultHome.value = runCatching {
            ctx.packageManager.resolveActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY,
            )?.activityInfo?.packageName == ctx.packageName
        }.getOrDefault(false)
    }

    fun requestAllPermissions() {
        val list = mutableListOf(
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
        )
        list += if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        uiRequests.tryEmit(UiRequest.Permissions(list.toTypedArray()))
    }

    fun requestPermission(name: String) {
        val p = when (name) {
            "calendar" -> Manifest.permission.READ_CALENDAR
            "contacts" -> Manifest.permission.READ_CONTACTS
            "location" -> Manifest.permission.ACCESS_COARSE_LOCATION
            "activity" -> Manifest.permission.ACTIVITY_RECOGNITION
            "photos" -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
            else -> return
        }
        uiRequests.tryEmit(UiRequest.Permissions(arrayOf(p)))
    }

    fun onPermissionsResult() {
        refreshPermissions()
        refresh(force = true)
    }

    fun loadApps() {
        viewModelScope.launch {
            val labels = prefs.labels.value
            allApps.value = withContext(Dispatchers.IO) { appsRepo.load() }
                .map { a -> labels[a.key]?.let { a.copy(label = it) } ?: a }
                .sortedWith(compareBy({ it.label.lowercase() }, { it.isWork }))
            applyHidden()
            rebuildShortcutIndex()
        }
    }

    /** Every calendar seen in the loaded range, hidden or not. */
    val calendarNames = MutableStateFlow<List<String>>(emptyList())

    suspend fun loadEvents() {
        val start = calStart.value
        val all = withContext(Dispatchers.IO) { calendarRepo.load(start, Fmt.addDays(start, 42)) }
        calendarNames.value = all.map { it.calendarName }.filter { it.isNotBlank() }.distinct().sorted()
        val hidden = prefs.hiddenCalendars.value
        events.value = all.filter { it.calendarName !in hidden }
    }

    fun toggleCalendar(name: String) {
        prefs.toggleCalendar(name)
        viewModelScope.launch { loadEvents() }
    }

    fun shiftCalendar(days: Int?) {
        calStart.value = if (days == null) Fmt.startOfDay(System.currentTimeMillis()) else Fmt.addDays(calStart.value, days)
        viewModelScope.launch { loadEvents() }
    }

    fun setCalendarStart(dayStart: Long) {
        calStart.value = Fmt.startOfDay(dayStart)
        viewModelScope.launch { loadEvents() }
    }

    private suspend fun loadContacts() {
        contacts.value = withContext(Dispatchers.IO) { contactsRepo.load() }
        val sel = selectedContact.value
        if (sel != null) contacts.value.firstOrNull { it.id == sel.summary.id }?.let { selectContact(it) }
    }

    val contactGroups = MutableStateFlow<Map<String, Set<Long>>>(emptyMap())

    fun loadGroups() {
        viewModelScope.launch { contactGroups.value = withContext(Dispatchers.IO) { contactsRepo.groups() } }
    }

    // news read state & saved stories
    val newsRead = MutableStateFlow(prefs.loadNewsRead())
    val newsSaved = MutableStateFlow(prefs.loadNewsSaved())

    fun openStory(n: NewsItem) {
        if (n.link.isNotBlank()) openUrl(n.link)
        if (n.link !in newsRead.value) { newsRead.value = (newsRead.value + n.link).toList().takeLast(400).toSet(); prefs.saveNewsRead(newsRead.value) }
    }

    fun toggleSaved(n: NewsItem) {
        newsSaved.value = if (newsSaved.value.any { it.link == n.link }) newsSaved.value.filterNot { it.link == n.link } else listOf(n) + newsSaved.value
        prefs.saveNewsSaved(newsSaved.value)
    }

    fun markAllRead(list: List<NewsItem>) {
        newsRead.value = (newsRead.value + list.map { it.link }).toList().takeLast(400).toSet()
        prefs.saveNewsRead(newsRead.value)
    }

    fun selectContact(c: ContactSummary) {
        viewModelScope.launch {
            selectedContact.value = withContext(Dispatchers.IO) { contactsRepo.detail(c) }
        }
    }

    suspend fun loadWeather() {
        weatherStatus.value = "locating…"
        val loc = online.location(prefs.manualLocation.value)
        if (loc == null) {
            weatherStatus.value = if (online.locationGranted()) "no location fix" else "location permission needed"
            return
        }
        weatherStatus.value = "fetching…"
        val w = withContext(Dispatchers.IO) {
            runCatching { online.weather(loc.first, loc.second, prefs.fahrenheit.value) }
        }
        w.onSuccess { weather.value = it; weatherStatus.value = ""; lastWeather = System.currentTimeMillis() }
            .onFailure { weatherStatus.value = "weather: ${it.message ?: it.javaClass.simpleName}" }
    }

    suspend fun loadNews() {
        newsStatus.value = "fetching…"
        val feeds = prefs.feeds.value.lines().map { it.trim() }.filter { it.startsWith("http") }
        val items = withContext(Dispatchers.IO) { online.news(feeds) }
        news.value = items
        newsStatus.value = if (items.isEmpty()) "no stories (offline or bad feed url)" else ""
        if (items.isNotEmpty()) lastNews = System.currentTimeMillis()
    }

    fun reloadWeather() { viewModelScope.launch { loadWeather() } }

    // ───────────────────────── weather places ─────────────────────────

    /** Saved place shown in the Weather pane ("name|lat|lon"), or null for here. */
    val weatherPlace = MutableStateFlow<String?>(null)
    val placeWeather = MutableStateFlow<Weather?>(null)
    val placeResults = MutableStateFlow<List<OnlineRepository.Place>>(emptyList())

    fun showPlace(spec: String?) {
        weatherPlace.value = spec
        placeWeather.value = null
        if (spec == null) return
        val p = spec.split('|')
        val lat = p.getOrNull(1)?.toDoubleOrNull() ?: return
        val lon = p.getOrNull(2)?.toDoubleOrNull() ?: return
        viewModelScope.launch {
            placeWeather.value = withContext(Dispatchers.IO) { runCatching { online.weather(lat, lon, prefs.fahrenheit.value) }.getOrNull() }
                ?.copy(place = p[0])
            if (placeWeather.value == null) out("couldn't load weather for ${p[0]}", LineKind.ERR)
        }
    }

    fun searchPlaces(q: String) {
        if (q.isBlank()) { placeResults.value = emptyList(); return }
        viewModelScope.launch {
            placeResults.value = withContext(Dispatchers.IO) { runCatching { online.geocode(q) }.getOrDefault(emptyList()) }
        }
    }

    fun savePlace(p: OnlineRepository.Place) {
        val spec = "${p.name}|${p.lat}|${p.lon}"
        if (prefs.places.value.none { it.substringBefore('|') == p.name }) prefs.setPlaces(prefs.places.value + spec)
        placeResults.value = emptyList()
        showPlace(spec)
    }

    fun removePlace(spec: String) {
        prefs.setPlaces(prefs.places.value - spec)
        if (weatherPlace.value == spec) showPlace(null)
    }

    /** Prints a one-off forecast for any city (used by `weather <city>`). */
    fun weatherFor(q: String) {
        viewModelScope.launch {
            val place = withContext(Dispatchers.IO) { runCatching { online.geocode(q).firstOrNull() }.getOrNull() }
            if (place == null) { out("no place called '$q'", LineKind.ERR); return@launch }
            val w = withContext(Dispatchers.IO) { runCatching { online.weather(place.lat, place.lon, prefs.fahrenheit.value) }.getOrNull() }
            if (w == null) { out("couldn't fetch weather", LineKind.ERR); return@launch }
            out("${w.glyph}  ${w.temp.toInt()}°${w.unit} ${w.description}  (${place.name})", LineKind.ACCENT)
            out("   high ${w.high.toInt()}° / low ${w.low.toInt()}°${if (w.rainInHours == null) " · no rain in the next 12h" else " · rain likely in ~${w.rainInHours}h"}")
        }
    }
    fun reloadNews() { viewModelScope.launch { loadNews() } }

    private fun startSteps() {
        val available = deviceRepo.stepSensorAvailable()
        val permitted = deviceRepo.stepsPermitted()
        steps.update { it.copy(available = available, permitted = permitted) }
        if (stepStop != null || !available || !permitted) return
        stepStop = deviceRepo.listenSteps { raw ->
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val base = prefs.stepBaseline()
            if (base != null && base.first != day) prefs.rollStepHistory()
            val baseline = if (base == null || base.first != day || raw < base.second) {
                prefs.setStepBaseline(day, raw); raw
            } else base.second
            steps.update { it.copy(steps = raw - baseline) }
            prefs.recordStepsLive(day, raw - baseline)
        }
    }

    // ───────────────────────── fold ─────────────────────────

    fun updateFold(info: WindowLayoutInfo) {
        val f = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
        fold.value = if (f == null) FoldState() else {
            val vertical = f.orientation == FoldingFeature.Orientation.VERTICAL
            val half = f.state == FoldingFeature.State.HALF_OPENED
            FoldState(
                posture = when {
                    half && !vertical -> Posture.TABLETOP
                    half -> Posture.BOOK
                    else -> Posture.FLAT
                },
                hinge = android.graphics.Rect(f.bounds),
                vertical = vertical,
                separating = f.isSeparating,
            )
        }
    }

    // ───────────────────────── navigation ─────────────────────────

    fun go(s: Section) {
        section.value = s
        drawerOpen.value = false
        if (s == Section.HEALTH) vitals.value = deviceRepo.vitals()
    }

    fun moveSection(s: Section, dir: Int) {
        val list = orderedSections(prefs.sectionOrder.value).toMutableList()
        val i = list.indexOf(s)
        val j = i + dir
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        prefs.setSectionOrder(list.map { it.id })
    }

    fun onHomePressed() {
        if (shellOpen.value || drawerOpen.value) {
            shellOpen.value = false; drawerOpen.value = false; input.value = ""
        } else {
            section.value = Section.HOME
        }
    }

    fun onBack(): Boolean {
        return when {
            shellOpen.value -> { shellOpen.value = false; input.value = ""; true }
            splitPick.value != null -> { splitPick.value = null; true }
            drawerOpen.value -> { drawerOpen.value = false; true }
            section.value != Section.HOME -> { section.value = Section.HOME; true }
            else -> false
        }
    }

    // ───────────────────────── actions ─────────────────────────

    fun start(intent: Intent, failMsg: String = "no app can handle that"): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        out(failMsg, LineKind.ERR); false
    } catch (e: SecurityException) {
        out("denied: ${e.message}", LineKind.ERR); false
    }

    private fun applyHidden() {
        val hidden = prefs.hidden.value + (if (focusActive()) prefs.focusBlock.value else emptySet())
        apps.value = allApps.value.filter { it.key !in hidden }
    }

    // ───────────────────────── focus mode ─────────────────────────

    fun focusActive(): Boolean = prefs.focusUntil.value > System.currentTimeMillis()
    fun startFocus(minutes: Int, label: String = "") {
        val ms = minutes * 60_000L
        prefs.setFocus(System.currentTimeMillis() + ms, label)
        applyHidden()
        if (prefs.focusDnd.value) controls.setDnd(true)?.let { out("focus: $it — DND not changed", LineKind.DIM) }
        // a clock-app timer rings at the end even if the launcher is in the background
        runCatching {
            ctx.startActivity(
                Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
                    .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, minutes * 60)
                    .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                    .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Focus${if (label.isNotBlank()) ": $label" else ""} done")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        out("focus ${minutes}m${if (label.isNotBlank()) " · $label" else ""} — ${prefs.focusBlock.value.size} apps hidden", LineKind.OK)
    }

    fun stopFocus(completed: Boolean = false) {
        if (prefs.focusUntil.value == 0L) return
        val end = minOf(System.currentTimeMillis(), prefs.focusUntil.value)
        val start = prefs.focusStart().takeIf { it > 0 } ?: end
        if (end > start) prefs.addFocus(end - start)
        prefs.setFocus(0L, "")
        applyHidden()
        if (prefs.focusDnd.value) controls.setDnd(false)
        out(if (completed) "focus session complete ✓" else "focus ended", LineKind.OK)
    }

    fun setHidden(app: AppEntry, hide: Boolean) {
        prefs.setHidden(app.key, hide)
        applyHidden()
    }

    fun recentApps(limit: Int = 8): List<AppEntry> =
        prefs.recent.value.mapNotNull { k -> apps.value.firstOrNull { it.key == k } }.take(limit)

    fun usageOf(app: AppEntry): Int = prefs.usage.value[app.key] ?: 0

    fun launch(app: AppEntry) {
        runCatching { appsRepo.launch(app); prefs.recordLaunch(app.key) }
            .onFailure { out("failed to launch ${app.label}: ${it.message}", LineKind.ERR) }
        shellOpen.value = false
        drawerOpen.value = false
        input.value = ""
    }

    fun launchKey(key: String): Boolean {
        val app = apps.value.firstOrNull { it.key == key && !it.isWork } ?: apps.value.firstOrNull { it.key == key }
            ?: apps.value.firstOrNull { it.packageName == key.substringBefore('/') } ?: return false
        launch(app); return true
    }

    /** Shortcut index for prompt search, rebuilt with the app list (only when we're the home app). */
    val shortcutIndex = MutableStateFlow<List<Pair<AppEntry, android.content.pm.ShortcutInfo>>>(emptyList())

    fun rebuildShortcutIndex() {
        viewModelScope.launch {
            shortcutIndex.value = withContext(Dispatchers.IO) {
                if (!appsRepo.canUseShortcuts()) emptyList()
                else apps.value.filter { !it.isWork }.flatMap { a -> appsRepo.shortcuts(a).map { a to it } }
            }
        }
    }

    fun startShortcut(s: android.content.pm.ShortcutInfo) {
        runCatching { appsRepo.startShortcut(s) }.onFailure { out("shortcut failed: ${it.message}", LineKind.ERR) }
        shellOpen.value = false; drawerOpen.value = false; input.value = ""
    }

    fun dockApps(): List<AppEntry> {
        val list = apps.value
        val keys = prefs.dock.value ?: appsRepo.defaultDock(list)
        return keys.mapNotNull { k -> list.firstOrNull { it.key == k && !it.isWork } ?: list.firstOrNull { it.key == k } }
    }

    fun pinToDock(app: AppEntry) {
        val cur = (prefs.dock.value ?: appsRepo.defaultDock(apps.value)).toMutableList()
        if (app.key !in cur) cur += app.key
        prefs.setDock(cur.take(8))
    }

    fun unpinFromDock(app: AppEntry) {
        val cur = (prefs.dock.value ?: appsRepo.defaultDock(apps.value)).toMutableList()
        cur.remove(app.key)
        prefs.setDock(cur)
    }

    val iconPacks = dev.foldterm.launcher.data.IconPacks(app)
    val controls = dev.foldterm.launcher.data.Controls(app)
    val radio = dev.foldterm.launcher.data.Radio(app)
    val stations = MutableStateFlow(prefs.loadStations())

    fun addStation(s: dev.foldterm.launcher.data.Station) {
        stations.value = stations.value.filter { it.url != s.url } + s
        prefs.saveStations(stations.value)
    }

    fun removeStation(s: dev.foldterm.launcher.data.Station) {
        if (radio.playing.value?.url == s.url) radio.stop()
        stations.value = stations.value.filter { it.url != s.url }
        prefs.saveStations(stations.value)
    }
    val screenTime = dev.foldterm.launcher.data.ScreenTime(app)
    val usage = MutableStateFlow<dev.foldterm.launcher.data.UsageReport?>(null)

    fun loadUsage() {
        if (demo) return
        viewModelScope.launch { usage.value = withContext(Dispatchers.Default) { runCatching { screenTime.report() }.getOrNull() } }
    }
    /** Bumped after a control changes so the Controls pane re-reads system state. */
    val controlsTick = MutableStateFlow(0)

    /** Runs a control action; on failure explains what to grant and opens the right screen. */
    fun control(action: () -> String?) {
        val err = action()
        controlsTick.value++
        if (err != null) {
            out(err, LineKind.ERR)
            when {
                "Do Not Disturb" in err -> start(controls.dndAccessIntent())
                "system settings" in err -> start(controls.writeSettingsIntent())
            }
        }
    }

    /** App icon honouring the icon pack setting; packs fall back to the app's own icon. */
    fun icon(app: AppEntry): Bitmap? {
        val pack = prefs.iconPack.value
        if (prefs.iconStyle.value == "pack" && pack.isNotEmpty()) {
            iconPacks.icon(pack, app.component)?.let { return it }
        }
        return appsRepo.icon(app)
    }

    /** Opens a WhatsApp chat with [number] (digits only, with country code if stored). */
    fun whatsapp(number: String, text: String? = null) {
        val digits = number.filter { it.isDigit() || it == '+' }.removePrefix("+")
        openUrl("https://wa.me/$digits${text?.let { "?text=" + Uri.encode(it) } ?: ""}")
    }

    fun dial(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
    fun sms(number: String, body: String? = null) = start(
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply { if (!body.isNullOrBlank()) putExtra("sms_body", body) },
    )
    fun email(address: String, subject: String? = null) = start(
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply { if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject) },
    )
    fun directions(place: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}")), "no maps app")
    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "no browser")
    fun webSearch(q: String) {
        val e = Uri.encode(q)
        val url = when (prefs.searchEngine.value) {
            "duckduckgo" -> "https://duckduckgo.com/?q=$e"
            "google" -> "https://www.google.com/search?q=$e"
            "brave" -> "https://search.brave.com/search?q=$e"
            "startpage" -> "https://www.startpage.com/do/search?q=$e"
            "kagi" -> "https://kagi.com/search?q=$e"
            else -> null
        }
        if (url != null) { openUrl(url); return }
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (ex: ActivityNotFoundException) {
            openUrl("https://duckduckgo.com/?q=$e")
        }
    }

    /** Daytime = between today's sunrise and sunset (from the forecast), else 07:00–19:00. */
    fun isDaytime(nowMs: Long, w: Weather?): Boolean {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val mins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        fun parse(hm: String?): Int? = hm?.split(':')?.let { p -> p.getOrNull(0)?.toIntOrNull()?.let { h -> h * 60 + (p.getOrNull(1)?.toIntOrNull() ?: 0) } }
        val rise = parse(w?.sunrise) ?: (7 * 60)
        val set = parse(w?.sunset) ?: (19 * 60)
        return mins in rise until set
    }

    /** neofetch-style device summary. */
    fun neofetch() {
        val v = deviceRepo.vitals()
        val dm = ctx.resources.displayMetrics
        val art = listOf(
            "   ▄▄▄▄▄▄▄▄   ", "  █ ❯ _    █  ", "  █        █  ", "  █▄▄▄▄▄▄▄▄█  ", "   ▀▀▀▀▀▀▀▀   ", "              ", "              ", "              ", "              ",
        )
        val info = listOf(
            "${prefs.userName.value.ifBlank { "user" }}@${Build.MODEL.lowercase().replace(' ', '-')}",
            "os       Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "host     ${Build.MANUFACTURER} ${Build.MODEL}",
            "shell    foldterm ${BuildConfig.VERSION_NAME}",
            "uptime   ${Fmt.duration(v.uptimeMs)}",
            "display  ${dm.widthPixels}×${dm.heightPixels} · ${fold.value.label}",
            "theme    ${prefs.theme.value}${if (prefs.autoTheme.value) " (auto)" else ""} · icons ${prefs.iconStyle.value}",
            "apps     ${allApps.value.size} (${prefs.hidden.value.size} hidden)",
            "battery  ${v.battery}%${if (v.charging) " ⚡" else ""} · memory ${Fmt.gb(v.ramTotalGb - v.ramAvailGb)}/${Fmt.gb(v.ramTotalGb)}",
        )
        art.zip(info).forEachIndexed { i, (a, b) -> out("$a $b", if (i == 0) LineKind.ACCENT else LineKind.OUT) }
        out("               " + listOf("█", "█", "█", "█", "█", "█").joinToString(""), LineKind.OK)
    }

    /** Local IP addresses (Wi-Fi / mobile / VPN). */
    fun ipAddresses(): List<String> = runCatching {
        java.net.NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }.flatMap { ni ->
            ni.inetAddresses.toList().filter { !it.isLoopbackAddress && it is java.net.Inet4Address }.map { "${ni.name.padEnd(8)} ${it.hostAddress}" }
        }
    }.getOrDefault(emptyList())

    /** A terminal-style rundown of the day. */
    fun brief() {
        val nowMs = System.currentTimeMillis()
        out("── ${Fmt.greeting(nowMs).lowercase()}${prefs.userName.value.takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""} · ${Fmt.longDate(nowMs)} ──", LineKind.ACCENT)
        weather.value?.let { w ->
            out("  ${w.glyph} ${w.temp.toInt()}°, ${w.description.lowercase()}; high ${w.high.toInt()}°${if (w.rainInHours != null) ", rain in ~${w.rainInHours}h" else ", no rain soon"}")
        }
        val end = Fmt.addDays(Fmt.startOfDay(nowMs), 1)
        val today = events.value.filter { it.end > nowMs && it.begin < end }
        if (today.isEmpty()) out("  ▦ nothing left on the calendar today")
        else {
            out("  ▦ ${today.size} event${if (today.size > 1) "s" else ""} left today:")
            today.take(5).forEach { e -> out("      ${if (e.allDay) "all-day" else Fmt.time(e.begin)}  ${e.title}") }
        }
        val open = sortedTasks(tasks.value).filter { !it.done }
        val overdue = open.count { it.due != null && it.due < Fmt.startOfDay(nowMs) }
        if (open.isNotEmpty()) out("  ✓ ${open.size} task${if (open.size > 1) "s" else ""}${if (overdue > 0) " ($overdue overdue)" else ""}; next: ${open.first().text}")
        upcomingBirthdays(7).take(2).forEach { (c, d) -> out("  ✦ ${c.name}'s birthday ${when (d) { 0 -> "is today"; 1 -> "is tomorrow"; else -> "in $d days" }}") }
        val waiting = NotificationStore.items.value.count { !it.removed && it.kind != dev.foldterm.launcher.data.NotifKind.OTHER && it.pkg !in prefs.mutedApps.value }
        if (notifAccess.value) out("  @ $waiting message${if (waiting == 1) "" else "s"} waiting")
    }

    fun openNotif(n: Notif) {
        val pi = n.contentIntent
        if (pi == null) {
            apps.value.firstOrNull { it.packageName == n.pkg }?.let { launch(it) }
            return
        }
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(ctx, 0, null, null, null, null, opts.toBundle())
            } else {
                pi.send()
            }
        } catch (e: PendingIntent.CanceledException) {
            apps.value.firstOrNull { it.packageName == n.pkg }?.let { launch(it) }
        }
    }

    fun replyNotif(n: Notif, text: String): Boolean {
        val action = n.replyAction ?: return false
        val ri = action.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: return false
        return try {
            val intent = Intent()
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, Bundle().apply { putCharSequence(ri.resultKey, text) })
            action.actionIntent.send(ctx, 0, intent)
            out("replied to ${n.title}", LineKind.OK)
            true
        } catch (e: Exception) {
            out("reply failed: ${e.message}", LineKind.ERR); false
        }
    }

    /** Fires one of a notification's own buttons (archive, mark as read…). */
    fun runNotifAction(n: Notif, a: android.app.Notification.Action) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                a.actionIntent.send(ctx, 0, null, null, null, null, opts.toBundle())
            } else a.actionIntent.send()
            out("${a.title} · ${n.title.ifBlank { n.app }}", LineKind.OK)
        } catch (e: Exception) {
            out("${a.title} failed: ${e.message}", LineKind.ERR)
        }
    }

    fun snoozeNotif(n: Notif, minutes: Int) {
        if (NotifListener.snooze(n.key, minutes * 60_000L)) {
            NotificationStore.markRemoved(n.key)
            out("snoozed ${n.title.ifBlank { n.app }} for ${Fmt.duration(minutes * 60_000L)}", LineKind.OK)
        } else out("couldn't snooze (notification access off?)", LineKind.ERR)
    }

    fun dismissNotif(n: Notif) {
        NotifListener.dismiss(n.key)
        NotificationStore.markRemoved(n.key)
    }

    // ───────────────────────── notes ─────────────────────────

    fun newNote(text: String = ""): Long {
        val id = System.currentTimeMillis()
        prefs.saveNotes(listOf(Note(id, text, id)) + prefs.notes.value)
        selectedNote.value = id
        return id
    }

    fun updateNote(id: Long, text: String) {
        prefs.saveNotes(
            prefs.notes.value.map { if (it.id == id) it.copy(text = text, updated = System.currentTimeMillis()) else it }
                .sortedByDescending { it.updated },
        )
    }

    fun deleteNote(id: Long) {
        prefs.saveNotes(prefs.notes.value.filterNot { it.id == id })
        if (selectedNote.value == id) selectedNote.value = prefs.notes.value.firstOrNull()?.id
    }

    fun openNote(id: Long) { selectedNote.value = id; go(Section.NOTES); shellOpen.value = false; input.value = "" }

    // ───────────────────────── widgets ─────────────────────────

    /** Step 1: allocate an id and bind it, asking the user if needed. */
    fun addWidget(info: AppWidgetProviderInfo) {
        widgetPickerOpen.value = false
        val id = widgetsHost.allocate()
        if (widgetsHost.bindIfAllowed(id, info)) onWidgetBound(id, true)
        else uiRequests.tryEmit(UiRequest.BindWidget(id, info))
    }

    /** Step 2: after binding, run the widget's configure screen when it has one. */
    fun onWidgetBound(id: Int, ok: Boolean) {
        if (!ok) { widgetsHost.delete(id); out("widget not added", LineKind.DIM); return }
        val info = widgetsHost.info(id)
        if (info?.configure != null) uiRequests.tryEmit(UiRequest.ConfigureWidget(id)) else onWidgetConfigured(id, true)
    }

    /** Step 3: keep it. */
    fun onWidgetConfigured(id: Int, ok: Boolean) {
        if (!ok) { widgetsHost.delete(id); out("widget setup cancelled", LineKind.DIM); return }
        val info = widgetsHost.info(id)
        val density = ctx.resources.displayMetrics.density
        val h = ((info?.minHeight ?: 0) / density).toInt().coerceIn(110, 420)
        prefs.saveWidgets(prefs.widgets.value + WidgetSlot(id, h))
        out("widget added", LineKind.OK)
    }

    fun removeWidget(id: Int) {
        widgetsHost.delete(id)
        prefs.saveWidgets(prefs.widgets.value.filterNot { it.id == id })
    }

    fun resizeWidget(id: Int, deltaDp: Int) {
        prefs.saveWidgets(prefs.widgets.value.map { if (it.id == id) it.copy(heightDp = (it.heightDp + deltaDp).coerceIn(80, 900)) else it })
    }

    fun moveWidget(id: Int, dir: Int) {
        val list = prefs.widgets.value.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        prefs.saveWidgets(list)
    }

    // ───────────────────────── misc device actions ─────────────────────────

    /** App chosen as the first half of a split from the drawer; the next tap picks its partner. */
    val splitPick = MutableStateFlow<AppEntry?>(null)

    /** Split screen needs a wide window: the unfolded inner screen, not the cover screen. */
    private fun splitAvailable(): Boolean {
        val cfg = ctx.resources.configuration
        if (cfg.smallestScreenWidthDp < 600 && cfg.screenWidthDp < 600) {
            out("split screen needs the inner screen — unfold and try again", LineKind.ERR)
            return false
        }
        return true
    }

    private fun mainIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /**
     * Opens [left] and [right] side by side through [SplitActivity]: the home screen itself can't
     * start split screen, but an ordinary activity can.
     */
    fun split(left: AppEntry, right: AppEntry) {
        splitPick.value = null
        drawerOpen.value = false
        shellOpen.value = false
        input.value = ""
        if (!splitAvailable()) return
        if (left.isWork || right.isWork) { out("split doesn't support work-profile apps yet", LineKind.ERR); return }
        val i = Intent(ctx, SplitActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra(SplitActivity.EXTRA_LEFT, mainIntent(left))
            .putExtra(SplitActivity.EXTRA_RIGHT, mainIntent(right))
        runCatching { ctx.startActivity(i) }.onFailure { out("split: ${it.message}", LineKind.ERR); return }
        prefs.recordLaunch(left.key); prefs.recordLaunch(right.key)
        out("split ${left.label} | ${right.label}", LineKind.OK)
    }

    fun splitKeys(leftKey: String, rightKey: String): Boolean {
        val l = apps.value.firstOrNull { it.key == leftKey && !it.isWork }
        val r = apps.value.firstOrNull { it.key == rightKey && !it.isWork }
        if (l == null || r == null) { out("an app in that pair isn't installed any more", LineKind.ERR); return false }
        split(l, r); return true
    }

    fun launchPair(name: String): Boolean {
        val p = prefs.pairs.value[name] ?: run { out("no pair '$name'", LineKind.ERR); return false }
        return splitKeys(p.first, p.second)
    }

    fun labelOf(key: String): String = allApps.value.firstOrNull { it.key == key }?.label ?: key.substringBefore('/')

    /**
     * Opens [app] as a floating window (Samsung "pop-up view" / freeform). Uses public launch bounds,
     * plus the freeform windowing mode where the platform still accepts it.
     */
    fun popup(app: AppEntry) {
        drawerOpen.value = false; shellOpen.value = false; input.value = ""
        val dm = ctx.resources.displayMetrics
        val w = (dm.widthPixels * 0.6f).toInt()
        val h = (dm.heightPixels * 0.6f).toInt()
        val left = (dm.widthPixels - w) / 2
        val top = (dm.heightPixels - h) / 2
        val opts = ActivityOptions.makeBasic().setLaunchBounds(android.graphics.Rect(left, top, left + w, top + h))
        runCatching {
            ActivityOptions::class.java.getMethod("setLaunchWindowingMode", Int::class.javaPrimitiveType).invoke(opts, 5)
        }
        runCatching {
            ctx.startActivity(mainIntent(app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK), opts.toBundle())
            prefs.recordLaunch(app.key)
        }.onFailure { out("pop-up: ${it.message}", LineKind.ERR) }
    }

    fun volume(percent: Int?) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (percent == null) {
            out("media volume ${am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max}%")
            return
        }
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * percent.coerceIn(0, 100) / 100f).toInt(), AudioManager.FLAG_SHOW_UI)
        }.onSuccess { out("media volume → ${percent.coerceIn(0, 100)}%", LineKind.OK) }
            .onFailure { out("volume: ${it.message}", LineKind.ERR) }
    }

    /** Media volume as 0..1. */
    fun volumeFraction(): Float {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return 0f
        return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun setVolumeFraction(f: Float) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, (f.coerceIn(0f, 1f) * max).roundToInt(), 0) }
    }

    /** Where audio is going: the first headphone/Bluetooth/USB output, else the phone speaker. */
    fun audioOutputs(): List<String> {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return emptyList()
        val external = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_HDMI,
        ) + (if (Build.VERSION.SDK_INT >= 31) setOf(AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER) else emptySet())
        val names = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.type in external }
            .map { it.productName?.toString()?.takeIf { n -> n.isNotBlank() } ?: "headphones" }
            .distinct()
        return names.ifEmpty { listOf("phone speaker") }
    }

    fun appLabelForPackage(pkg: String): String = allApps.value.firstOrNull { it.packageName == pkg }?.label ?: pkg.substringAfterLast('.')

    fun openPackage(pkg: String) {
        apps.value.firstOrNull { it.packageName == pkg && !it.isWork }?.let { launch(it) }
    }

    fun copy(text: String) {
        ctx.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("foldterm", text))
        out("copied", LineKind.OK)
    }

    /** Pulls down the notification shade (hidden but widely available API). */
    fun expandNotifications() {
        runCatching {
            @SuppressLint("WrongConstant")
            val sbm = ctx.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
        }.onFailure { out("couldn't open the notification shade on this device", LineKind.DIM) }
    }

    fun setTorch(on: Boolean?) {
        val cm = ctx.getSystemService(CameraManager::class.java) ?: return
        val id = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: run { out("no flashlight on this device", LineKind.ERR); return }
        val next = on ?: !torchOn.value
        try {
            cm.setTorchMode(id, next)
            torchOn.value = next
            out("torch ${if (next) "on" else "off"}", LineKind.OK)
        } catch (e: Exception) {
            out("torch: ${e.message}", LineKind.ERR)
        }
    }

    fun lockScreen() {
        if (!LockAdmin.lock(ctx)) {
            out("screen lock isn't enabled — allow it in Settings → gestures", LineKind.ERR)
            start(LockAdmin.enableIntent(ctx))
        }
    }

    /** Runs a gesture's configured action. */
    fun gesture(action: String) {
        when (action) {
            "drawer" -> drawerOpen.value = !drawerOpen.value
            "shade" -> expandNotifications()
            "prompt" -> shellOpen.value = true
            "alerts" -> go(Section.NOTIFS)
            "media" -> go(Section.MEDIA)
            "lock" -> lockScreen()
            "torch" -> setTorch(null)
            "home" -> go(Section.HOME)
        }
    }

    // ───────────────────────── backup ─────────────────────────

    fun exportSettings(uri: Uri) {
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(prefs.exportJson().toByteArray()) } ?: error("can't write there")
        }.onSuccess { out("settings backed up ✓", LineKind.OK) }.onFailure { out("backup failed: ${it.message}", LineKind.ERR) }
    }

    fun importSettings(uri: Uri) {
        runCatching {
            val json = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } ?: error("can't read that file")
            prefs.importJson(json)
        }.onSuccess { n ->
            out("restored $n settings — restarting", LineKind.OK)
            viewModelScope.launch {
                delay(600)
                ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
                    ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)?.let { ctx.startActivity(it) }
                android.os.Process.killProcess(android.os.Process.myPid())
            }
        }.onFailure { out("restore failed: ${it.message}", LineKind.ERR) }
    }

    /** Contacts whose birthday falls within [days] days, soonest first, with days-until. */
    fun upcomingBirthdays(days: Int = 14): List<Pair<ContactSummary, Int>> {
        val today = java.time.LocalDate.now()
        return contacts.value.mapNotNull { c ->
            val md = c.birthday ?: return@mapNotNull null
            val month = md.substringBefore('-').toIntOrNull() ?: return@mapNotNull null
            val day = md.substringAfter('-').toIntOrNull() ?: return@mapNotNull null
            val next = runCatching { java.time.LocalDate.of(today.year, month, day) }.getOrNull() ?: return@mapNotNull null
            val occ = if (next.isBefore(today)) next.plusYears(1) else next
            val d = java.time.temporal.ChronoUnit.DAYS.between(today, occ).toInt()
            if (d <= days) c to d else null
        }.sortedBy { it.second }
    }

    // ───────────────────────── currency ─────────────────────────

    /** Converts with cached rates when fresh (12h); null if no usable cache. */
    fun convertCached(amount: Double, from: String, to: String): Double? {
        val (at, rates) = prefs.cachedRates() ?: return null
        if (System.currentTimeMillis() - at > 12 * 3_600_000L) return null
        val a = rates[from] ?: return null
        val b = rates[to] ?: return null
        return amount / a * b
    }

    fun convertCurrency(amount: Double, from: String, to: String) {
        convertCached(amount, from, to)?.let { out("= %,.2f %s".format(it, to), LineKind.ACCENT); return }
        out("fetching exchange rates…", LineKind.DIM)
        viewModelScope.launch {
            val rates = withContext(Dispatchers.IO) { runCatching { online.usdRates() }.getOrNull() }
            if (rates == null) { out("couldn't fetch rates (offline?)", LineKind.ERR); return@launch }
            prefs.saveRates(rates)
            val a = rates[from]; val b = rates[to]
            if (a == null || b == null) out("unknown currency: ${if (a == null) from else to}", LineKind.ERR)
            else out("= %,.2f %s".format(amount / a * b, to), LineKind.ACCENT)
        }
    }

    fun clearAllNotifications() {
        runCatching { NotifListener.instance?.cancelAllNotifications() }
        NotificationStore.items.update { list -> list.map { it.copy(removed = true) } }
    }

    fun clearApp(pkg: String) {
        NotificationStore.items.value.filter { it.pkg == pkg && !it.removed }.forEach { dismissNotif(it) }
    }

    /** Waiting notifications from apps that aren't muted. */
    fun unreadCounts(list: List<Notif>, muted: Set<String>): Map<String, Int> =
        list.filter { !it.removed && it.pkg !in muted }.groupingBy { it.pkg }.eachCount()

    fun openNotificationAccess() {
        if (!BuildConfig.NOTIFICATIONS) {
            out("lite edition: mail, chat and now playing need the full APK", LineKind.ERR)
            shellOpen.value = true
            return
        }
        out("if the switch is greyed out: App info → ⋮ → Allow restricted settings, then try again", LineKind.DIM)
        start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    fun requestDefaultHome() {
        uiRequests.tryEmit(UiRequest.RoleHome)
    }

    fun newEvent(begin: Long? = null) = start(calendarRepo.newEventIntent(begin), "no calendar app")

    // tasks
    fun addTask(text: String) {
        if (text.isBlank()) return
        val (title, due, starred) = dev.foldterm.launcher.shell.When.task(text)
        if (title.isBlank()) return
        tasks.update { it + TaskItem(System.nanoTime(), title, false, System.currentTimeMillis(), due, starred) }
        prefs.saveTasks(tasks.value)
    }
    fun starTask(id: Long) {
        tasks.update { list -> list.map { if (it.id == id) it.copy(starred = !it.starred) else it } }
        prefs.saveTasks(tasks.value)
    }

    /** Open tasks first (overdue, starred, due soonest, oldest), then done ones. */
    fun sortedTasks(list: List<TaskItem>): List<TaskItem> {
        val today = Fmt.startOfDay(System.currentTimeMillis())
        return list.sortedWith(
            compareBy<TaskItem>({ it.done }, { !(it.due != null && it.due < today) }, { !it.starred }, { it.due ?: Long.MAX_VALUE }, { it.created }),
        )
    }

    fun toggleTask(id: Long) {
        tasks.update { list -> list.map { if (it.id == id) it.copy(done = !it.done) else it } }
        prefs.saveTasks(tasks.value)
    }
    fun removeTask(id: Long) {
        tasks.update { list -> list.filterNot { it.id == id } }
        prefs.saveTasks(tasks.value)
    }

    // shell
    fun out(text: String, kind: LineKind = LineKind.OUT) {
        log.update { (it + Line(text, kind)).takeLast(400) }
    }
    fun clearLog() { log.value = emptyList() }
    fun execShell(cmd: String) {
        shellOpen.value = true
        shell.execute(cmd)
    }
    /** Spoken command from the mic button: shown in the prompt, then run. */
    fun onVoice(text: String) {
        if (text.isBlank()) return
        shellOpen.value = true
        input.value = text
        submit()
    }

    /** Highlighted suggestion (arrow keys); 0 = the top one. */
    val selIndex = MutableStateFlow(0)

    fun submit() {
        val line = input.value
        val pick = if (selIndex.value > 0 && line.isNotBlank()) shell.suggest(line).getOrNull(selIndex.value) else null
        selIndex.value = 0
        if (pick != null) {
            history.update { (it.filter { h -> h != line } + line).takeLast(100) }
            prefs.saveHistory(history.value)
            input.value = ""
            pick.run()
            return
        }
        input.value = ""
        if (line.isNotBlank() && !line.trim().startsWith("!")) {
            history.update { (it.filter { h -> h != line } + line).takeLast(100) }
            prefs.saveHistory(history.value)
        }
        shell.execute(line)
    }
}
