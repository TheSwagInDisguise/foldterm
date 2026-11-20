package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.Section
import dev.foldterm.launcher.data.NotifKind
import dev.foldterm.launcher.data.NowPlayingStore
import dev.foldterm.launcher.ui.Avatar
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.nameColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Cards available on the cover screen, in default order, with their Settings labels. */
val COVER_CARDS = listOf(
    "clock" to "big clock",
    "next" to "next event",
    "dial" to "quick dial",
    "inbox" to "inbox",
    "media" to "now playing",
    "weather" to "weather",
    "tasks" to "tasks",
)

/** Home for the folded phone: large type, big targets, one column, no nested scrolling. */
@Composable
fun CoverHome(vm: LauncherViewModel) {
    val cards by vm.prefs.coverCards.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cards.forEach { id ->
            when (id) {
                "clock" -> ClockCard(vm)
                "next" -> NextCard(vm)
                "dial" -> DialCard(vm)
                "inbox" -> InboxCard(vm)
                "media" -> MediaCard(vm)
                "weather" -> WeatherCard(vm)
                "tasks" -> TasksCard(vm)
            }
        }
        if (cards.isEmpty()) T("# no cover cards — choose some in Settings → cover screen", color = LocalTerm.current.dim)
    }
}

@Composable
private fun Card(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val t = LocalTerm.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(t.panel).border(1.dp, t.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
    ) { content() }
}

@Composable
private fun Label(text: String, extra: String? = null) {
    val t = LocalTerm.current
    Row {
        T(text, color = t.accent, size = 12.sp, weight = FontWeight.Bold)
        if (extra != null) T(" · $extra", color = t.dim, size = 12.sp)
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
internal fun ClockCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val now by vm.now.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            T(Fmt.clock(now), size = 64.sp, weight = FontWeight.Bold)
            if (!Fmt.is24) {
                Spacer(Modifier.width(6.dp))
                T(SimpleDateFormat("a", Locale.getDefault()).format(Date(now)).lowercase(), color = t.dim, size = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
        T("${Fmt.longDate(now)}${vitals?.let { " · ${it.battery}%${if (it.charging) "⚡" else ""}" } ?: ""}", color = t.dim, size = 14.sp)
    }
}

@Composable
internal fun NextCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val events by vm.events.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val e = events.firstOrNull { !it.allDay && it.end > now }
    Card(onClick = { if (e != null) vm.selectedEvent.value = e.instanceKey; vm.go(Section.CALENDAR) }) {
        if (e == null) { Label("next"); T("Nothing scheduled", size = 18.sp, weight = FontWeight.Bold); return@Card }
        Label("next", if (e.begin <= now) "now" else Fmt.until(e.begin, now))
        T(e.title, size = 20.sp, weight = FontWeight.Bold, maxLines = 2)
        T("${Fmt.dayLabel(e.begin, now)} ${Fmt.time(e.begin)} → ${Fmt.time(e.end)}", color = t.dim)
        if (e.location.isNotBlank()) T(e.location, color = t.dim, maxLines = 1)
        val url = e.url
        if (url != null || (e.location.isNotBlank() && !e.isOnline)) {
            Spacer(Modifier.height(10.dp))
            if (url != null) TButton("join", primary = true) { vm.openUrl(url) }
            else TButton("directions", primary = true) { vm.directions(e.location) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DialCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val ice by vm.prefs.ice.collectAsStateWithLifecycle()
    val people = (contacts.filter { it.id in ice } + contacts.filter { it.starred }).distinctBy { it.id }.filter { it.phone != null }.take(8)
    Card {
        Label("quick dial", "tap call · hold text")
        if (people.isEmpty()) { T("Star contacts or mark ICE contacts in People to fill this.", color = t.dim, size = 13.sp); return@Card }
        people.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onClick = { c.phone?.let { vm.dial(it) } }, onLongClick = { c.phone?.let { vm.sms(it) } })
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Avatar(c.name, 52.dp, if (c.id in ice) t.err else nameColor(c.name))
                        Spacer(Modifier.height(4.dp))
                        T(c.name.substringBefore(' '), size = 12.sp, maxLines = 1)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun InboxCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val notifs by vm.notifications.collectAsStateWithLifecycle()
    val access by vm.notifAccess.collectAsStateWithLifecycle()
    val muted by vm.prefs.mutedApps.collectAsStateWithLifecycle()
    val waiting = notifs.filter { !it.removed && it.kind != NotifKind.OTHER && it.pkg !in muted }
    Card(onClick = { vm.go(Section.NOTIFS) }) {
        Label("inbox", if (access) "${waiting.size}" else null)
        if (!access) { T("Turn on notification access in Settings.", color = t.dim, size = 13.sp); return@Card }
        if (waiting.isEmpty()) { T("Inbox zero.", color = t.dim); return@Card }
        waiting.take(3).forEach { n ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.openNotif(n) }.padding(vertical = 6.dp)) {
                T(if (n.kind == NotifKind.MAIL) "@" else "»", color = if (n.kind == NotifKind.MAIL) t.accent2 else t.accent3, modifier = Modifier.width(22.dp))
                Column(Modifier.weight(1f)) {
                    T(n.title.ifBlank { n.app }, weight = FontWeight.SemiBold, maxLines = 1)
                    T(n.text, color = t.dim, size = 12.sp, maxLines = 1)
                }
                T(Fmt.ago(n.time), color = t.dim, size = 11.sp)
            }
        }
    }
}

@Composable
internal fun MediaCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val np by NowPlayingStore.state.collectAsStateWithLifecycle()
    val cur = np ?: return
    Card(onClick = { vm.go(Section.MEDIA) }) {
        Label("now playing", vm.appLabelForPackage(cur.pkg))
        T(cur.title, size = 16.sp, weight = FontWeight.Bold, maxLines = 1)
        if (cur.artist.isNotBlank()) T(cur.artist, color = t.dim, maxLines = 1)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            T("⏮", size = 24.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { NowPlayingStore.previous() }.padding(10.dp))
            Box(
                Modifier.width(64.dp).aspectRatio(1f).clip(RoundedCornerShape(50)).background(t.button).clickable { NowPlayingStore.toggle() },
                contentAlignment = Alignment.Center,
            ) { T(if (cur.isPlaying) "▮▮" else "▶", color = t.onButton, size = 22.sp, weight = FontWeight.Bold) }
            T("⏭", size = 24.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { NowPlayingStore.next() }.padding(10.dp))
        }
    }
}

@Composable
internal fun WeatherCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val w by vm.weather.collectAsStateWithLifecycle()
    val cur = w ?: return
    Card(onClick = { vm.go(Section.WEATHER) }) {
        Label("weather", cur.place)
        Row(verticalAlignment = Alignment.CenterVertically) {
            T(cur.glyph, color = t.warn, size = 32.sp)
            Spacer(Modifier.width(12.dp))
            Column {
                T("${cur.temp.toInt()}° ${cur.description}", size = 18.sp, weight = FontWeight.Bold)
                T("H${cur.high.toInt()}° L${cur.low.toInt()}° · ${if (cur.rainInHours == null) "no rain soon" else "rain in ~${cur.rainInHours}h"}", color = t.dim, size = 13.sp)
            }
        }
        severeLine(cur)?.let { T("⚠ $it", color = t.err, size = 13.sp) }
    }
}

@Composable
internal fun TasksCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val open = tasks.filter { !it.done }
    Card {
        Label("tasks", "${open.size} to do")
        if (open.isEmpty()) T("Nothing on the list.", color = t.dim)
        open.take(4).forEach { task ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.toggleTask(task.id) }.padding(vertical = 6.dp)) {
                T("[ ]", color = t.accent, weight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                T(task.text, maxLines = 1)
            }
        }
    }
}
