package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HabitsScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val habits by vm.habits.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    Panel(Modifier.fillMaxSize(), title = "habits", trailing = {
        T("${habits.count { it.doneOn(today) }}/${habits.size} today", color = t.dim, size = 12.sp)
    }) {
        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            Spacer(Modifier.weight(1f))
            days.forEach { d ->
                T(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2).lowercase(), color = if (d == today) t.accent else t.dim, size = 11.sp,
                    modifier = Modifier.width(38.dp).padding(start = 8.dp))
            }
            Spacer(Modifier.width(96.dp))
        }
        if (habits.isEmpty()) Empty("no habits yet — add one below (read 20 pages, walk, water…)")
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(habits.size, key = { habits[it].id }) { i ->
                val h = habits[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(t.panelAlt).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        T(h.name, weight = FontWeight.SemiBold, maxLines = 1)
                        T("${h.rate()}% of the last 30 days · best ${h.best()}", color = t.dim, size = 11.sp, maxLines = 1)
                    }
                    days.forEach { d ->
                        val done = h.doneOn(d)
                        Box(
                            Modifier.padding(start = 8.dp).size(30.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (done) t.accent else t.chip)
                                .then(if (d == today) Modifier.border(2.dp, t.accent, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { vm.toggleHabit(h.id, d) },
                            contentAlignment = Alignment.Center,
                        ) { if (done) T("✓", color = t.onButton, size = 13.sp, weight = FontWeight.Bold) }
                    }
                    Column(Modifier.width(66.dp).padding(start = 10.dp), horizontalAlignment = Alignment.End) {
                        T("🔥 ${h.streak()}", color = if (h.streak() > 0) t.warn else t.dim, weight = FontWeight.Bold)
                    }
                    T("✕", color = t.dim, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.removeHabit(h.id) }.padding(6.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(draft, { draft = it }, "new habit · e.g. read 20 pages", Modifier.weight(1f), onDone = { vm.addHabit(draft); draft = "" })
            TButton("add", primary = true) { vm.addHabit(draft); draft = "" }
        }
    }
}

/** Today's habits as tap-to-tick chips, for Home and the cover screen. */
@Composable
internal fun HabitsCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val habits by vm.habits.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.panel).border(1.dp, t.border, RoundedCornerShape(20.dp))
            .clickable { vm.go(dev.foldterm.launcher.Section.HABITS) }.padding(16.dp),
    ) {
        Row {
            T("habits", color = t.accent, size = 12.sp, weight = FontWeight.Bold)
            T(" · ${habits.count { it.doneOn(today) }}/${habits.size} today", color = t.dim, size = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        if (habits.isEmpty()) T("Add habits with `habit add read`.", color = t.dim, size = 13.sp)
        habits.take(6).forEach { h ->
            val done = h.doneOn(today)
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.toggleHabit(h.id, today) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(if (done) "[x]" else "[ ]", color = if (done) t.ok else t.accent, weight = FontWeight.Bold)
                Spacer(Modifier.width(10.dp))
                T(h.name, color = if (done) t.dim else t.fg, modifier = Modifier.weight(1f), maxLines = 1)
                if (h.streak() > 0) T("🔥${h.streak()}", color = t.warn, size = 12.sp)
            }
        }
    }
}
