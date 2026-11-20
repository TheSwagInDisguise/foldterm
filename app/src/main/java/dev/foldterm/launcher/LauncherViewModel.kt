package dev.foldterm.launcher

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.media.AudioManager
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

sealed interface UiRequest {
    data class Permissions(val perms: Array<String>) : UiRequest
    data object RoleHome : UiRequest
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
            allApps.value = withContext(Dispatchers.IO) { appsRepo.load() }
            applyHidden()
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

    private fun applyHidden() {
        val hidden = prefs.hidden.value
        apps.value = allApps.value.filter { it.key !in hidden }
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

    /** Opens [a] then [b] beside it. Works where the device supports launching adjacent windows. */
    fun split(a: AppEntry, b: AppEntry) {
        launch(a)
        viewModelScope.launch {
            delay(700)
            val i = ctx.packageManager.getLaunchIntentForPackage(b.packageName)
            if (i == null) { out("can't open ${b.label} in split view", LineKind.ERR); return@launch }
            i.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            runCatching { ctx.startActivity(i) }.onFailure { out("split: ${it.message}", LineKind.ERR) }
        }
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
