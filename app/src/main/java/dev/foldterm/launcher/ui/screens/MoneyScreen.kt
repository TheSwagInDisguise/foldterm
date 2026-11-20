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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.TwoPane
import java.util.Calendar

@Composable
fun MoneyScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val all by vm.expenses.collectAsStateWithLifecycle()
    val budget by vm.prefs.budget.collectAsStateWithLifecycle()
    val cur by vm.prefs.currencySymbol.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var budgetDraft by remember(budget) { mutableStateOf(if (budget > 0) Fmt.money(budget) else "") }
    val monthStart = vm.monthStart()
    val month = all.filter { it.time >= monthStart }
    val total = month.sumOf { it.amount }

    val summary: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "this month") {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                T("$cur${Fmt.money(total)}", size = 40.sp, weight = FontWeight.Bold, color = t.accent)
                if (budget > 0) {
                    val frac = (total / budget).toFloat()
                    T("of $cur${Fmt.money(budget)} budget · ${if (budget >= total) "$cur${Fmt.money(budget - total)} left" else "$cur${Fmt.money(total - budget)} over"}",
                        color = if (frac > 1f) t.err else t.dim)
                    Spacer(Modifier.height(6.dp))
                    T("[${Fmt.bar(frac, 24)}] ${(frac * 100).toInt()}%", color = if (frac > 1f) t.err else if (frac > 0.8f) t.warn else t.accent)
                    val cal = Calendar.getInstance()
                    val day = cal.get(Calendar.DAY_OF_MONTH); val days = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    T("pace: on track for $cur${Fmt.money(total / day * days)} this month", color = t.dim, size = 12.sp)
                }
                Divider()
                T("by category", color = t.dim, size = 12.sp)
                val byCat = month.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.entries.sortedByDescending { it.value }
                val top = byCat.firstOrNull()?.value ?: 1.0
                byCat.forEach { (c, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(c, modifier = Modifier.width(110.dp), maxLines = 1)
                        T(Fmt.bar((v / top).toFloat(), 14), color = t.accent2, size = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        T("$cur${Fmt.money(v)}", size = 13.sp)
                    }
                }
                if (byCat.isEmpty()) T("# nothing logged this month", color = t.dim)
                Divider()
                T("last 14 days", color = t.dim, size = 12.sp)
                val today = Fmt.startOfDay(System.currentTimeMillis())
                val daily = (13 downTo 0).map { d -> val s = Fmt.addDays(today, -d); all.filter { it.time >= s && it.time < Fmt.addDays(s, 1) }.sumOf { it.amount } }
                val dMax = daily.maxOrNull()?.takeIf { it > 0 } ?: 1.0
                Row(Modifier.fillMaxWidth().height(90.dp).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    daily.forEachIndexed { i, v ->
                        Box(Modifier.weight(1f).fillMaxHeight((v / dMax).toFloat().coerceAtLeast(0.02f)).clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(if (i == 13) t.accent else t.accent.copy(alpha = 0.4f)))
                    }
                }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(budgetDraft, { budgetDraft = it }, "monthly budget", Modifier.weight(1f), onDone = { vm.prefs.setBudget(budgetDraft.toDoubleOrNull() ?: 0.0) })
                    Spacer(Modifier.width(8.dp))
                    TButton("set") { vm.prefs.setBudget(budgetDraft.replace(",", "").toDoubleOrNull() ?: 0.0) }
                }
            }
        }
    }
    val log: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "log") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Field(draft, { draft = it }, "12.50 lunch with maya", Modifier.weight(1f), onDone = { if (vm.logExpense(draft)) draft = "" })
                Spacer(Modifier.width(8.dp))
                TButton("add", primary = true) { if (vm.logExpense(draft)) draft = "" }
            }
            Spacer(Modifier.height(8.dp))
            if (all.isEmpty()) Empty("type an amount then what it was for, or `spent 12.50 lunch` in the prompt")
            LazyColumn(Modifier.fillMaxSize()) {
                items(all.size, key = { all[it].id }) { i ->
                    val e = all[i]
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(e.note.ifBlank { e.category }, maxLines = 1)
                            T("${e.category} · ${Fmt.dayLabel(e.time)} ${Fmt.time(e.time)}", color = t.dim, size = 11.sp)
                        }
                        T("$cur${Fmt.money(e.amount)}", weight = FontWeight.SemiBold, align = TextAlign.End)
                        T("✕", color = t.dim, modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).clickable { vm.removeExpense(e.id) }.padding(6.dp))
                    }
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) TwoPane(Modifier.fillMaxSize(), left = summary, right = log)
        else Column(Modifier.fillMaxSize()) { summary(Modifier.fillMaxWidth().weight(1f)); Spacer(Modifier.height(10.dp)); log(Modifier.fillMaxWidth().weight(1f)) }
    }
}
