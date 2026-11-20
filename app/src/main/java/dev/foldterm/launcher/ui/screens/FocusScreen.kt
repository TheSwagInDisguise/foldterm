package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.ui.AppIcon
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.Tabs
import dev.foldterm.launcher.ui.TwoPane
import kotlinx.coroutines.delay

/** Live mm:ss until [until], ticking every second. */
@Composable
fun rememberCountdown(until: Long): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
    return (until - now).coerceAtLeast(0)
}

fun clockText(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

/** Thin bar under the top bar while a focus session runs. */
@Composable
fun FocusBanner(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val until by vm.prefs.focusUntil.collectAsStateWithLifecycle()
    val label by vm.prefs.focusLabel.collectAsStateWithLifecycle()
    if (until <= System.currentTimeMillis()) return
    val left = rememberCountdown(until)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(12.dp)).background(t.accent.copy(alpha = 0.15f))
            .clickable { vm.go(dev.foldterm.launcher.Section.FOCUS) }.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("◎ focus${if (label.isNotBlank()) " · $label" else ""}", color = t.accent, weight = FontWeight.Bold, size = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
        T("${clockText(left)} left", size = 13.sp)
        Spacer(Modifier.width(12.dp))
        T("end", color = t.dim, size = 13.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.stopFocus() }.padding(horizontal = 6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val until by vm.prefs.focusUntil.collectAsStateWithLifecycle()
    val label by vm.prefs.focusLabel.collectAsStateWithLifecycle()
    val dnd by vm.prefs.focusDnd.collectAsStateWithLifecycle()
    val blocked by vm.prefs.focusBlock.collectAsStateWithLifecycle()
    val all by vm.allApps.collectAsStateWithLifecycle()
    val iconStyle by vm.prefs.iconStyle.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(25) }
    val active = until > System.currentTimeMillis()
    val left = rememberCountdown(until)

    val timer: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "focus") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (active) {
                    T(clockText(left), size = 64.sp, weight = FontWeight.Bold, color = t.accent)
                    if (label.isNotBlank()) T(label, size = 18.sp)
                    Spacer(Modifier.height(8.dp))
                    T("[${Fmt.bar(1f - left.toFloat() / ((until - vm.prefs.focusStart()).coerceAtLeast(1)), 24)}]", color = t.accent)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TButton("end session", primary = true) { vm.stopFocus() }
                        TButton("+5 min") { vm.prefs.setFocus(until + 5 * 60_000, label) }
                    }
                } else {
                    T(clockText(minutes * 60_000L), size = 64.sp, weight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 25, 45, 60, 90).forEach { m2 -> TButton("${m2}m", primary = m2 == minutes) { minutes = m2 } }
                    }
                    Spacer(Modifier.height(10.dp))
                    Field(draft, { draft = it }, "what are you focusing on? (optional)", Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    TButton("start focus", primary = true) { vm.startFocus(minutes, draft.trim()); draft = "" }
                }
                Divider()
                T("focused today: ${Fmt.duration(vm.prefs.focusToday() + if (active) System.currentTimeMillis() - vm.prefs.focusStart() else 0).ifBlank { "0m" }}", color = t.dim)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("do not disturb while focusing", modifier = Modifier.weight(1f))
                    Tabs(listOf("off", "on"), if (dnd) 1 else 0, { vm.prefs.setFocusDnd(it == 1) })
                }
                Spacer(Modifier.height(6.dp))
                T("prompt: `focus 25m essay` · `focus stop`", color = t.dim, size = 12.sp)
            }
        }
    }
    val blocker: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "hide while focusing (${blocked.size})") {
            T("ticked apps disappear from the drawer, dock and prompt during a session", color = t.dim, size = 12.sp)
            Spacer(Modifier.height(8.dp))
            val sorted = remember(all, blocked) { all.filter { !it.isWork }.sortedWith(compareByDescending<dev.foldterm.launcher.data.AppEntry> { it.key in blocked }.thenBy { it.label.lowercase() }) }
            LazyColumn(Modifier.fillMaxSize()) {
                items(sorted.size, key = { sorted[it].uniqueKey }) { i ->
                    val app = sorted[i]
                    val on = app.key in blocked
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (on) t.selection else Color.Transparent)
                            .clickable { vm.prefs.toggleFocusBlock(app.key) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(if (on) "[x]" else "[ ]", color = if (on) t.accent else t.dim, weight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        AppIcon(app, 26.dp, iconStyle) { vm.icon(app) }
                        Spacer(Modifier.width(10.dp))
                        T(app.label, maxLines = 1)
                    }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) TwoPane(Modifier.fillMaxSize(), left = timer, right = blocker)
        else Column(Modifier.fillMaxSize()) {
            timer(Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(10.dp))
            blocker(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
