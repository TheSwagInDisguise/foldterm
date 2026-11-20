package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.ui.AppIcon
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.TwoPane
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun UsageScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val report by vm.usage.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadUsage() }
    val r = report
    if (r == null) {
        Panel(Modifier.fillMaxSize(), title = "screen time") {
            T("$ uptime --per-app", color = t.accent)
            Spacer(Modifier.height(6.dp))
            T("usage access needed", color = t.err)
            Spacer(Modifier.height(8.dp))
            T("Android keeps per-app screen time privately; FoldTerm reads it only on this phone. Find FoldTerm in the list and allow it.", color = t.dim)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TButton("allow usage access", primary = true) { vm.start(vm.screenTime.settingsIntent()) }
                TButton("↻ check again") { vm.loadUsage() }
            }
        }
        return
    }
    val list: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "today · ${Fmt.duration(r.totalMs).ifBlank { "0m" }}", trailing = { T("↻", color = t.accent, size = 18.sp, modifier = Modifier.clickable { vm.loadUsage() }) }) {
            if (r.apps.isEmpty()) { Empty("no app use recorded yet today"); return@Panel }
            val top = r.apps.first().ms.coerceAtLeast(1)
            LazyColumn(Modifier.fillMaxSize()) {
                items(r.apps.size, key = { r.apps[it].pkg }) { i ->
                    val a = r.apps[i]
                    val entry = vm.allApps.value.firstOrNull { it.packageName == a.pkg && !it.isWork }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { entry?.let { vm.launch(it) } }.padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (entry != null) AppIcon(entry, 32.dp, vm.prefs.iconStyle.value) { vm.icon(entry) } else Spacer(Modifier.width(32.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                T(vm.appLabelForPackage(a.pkg), weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                                T(Fmt.duration(a.ms).ifBlank { "<1m" }, color = t.accent)
                            }
                            Spacer(Modifier.height(3.dp))
                            Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(t.chip)) {
                                Box(Modifier.fillMaxWidth(a.ms.toFloat() / top).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(t.accent))
                            }
                            T("opened ${a.opens}×", color = t.dim, size = 11.sp)
                        }
                    }
                }
            }
        }
    }
    val week: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "last 7 days") {
            val max = r.week.maxOf { it.totalMs }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                r.week.forEachIndexed { i, d ->
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                        T(Fmt.duration(d.totalMs).ifBlank { "0" }, color = t.dim, size = 9.sp, maxLines = 1)
                        Box(
                            Modifier.fillMaxWidth().height((4 + 120f * d.totalMs / max).dp).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(if (i == r.week.lastIndex) t.accent else t.accent.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.week.forEach { d ->
                    T(SimpleDateFormat("EEE", Locale.getDefault()).format(Date(d.dayStart)).take(2).lowercase(), color = t.dim, size = 11.sp,
                        align = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            Divider()
            val avg = r.week.dropLast(1).filter { it.totalMs > 0 }.map { it.totalMs }.average().takeIf { !it.isNaN() }?.toLong()
            T("unlocks today: ${r.unlocks}", size = 15.sp)
            if (avg != null) {
                T("daily average (6 days): ${Fmt.duration(avg)}", color = t.dim)
                val diff = r.totalMs - avg
                T(if (diff > 0) "today is ${Fmt.duration(diff)} above average so far" else "today is ${Fmt.duration(-diff).ifBlank { "0m" }} below average so far",
                    color = if (diff > 0) t.warn else t.ok)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) TwoPane(Modifier.fillMaxSize(), left = list, right = week)
        else Column(Modifier.fillMaxSize()) {
            week(Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            list(Modifier.fillMaxWidth().weight(1f))
        }
    }
}
