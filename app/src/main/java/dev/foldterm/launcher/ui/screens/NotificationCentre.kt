package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.BuildConfig
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.data.Notif
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.Tabs
import dev.foldterm.launcher.ui.TwoPane
import java.util.Calendar

@Composable
fun NotificationCentre(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val access by vm.notifAccess.collectAsStateWithLifecycle()
    if (!BuildConfig.NOTIFICATIONS || !access) {
        Panel(Modifier.fillMaxSize(), title = "alerts") {
            T("$ tail -f /dev/notifications", color = t.accent)
            Spacer(Modifier.height(6.dp))
            T(if (!BuildConfig.NOTIFICATIONS) "not available in the lite edition" else "permission denied", color = t.err)
            if (BuildConfig.NOTIFICATIONS) {
                Spacer(Modifier.height(14.dp))
                TButton("grant access", primary = true) { vm.openNotificationAccess() }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            TwoPane(Modifier.fillMaxSize(), leftFraction = 0.58f, left = { m -> GroupedList(vm, m) }, right = { m -> DetailAndRules(vm, m) })
        } else {
            Column(Modifier.fillMaxSize()) {
                GroupedList(vm, Modifier.fillMaxWidth().weight(1.4f))
                Spacer(Modifier.height(10.dp))
                DetailAndRules(vm, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun GroupedList(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val all by vm.notifications.collectAsStateWithLifecycle()
    val pinned by vm.prefs.pinnedApps.collectAsStateWithLifecycle()
    val muted by vm.prefs.mutedApps.collectAsStateWithLifecycle()
    val selected by vm.selectedNotif.collectAsStateWithLifecycle()
    var showDismissed by rememberSaveable { mutableStateOf(false) }
    val live = all.filter { showDismissed || !it.removed }
    // pinned apps first, then the app with the newest notification
    val groups = remember(live, pinned) {
        live.groupBy { it.pkg }.entries.sortedWith(
            compareByDescending<Map.Entry<String, List<Notif>>> { it.key in pinned }.thenByDescending { e -> e.value.maxOf { it.time } },
        )
    }
    Panel(
        modifier, title = "alerts (${all.count { !it.removed }})",
        trailing = {
            T(if (showDismissed) "history ▾" else "active ▾", color = t.dim, size = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showDismissed = !showDismissed }.padding(6.dp))
            Spacer(Modifier.width(6.dp))
            TButton("clear all") { vm.clearAllNotifications() }
        },
    ) {
        if (groups.isEmpty()) { Empty("all clear — nothing waiting"); return@Panel }
        LazyColumn(Modifier.fillMaxSize()) {
            groups.forEach { (pkg, items) ->
                item(key = "g_$pkg") {
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (pkg in pinned) T("📌 ", size = 12.sp)
                        T(items.first().app, color = if (pkg in muted) t.dim else t.accent, weight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        if (pkg in muted) T("muted  ", color = t.dim, size = 11.sp)
                        T("${items.count { !it.removed }}", color = t.dim, size = 12.sp)
                        T("  clear", color = t.dim, size = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.clearApp(pkg) }.padding(4.dp))
                    }
                }
                items(items.size, key = { "n_${items[it].key}_${items[it].time}" }) { i ->
                    val n = items[i]
                    val sel = n.key == selected
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (sel) t.selection else Color.Transparent)
                            .clickable { vm.selectedNotif.value = n.key }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(Fmt.ago(n.time).padStart(4), color = t.dim, size = 12.sp, modifier = Modifier.width(44.dp))
                        Column(Modifier.weight(1f)) {
                            T(n.title.ifBlank { n.app }, color = if (n.removed) t.dim else t.fg, weight = FontWeight.SemiBold, size = 13.sp, maxLines = 1)
                            if (n.text.isNotBlank()) T(n.text, color = t.dim, size = 12.sp, maxLines = 2)
                        }
                        if (n.replyAction != null && !n.removed) T("↩", color = t.accent, modifier = Modifier.padding(horizontal = 6.dp))
                        if (!n.removed) T("✕", color = t.dim, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.dismissNotif(n) }.padding(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailAndRules(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val all by vm.notifications.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedNotif.collectAsStateWithLifecycle()
    val pinned by vm.prefs.pinnedApps.collectAsStateWithLifecycle()
    val muted by vm.prefs.mutedApps.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val sel = all.firstOrNull { it.key == selectedKey } ?: all.firstOrNull { !it.removed }
    Panel(modifier) {
        Tabs(listOf("detail", "rules", "activity"), tab, { tab = it })
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            when (tab) {
                0 -> if (sel == null) Empty("nothing selected") else {
                    var reply by remember(sel.key) { mutableStateOf("") }
                    T(sel.app, color = t.accent3, size = 12.sp)
                    T(sel.title.ifBlank { sel.app }, size = 18.sp, weight = FontWeight.Bold)
                    T("${Fmt.dayLabel(sel.time)} ${Fmt.time(sel.time)}", color = t.dim, size = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.panelAlt)
                            .border(1.dp, t.border, RoundedCornerShape(14.dp)).padding(14.dp),
                    ) { T(sel.body.ifBlank { sel.text }) }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TButton("open", primary = true) { vm.openNotif(sel) }
                        if (!sel.removed) TButton("dismiss") { vm.dismissNotif(sel) }
                        TButton("copy") { vm.copy(sel.body.ifBlank { sel.text }) }
                    }
                    if (sel.replyAction != null && !sel.removed) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Field(reply, { reply = it }, "reply…", Modifier.weight(1f), onDone = { if (vm.replyNotif(sel, reply)) reply = "" })
                            Spacer(Modifier.width(8.dp))
                            TButton("send", primary = true, enabled = reply.isNotBlank()) { if (vm.replyNotif(sel, reply)) reply = "" }
                        }
                    }
                }
                1 -> {
                    T("pinned apps are listed first; muted apps stay out of badges and the Home summary", color = t.dim, size = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    val appsSeen = all.distinctBy { it.pkg }.sortedBy { it.app.lowercase() }
                    if (appsSeen.isEmpty()) Empty("no apps have posted notifications yet")
                    appsSeen.forEach { n ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(n.app, modifier = Modifier.weight(1f), maxLines = 1)
                            RuleChip("pin", n.pkg in pinned) { vm.prefs.togglePinned(n.pkg) }
                            Spacer(Modifier.width(6.dp))
                            RuleChip("mute", n.pkg in muted) { vm.prefs.toggleMuted(n.pkg) }
                        }
                    }
                }
                else -> {
                    val cal = Calendar.getInstance()
                    val today = Fmt.startOfDay(System.currentTimeMillis())
                    val perHour = IntArray(24)
                    all.filter { it.time >= today }.forEach {
                        cal.timeInMillis = it.time
                        perHour[cal.get(Calendar.HOUR_OF_DAY)]++
                    }
                    val max = perHour.maxOrNull()?.coerceAtLeast(1) ?: 1
                    val blocks = "▁▂▃▄▅▆▇█"
                    T("today, by hour", color = t.dim, size = 12.sp)
                    T(perHour.joinToString("") { v -> if (v == 0) "·" else blocks[(v * (blocks.length - 1)) / max].toString() }, color = t.accent, size = 16.sp)
                    T("00          06          12          18        23", color = t.dim, size = 10.sp)
                    Divider()
                    T("busiest apps (kept since launch)", color = t.dim, size = 12.sp)
                    all.groupBy { it.app }.entries.sortedByDescending { it.value.size }.take(8).forEach { (app, list) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            T(app, modifier = Modifier.weight(1f), maxLines = 1)
                            T("${list.size}", color = t.accent2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleChip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = LocalTerm.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) t.button else t.chip).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) { T(label, color = if (on) t.onButton else t.dim, size = 12.sp, weight = FontWeight.SemiBold) }
}
