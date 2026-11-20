package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.Section
import dev.foldterm.launcher.data.CalEvent
import dev.foldterm.launcher.data.CalendarRepository
import dev.foldterm.launcher.data.NotifKind
import dev.foldterm.launcher.ui.ColorBar
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.KV
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.Tabs
import dev.foldterm.launcher.ui.TwoPane
import java.util.Calendar

// ───────────────────────── home ─────────────────────────

@Composable
fun HomeScreen(vm: LauncherViewModel) {
    val coverMode by vm.prefs.coverMode.collectAsStateWithLifecycle()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 640.dp && coverMode) {
            CoverHome(vm)
        } else if (maxWidth >= 640.dp) {
            ConfigurableHome(vm)
        } else {
            // Narrow (cover screen): no outer scroll. Each panel scrolls itself, because nesting
            // scrollable containers crashes Compose.
            Column(Modifier.fillMaxSize()) {
                HappeningNow(vm)
                Spacer(Modifier.height(10.dp))
                SummaryPanel(vm, Modifier.fillMaxWidth().weight(0.45f))
                Spacer(Modifier.height(10.dp))
                AgendaPanel(vm, Modifier.fillMaxWidth().weight(0.55f))
            }
        }
    }
}

/** Cards the unfolded Home can show, with their Settings labels. Scrolling cards share leftover height. */
val HOME_CARDS = listOf(
    "happening" to "happening now",
    "agenda" to "agenda",
    "summary" to "summary",
    "widgets" to "widgets",
    "dial" to "quick dial",
    "inbox" to "inbox",
    "media" to "now playing",
    "weather" to "weather",
    "tasks" to "tasks",
    "notes" to "latest note",
    "clock" to "big clock",
)
private val STRETCHY = setOf("agenda", "summary", "widgets", "tasks", "notes")

@Composable
private fun ConfigurableHome(vm: LauncherViewModel) {
    val layout by vm.prefs.homeLayout.collectAsStateWithLifecycle()
    val left = layout.filter { it.endsWith(":L") }.map { it.substringBefore(':') }
    val right = layout.filter { it.endsWith(":R") }.map { it.substringBefore(':') }
    when {
        left.isEmpty() && right.isEmpty() -> Panel(Modifier.fillMaxSize()) { Empty("Home is empty — add cards in Settings → home layout") }
        left.isEmpty() || right.isEmpty() -> HomeColumn(vm, left + right, Modifier.fillMaxSize())
        else -> TwoPane(Modifier.fillMaxSize(), left = { m -> HomeColumn(vm, left, m) }, right = { m -> HomeColumn(vm, right, m) })
    }
}

@Composable
private fun HomeColumn(vm: LauncherViewModel, cards: List<String>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        cards.forEach { id ->
            val stretch = if (id in STRETCHY) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth()
            when (id) {
                "happening" -> HappeningNow(vm)
                "agenda" -> AgendaPanel(vm, stretch)
                "summary" -> SummaryPanel(vm, stretch)
                "widgets" -> HomeWidgets(vm, stretch)
                "tasks" -> TasksPanel(vm, stretch)
                "notes" -> LatestNote(vm, stretch)
                "dial" -> DialCard(vm)
                "inbox" -> InboxCard(vm)
                "media" -> MediaCard(vm)
                "weather" -> WeatherCard(vm)
                "clock" -> ClockCard(vm)
            }
        }
        if (cards.none { it in STRETCHY }) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun HomeWidgets(vm: LauncherViewModel, modifier: Modifier) {
    val slots by vm.prefs.widgets.collectAsStateWithLifecycle()
    Panel(modifier, title = "widgets", trailing = { T("edit ↗", color = LocalTerm.current.dim, size = 12.sp, modifier = Modifier.clickable { vm.go(Section.WIDGETS) }) }) {
        if (slots.isEmpty()) { Empty("no widgets yet", "+ add widget") { vm.go(Section.WIDGETS); vm.widgetPickerOpen.value = true }; return@Panel }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            slots.forEach { WidgetCard(vm, it, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun LatestNote(vm: LauncherViewModel, modifier: Modifier) {
    val notes by vm.prefs.notes.collectAsStateWithLifecycle()
    val n = notes.firstOrNull()
    Panel(modifier, title = "note", trailing = { T("open ↗", color = LocalTerm.current.dim, size = 12.sp, modifier = Modifier.clickable { if (n != null) vm.openNote(n.id) else vm.go(Section.NOTES) }) }) {
        if (n == null) { Empty("no notes yet", "+ new note") { vm.newNote(); vm.go(Section.NOTES) }; return@Panel }
        T(n.title, weight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        T(n.text.lines().drop(1).joinToString("\n").trim(), color = LocalTerm.current.fg.copy(alpha = 0.85f), modifier = Modifier.verticalScroll(rememberScrollState()))
    }
}

@Composable
private fun HappeningNow(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val events by vm.events.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    val timed = events.filter { !it.allDay && it.end > now }
    val current = timed.firstOrNull { it.begin <= now }
    val next = current ?: timed.firstOrNull { it.begin < Fmt.addDays(Fmt.startOfDay(now), 1) }
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(t.accent.copy(alpha = 0.16f), t.accent3.copy(alpha = 0.22f))))
            .border(1.dp, t.border, shape).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            when {
                perms["calendar"] != true -> {
                    T("CALENDAR OFFLINE", color = t.accent, size = 13.sp, weight = FontWeight.Bold)
                    T("$ grant calendar", size = 20.sp, weight = FontWeight.Bold)
                }
                next == null -> {
                    T("NOTHING SCHEDULED", color = t.accent, size = 13.sp, weight = FontWeight.Bold)
                    T("Rest of today is free", size = 22.sp, weight = FontWeight.Bold)
                }
                else -> {
                    T(if (current != null) "HAPPENING NOW" else "UP NEXT · ${Fmt.until(next.begin, now)}".uppercase(), color = t.accent, size = 13.sp, weight = FontWeight.Bold)
                    T(next.title, size = 24.sp, weight = FontWeight.Bold, maxLines = 2)
                    T("${Fmt.time(next.begin)} → ${Fmt.time(next.end)}${if (next.location.isNotBlank()) " · ${next.location}" else ""}", color = t.dim, size = 14.sp, maxLines = 1)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                perms["calendar"] != true -> TButton("grant", primary = true) { vm.requestPermission("calendar") }
                next != null -> {
                    TButton("open event", primary = true) { vm.start(vm.calendarRepo.viewIntent(next)) }
                    val url = next.url
                    if (url != null) TButton("join") { vm.openUrl(url) }
                    else if (next.location.isNotBlank() && !next.isOnline) TButton("directions") { vm.directions(next.location) }
                }
                else -> TButton("+ event", primary = true) { vm.newEvent() }
            }
        }
    }
}

@Composable
private fun AgendaPanel(vm: LauncherViewModel, modifier: Modifier) {
    val events by vm.events.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    val upcoming = remember(events, now) { events.filter { it.end > now }.take(40) }
    Panel(modifier, title = "agenda", trailing = { T("cal ↗", color = LocalTerm.current.dim, size = 12.sp, modifier = Modifier.clickable { vm.go(Section.CALENDAR) }) }) {
        if (perms["calendar"] != true) {
            Empty("calendar permission denied", "grant") { vm.requestPermission("calendar") }
            return@Panel
        }
        if (upcoming.isEmpty()) { Empty("no upcoming events", "+ new event") { vm.newEvent() }; return@Panel }
        LazyColumn(Modifier.fillMaxSize()) {
            agendaItems(upcoming, now, compact = true, selected = null) { e ->
                vm.selectedEvent.value = e.instanceKey
                vm.go(Section.CALENDAR)
            }
        }
    }
}

fun LazyListScope.agendaItems(
    events: List<CalEvent>,
    now: Long,
    compact: Boolean,
    selected: String?,
    onClick: (CalEvent) -> Unit,
) {
    val groups = events.groupBy { CalendarRepository.dayKey(it.begin) }
    groups.forEach { (day, list) ->
        item(key = "h$day") {
            val t = LocalTerm.current
            val label = Fmt.dayLabel(list.first().begin, now)
            T(
                if (label == "Today") "Today · ${Fmt.shortDate(now)}" else label,
                color = if (label == "Today") t.accent2 else t.dim,
                weight = FontWeight.Bold, size = 14.sp,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
            )
        }
        list.forEach { e ->
            item(key = e.instanceKey) { AgendaRow(e, now, compact, e.instanceKey == selected) { onClick(e) } }
        }
    }
}

@Composable
private fun AgendaRow(e: CalEvent, now: Long, compact: Boolean, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTerm.current
    val past = e.end < now
    val live = e.begin <= now && e.end > now
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) t.selection else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(if (compact) 92.dp else 100.dp)) {
            T(if (e.allDay) "all-day" else Fmt.time(e.begin), color = if (past) t.dim else t.fg, size = 14.sp, weight = FontWeight.SemiBold)
            if (!compact && !e.allDay) T(Fmt.time(e.end), color = t.dim, size = 13.sp)
        }
        ColorBar(Color(e.color or 0xFF000000.toInt()), if (compact) 24.dp else 40.dp)
        Spacer(Modifier.width(12.dp))
        if (compact) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                T(e.title, color = if (past) t.dim else t.fg, weight = FontWeight.SemiBold, size = 15.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(10.dp))
                val dur = if (e.allDay) "" else Fmt.duration(e.end - e.begin)
                T(listOf(e.location, dur).filter { it.isNotBlank() }.joinToString(" · "), color = t.dim, size = 13.sp, maxLines = 1)
            }
        } else {
            Column(Modifier.weight(1f)) {
                T(e.title, color = if (past) t.dim else t.fg, weight = FontWeight.SemiBold, size = 15.sp, maxLines = 1)
                if (e.location.isNotBlank()) T(e.location, color = t.dim, size = 13.sp, maxLines = 1)
            }
        }
        if (live) { Spacer(Modifier.width(8.dp)); T("● live", color = t.err, size = 12.sp) }
    }
}

@Composable
private fun SummaryPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val steps by vm.steps.collectAsStateWithLifecycle()
    val goal by vm.prefs.stepGoal.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val wStatus by vm.weatherStatus.collectAsStateWithLifecycle()
    val news by vm.news.collectAsStateWithLifecycle()
    val notifs by vm.notifications.collectAsStateWithLifecycle()
    val access by vm.notifAccess.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()

    Panel(
        modifier, title = "summary",
        trailing = { T("generated on-device · ${Fmt.time(now)}", color = t.dim, size = 12.sp) },
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            SummaryRow("♥", "Health", t.err, onClick = { vm.go(Section.HEALTH) }) {
                val s = steps.steps
                when {
                    !steps.available -> "No step sensor on this device."
                    !steps.permitted -> "Allow physical-activity access to count steps."
                    s == null -> "Waiting for the step counter…"
                    else -> "%,d of %,d steps so far. %s".format(s, goal, if (s >= goal) "Goal reached." else "${Fmt.bar(s.toFloat() / goal, 12)}")
                }
            }
            Divider()
            SummaryRow("☁", "Weather", t.warn, onClick = { vm.go(Section.WEATHER) }) {
                val w = weather
                if (w == null) wStatus.ifBlank { "Fetching forecast…" }
                else buildString {
                    severeLine(w)?.let { append("⚠ $it. ") }
                    append(if (w.rainInHours == null) "No rain in the next 12 hours. " else "Rain likely in about ${w.rainInHours}h. ")
                    append("${w.description}, ${w.temp.toInt()}°${w.unit} now; high of ${w.high.toInt()}° today")
                    w.place?.let { append(" in $it") }
                    append('.')
                }
            }
            Divider()
            val muted by vm.prefs.mutedApps.collectAsStateWithLifecycle()
            val mail = notifs.count { !it.removed && it.kind == NotifKind.MAIL && it.pkg !in muted }
            val chat = notifs.count { !it.removed && it.kind == NotifKind.CHAT && it.pkg !in muted }
            SummaryRow("@", "Inbox", t.accent2, onClick = { vm.go(if (chat > mail) Section.CHAT else Section.MAIL) }) {
                if (!access) "Turn on notification access to see mail & chats here."
                else if (mail + chat == 0) "Inbox zero. Nothing waiting."
                else {
                    val latest = notifs.firstOrNull { !it.removed && it.kind != NotifKind.OTHER && it.pkg !in muted }
                    "$mail mail, $chat chat. ${latest?.let { "Latest: ${it.title} — ${it.text.take(80)}" } ?: ""}"
                }
            }
            Divider()
            SummaryRow("≡", "News", t.accent3, onClick = { vm.go(Section.NEWS) }) {
                val top = news.firstOrNull()
                if (top == null) "No headlines yet." else "Latest: ${top.title} (${top.source}). ${news.size - 1} more stories today."
            }
            Divider()
            val open = tasks.filter { !it.done }
            SummaryRow("✓", "Tasks", t.ok, onClick = { vm.go(Section.CALENDAR) }) {
                if (open.isEmpty()) "Nothing on the list." else "${open.size} to do. Next: ${open.first().text}."
            }
            val bdays = remember(contacts) { vm.upcomingBirthdays(14) }
            if (bdays.isNotEmpty()) {
                Divider()
                SummaryRow("✦", "Birthdays", t.accent3, onClick = { vm.go(Section.PEOPLE); vm.selectContact(bdays.first().first) }) {
                    bdays.take(3).joinToString(", ") { (c, d) ->
                        "${c.name} ${when (d) { 0 -> "today 🎂"; 1 -> "tomorrow"; else -> "in $d days" }}"
                    } + if (bdays.size > 3) " and ${bdays.size - 3} more." else "."
                }
            }
            val alarm = remember(now) { vm.nextAlarm() }
            if (alarm != null && alarm - now < 24 * 3_600_000L) {
                Divider()
                SummaryRow("⏰", "Next alarm", t.warn, onClick = { vm.start(android.content.Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }) {
                    "${Fmt.dayLabel(alarm, now)} at ${Fmt.time(alarm)} (${Fmt.until(alarm, now)})."
                }
            }
            usage?.let { u ->
                Divider()
                SummaryRow("⏱", "Screen time", t.accent2, onClick = { vm.go(Section.USAGE) }) {
                    val top = u.apps.firstOrNull()
                    "${Fmt.duration(u.totalMs).ifBlank { "Under a minute" }} so far, ${u.unlocks} unlocks.${top?.let { " Most used: ${vm.appLabelForPackage(it.pkg)} (${Fmt.duration(it.ms)})." } ?: ""}"
                }
            }
            vitals?.let { v ->
                Divider()
                SummaryRow("⚡", "Device", t.accent, onClick = { vm.go(Section.HEALTH) }) {
                    val full = if (v.charging) vm.chargeTimeLeft() else null
                    "Battery ${v.battery}%${if (v.charging) " and charging${full?.let { ", full in ${Fmt.duration(it)}" } ?: ""}" else ""}. ${Fmt.gb(v.storageFreeGb)} storage free."
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(glyph: String, title: String, color: Color, onClick: () -> Unit, body: () -> String) {
    val t = LocalTerm.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) { T(glyph, color = color, size = 20.sp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            T(title, weight = FontWeight.Bold, size = 16.sp)
            Spacer(Modifier.height(4.dp))
            T(body(), size = 15.sp, color = t.fg.copy(alpha = 0.85f))
        }
    }
}

// ───────────────────────── calendar ─────────────────────────

@Composable
fun CalendarScreen(vm: LauncherViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            TwoPane(
                Modifier.fillMaxSize(), leftFraction = 0.62f,
                left = { m -> CalendarListPanel(vm, m) },
                right = { m ->
                    Column(m) {
                        EventDetailPanel(vm, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(14.dp))
                        TasksPanel(vm, Modifier.fillMaxWidth().weight(1f))
                    }
                },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                CalendarListPanel(vm, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(10.dp))
                EventDetailPanel(vm, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CalendarListPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val events by vm.events.collectAsStateWithLifecycle()
    val start by vm.calStart.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val selected by vm.selectedEvent.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Tabs(listOf("agenda", "day", "week", "month"), mode, { mode = it })
            Spacer(Modifier.width(16.dp))
            T(
                when (mode) {
                    0 -> "${Fmt.monthDay(start)} → ${Fmt.monthDay(Fmt.addDays(start, 13))}"
                    1 -> "${Fmt.dayLabel(start, now)} · ${Fmt.shortDate(start)}"
                    2 -> "${Fmt.monthDay(start)} → ${Fmt.monthDay(Fmt.addDays(start, 6))}"
                    else -> Fmt.monthYear(start)
                },
                size = 18.sp, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f),
            )
            T("‹", size = 22.sp, modifier = Modifier.clip(CircleShape).clickable { vm.shiftCalendar(when (mode) { 0 -> -14; 1 -> -1; 2 -> -7; else -> -30 }) }.padding(horizontal = 12.dp, vertical = 4.dp))
            TButton("today") { vm.shiftCalendar(null) }
            T("›", size = 22.sp, modifier = Modifier.clip(CircleShape).clickable { vm.shiftCalendar(when (mode) { 0 -> 14; 1 -> 1; 2 -> 7; else -> 30 }) }.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        if (perms["calendar"] != true) {
            Empty("calendar permission denied", "grant") { vm.requestPermission("calendar") }
            return@Panel
        }
        if (mode == 0) {
            val windowed = remember(events, start) { events.filter { it.begin < Fmt.addDays(start, 14) } }
            if (windowed.isEmpty()) Empty("no events in this range", "+ new event") { vm.newEvent(start + 9 * 3_600_000) }
            LazyColumn(Modifier.fillMaxSize()) {
                agendaItems(windowed, now, compact = false, selected = selected) { vm.selectedEvent.value = it.instanceKey }
            }
        } else if (mode == 1) {
            DayTimeline(
                events = events, dayStart = start, now = now, selected = selected,
                onSelect = { vm.selectedEvent.value = it.instanceKey },
                onCreate = { vm.newEvent(it) },
            )
        } else if (mode == 2) {
            WeekTimeline(
                events = events, start = start, now = now, selected = selected,
                onSelect = { vm.selectedEvent.value = it.instanceKey },
                onCreate = { vm.newEvent(it) },
            )
        } else {
            MonthGrid(events, start, now) { day ->
                vm.setCalendarStart(day)
                mode = 1
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthGrid(events: List<CalEvent>, anchor: Long, now: Long, onDay: (Long) -> Unit) {
    val t = LocalTerm.current
    val cal = Calendar.getInstance().apply { timeInMillis = anchor; set(Calendar.DAY_OF_MONTH, 1) }
    val month = cal.get(Calendar.MONTH)
    val firstDow = (cal.get(Calendar.DAY_OF_WEEK) - cal.firstDayOfWeek + 7) % 7
    val first = Fmt.startOfDay(cal.timeInMillis)
    val counts = events.groupingBy { CalendarRepository.dayKey(it.begin) }.eachCount()
    val today = CalendarRepository.dayKey(now)
    val dayNames = java.text.DateFormatSymbols.getInstance().shortWeekdays
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val dow = (cal.firstDayOfWeek - 1 + i) % 7 + 1
                T(dayNames[dow].take(2).lowercase(), color = t.dim, size = 12.sp, modifier = Modifier.weight(1f).padding(4.dp))
            }
        }
        for (week in 0 until 6) {
            Row(Modifier.fillMaxWidth()) {
                for (d in 0 until 7) {
                    val day = Fmt.addDays(first, week * 7 + d - firstDow)
                    val c = Calendar.getInstance().apply { timeInMillis = day }
                    val inMonth = c.get(Calendar.MONTH) == month
                    val key = CalendarRepository.dayKey(day)
                    val n = counts[key] ?: 0
                    Column(
                        Modifier.weight(1f).aspectRatio(1.3f).padding(2.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (key == today) t.selection else Color.Transparent)
                            .clickable { onDay(day) }.padding(6.dp),
                    ) {
                        T(c.get(Calendar.DAY_OF_MONTH).toString(), color = if (inMonth) t.fg else t.dim.copy(alpha = 0.5f), weight = if (key == today) FontWeight.Bold else FontWeight.Normal)
                        if (n > 0) T("•".repeat(n.coerceAtMost(4)), color = t.accent, size = 12.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventDetailPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val events by vm.events.collectAsStateWithLifecycle()
    val selected by vm.selectedEvent.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val e = events.firstOrNull { it.instanceKey == selected } ?: events.firstOrNull { it.end > now }
    Panel(modifier) {
        if (e == null) { Empty("select an event"); return@Panel }
        Box(Modifier.width(56.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(e.color or 0xFF000000.toInt())))
        Spacer(Modifier.height(14.dp))
        T(e.title, size = 24.sp, weight = FontWeight.Bold, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        T(Fmt.longDate(e.begin), weight = FontWeight.Bold)
        T(if (e.allDay) "All day" else "${Fmt.time(e.begin)} to ${Fmt.time(e.end)}  (${Fmt.duration(e.end - e.begin)})", color = t.fg)
        Spacer(Modifier.height(8.dp))
        if (e.location.isNotBlank()) KV("where", e.location)
        if (e.calendarName.isNotBlank()) KV("calendar", e.calendarName)
        if (e.description.isNotBlank()) KV("notes", e.description.take(400))
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TButton("edit", primary = true) { vm.start(vm.calendarRepo.editIntent(e)) }
            TButton("open") { vm.start(vm.calendarRepo.viewIntent(e)) }
            val url = e.url
            if (url != null) TButton("join") { vm.openUrl(url) }
            if (e.location.isNotBlank() && !e.isOnline) TButton("directions") { vm.directions(e.location) }
        }
    }
}

@Composable
fun TasksPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val raw by vm.tasks.collectAsStateWithLifecycle()
    val tasks = remember(raw) { vm.sortedTasks(raw) }
    val today = Fmt.startOfDay(System.currentTimeMillis())
    var draft by remember { mutableStateOf("") }
    Panel(modifier, title = "tasks", trailing = {
        val overdue = tasks.count { !it.done && it.due != null && it.due < today }
        T("${tasks.count { !it.done }} to do${if (overdue > 0) " · $overdue overdue" else ""}", color = if (overdue > 0) t.err else t.dim, size = 13.sp)
    }) {
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(tasks.size, key = { tasks[it].id }) { i ->
                val task = tasks[i]
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.toggleTask(task.id) }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (task.done) "[x]" else "[ ]", color = if (task.done) t.ok else t.accent, weight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    T(task.text, color = if (task.done) t.dim else t.fg, modifier = Modifier.weight(1f))
                    val due = task.due
                    if (due != null && !task.done) {
                        val late = due < today
                        T(if (late) "overdue" else Fmt.dayLabel(due).let { if (it.length > 9) Fmt.monthDay(due) else it }.lowercase(),
                            color = if (late) t.err else if (due == today) t.warn else t.dim, size = 12.sp)
                    }
                    T(if (task.starred) "★" else "☆", color = if (task.starred) t.warn else t.dim,
                        modifier = Modifier.clip(CircleShape).clickable { vm.starTask(task.id) }.padding(8.dp))
                    T("✕", color = t.dim, modifier = Modifier.clip(CircleShape).clickable { vm.removeTask(task.id) }.padding(8.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(draft, { draft = it }, "add a task · e.g. pay rent fri !", Modifier.weight(1f), onDone = { vm.addTask(draft); draft = "" })
            Spacer(Modifier.width(8.dp))
            TButton("add") { vm.addTask(draft); draft = "" }
        }
    }
}
