package dev.foldterm.launcher

import android.Manifest
import android.app.ActivityOptions
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

sealed interface UiRequest {
    data class Permissions(val perms: Array<String>) : UiRequest
    data object RoleHome : UiRequest
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

    // navigation / chrome
    val section = MutableStateFlow(Section.HOME)
    val drawerOpen = MutableStateFlow(false)
    val shellOpen = MutableStateFlow(false)
    val input = MutableStateFlow("")
    val fold = MutableStateFlow(FoldState())
    val now = MutableStateFlow(System.currentTimeMillis())
    val uiRequests = MutableSharedFlow<UiRequest>(extraBufferCapacity = 4)

    // data
    val apps = MutableStateFlow<List<AppEntry>>(emptyList())
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
    val history = MutableStateFlow<List<String>>(emptyList())

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
                delay(30_000)
            }
        }
        out("foldterm ${BuildConfig.VERSION_NAME} — type `help`", LineKind.DIM)
    }

    override fun onCleared() {
        appsRepo.unregister(appsCallback)
        stepStop?.invoke()
    }

    // ───────────────────────── refresh ─────────────────────────

    fun refresh(force: Boolean = false) {
        if (refreshJob?.isActive == true && !force) return
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
            apps.value = withContext(Dispatchers.IO) { appsRepo.load() }
        }
    }

    suspend fun loadEvents() {
        val start = calStart.value
        events.value = withContext(Dispatchers.IO) { calendarRepo.load(start, Fmt.addDays(start, 42)) }
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
    fun reloadNews() { viewModelScope.launch { loadNews() } }

    private fun startSteps() {
        val available = deviceRepo.stepSensorAvailable()
        val permitted = deviceRepo.stepsPermitted()
        steps.update { it.copy(available = available, permitted = permitted) }
        if (stepStop != null || !available || !permitted) return
        stepStop = deviceRepo.listenSteps { raw ->
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val base = prefs.stepBaseline()
            val baseline = if (base == null || base.first != day || raw < base.second) {
                prefs.setStepBaseline(day, raw); raw
            } else base.second
            steps.update { it.copy(steps = raw - baseline) }
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

    fun launch(app: AppEntry) {
        runCatching { appsRepo.launch(app) }.onFailure { out("failed to launch ${app.label}: ${it.message}", LineKind.ERR) }
        shellOpen.value = false
        drawerOpen.value = false
        input.value = ""
    }

    fun launchKey(key: String): Boolean {
        val app = apps.value.firstOrNull { it.key == key && !it.isWork } ?: apps.value.firstOrNull { it.key == key }
            ?: apps.value.firstOrNull { it.packageName == key.substringBefore('/') } ?: return false
        launch(app); return true
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

    fun icon(app: AppEntry): Bitmap? = appsRepo.icon(app)

    fun dial(number: String) = start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}")))
    fun sms(number: String) = start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")))
    fun email(address: String, subject: String? = null) = start(
        Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).apply { if (subject != null) putExtra(Intent.EXTRA_SUBJECT, subject) },
    )
    fun directions(place: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}")), "no maps app")
    fun openUrl(url: String) = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "no browser")
    fun webSearch(q: String) {
        val i = Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, q)
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: ActivityNotFoundException) {
            openUrl("https://duckduckgo.com/?q=${Uri.encode(q)}")
        }
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

    fun dismissNotif(n: Notif) {
        NotifListener.dismiss(n.key)
        NotificationStore.markRemoved(n.key)
    }

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
        tasks.update { it + TaskItem(System.nanoTime(), text.trim(), false, System.currentTimeMillis()) }
        prefs.saveTasks(tasks.value)
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
    fun submit() {
        val line = input.value
        input.value = ""
        if (line.isNotBlank()) history.update { (it.filter { h -> h != line } + line).takeLast(50) }
        shell.execute(line)
    }
}
