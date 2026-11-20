package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldterm.launcher.data.CalEvent
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.T

private const val MIN = 60_000L

/** Event placed in the timeline: which column it sits in, and how many columns its overlap group uses. */
private data class Placed(val e: CalEvent, val startMin: Int, val endMin: Int, val col: Int, val cols: Int)

/** Gives overlapping events side-by-side columns (greedy, per overlapping group). */
private fun layout(events: List<CalEvent>, dayStart: Long): List<Placed> {
    val items = events.map { e ->
        val s = ((e.begin - dayStart) / MIN).toInt().coerceIn(0, 24 * 60)
        val en = ((e.end - dayStart) / MIN).toInt().coerceIn(s + 15, 24 * 60)
        Triple(e, s, en)
    }.sortedBy { it.second }
    val out = mutableListOf<Placed>()
    var group = mutableListOf<Triple<CalEvent, Int, Int>>()
    var groupEnd = -1
    fun flush() {
        val colEnds = mutableListOf<Int>()
        val placed = group.map { (e, s, en) ->
            var c = colEnds.indexOfFirst { it <= s }
            if (c < 0) { colEnds += en; c = colEnds.lastIndex } else colEnds[c] = en
            Triple(Triple(e, s, en), c, 0)
        }
        placed.forEach { (tr, c, _) -> out += Placed(tr.first, tr.second, tr.third, c, colEnds.size) }
        group = mutableListOf()
    }
    items.forEach { tr ->
        if (group.isNotEmpty() && tr.second >= groupEnd) flush()
        group += tr
        groupEnd = maxOf(groupEnd, tr.third)
    }
    if (group.isNotEmpty()) flush()
    return out
}

/** Gaps of at least an hour between 08:00 and 20:00, as (startMin, endMin). */
private fun freeGaps(placed: List<Placed>): List<Pair<Int, Int>> {
    val busy = placed.map { it.startMin to it.endMin }.sortedBy { it.first }
    val gaps = mutableListOf<Pair<Int, Int>>()
    var cursor = 8 * 60
    for ((s, e) in busy) {
        if (s - cursor >= 60 && cursor < 20 * 60) gaps += cursor to minOf(s, 20 * 60)
        cursor = maxOf(cursor, e)
    }
    if (20 * 60 - cursor >= 60) gaps += cursor to 20 * 60
    return gaps
}

@Composable
fun DayTimeline(
    events: List<CalEvent>,
    dayStart: Long,
    now: Long,
    selected: String?,
    onSelect: (CalEvent) -> Unit,
    onCreate: (Long) -> Unit,
) {
    val t = LocalTerm.current
    val dayEnd = Fmt.addDays(dayStart, 1)
    val todays = events.filter { it.begin < dayEnd && it.end > dayStart }
    val allDay = todays.filter { it.allDay }
    val placed = remember(todays, dayStart) { layout(todays.filter { !it.allDay }, dayStart) }
    val gaps = remember(placed) { freeGaps(placed) }
    val isToday = now in dayStart until dayEnd
    val nowMin = ((now - dayStart) / MIN).toInt()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val labelW = 56.dp

    LaunchedEffect(dayStart) {
        val focusMin = if (isToday) nowMin - 60 else (placed.minOfOrNull { it.startMin } ?: (8 * 60)) - 30
        scroll.scrollTo(with(density) { focusMin.coerceAtLeast(0).dp.roundToPx() })
    }

    Column(Modifier.fillMaxSize()) {
        if (allDay.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                T("all-day", color = t.dim, size = 12.sp, modifier = Modifier.width(labelW))
                allDay.forEach { e ->
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(Color(e.color or 0xFF000000.toInt()).copy(alpha = 0.25f))
                            .clickable { onSelect(e) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) { T(e.title, size = 12.sp, maxLines = 1) }
                }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            // 1dp per minute → 60dp per hour
            BoxWithConstraints(Modifier.fillMaxWidth().height((24 * 60).dp)) {
                val laneW = maxWidth - labelW - 4.dp
                for (h in 0 until 24) {
                    Row(
                        Modifier.offset(y = (h * 60).dp).fillMaxWidth().height(60.dp)
                            .clickable { onCreate(dayStart + h * 60 * MIN) },
                    ) {
                        T("%02d:00".format(h), color = t.dim, size = 11.sp, modifier = Modifier.width(labelW))
                        Box(Modifier.fillMaxWidth().height(1.dp).background(t.border))
                    }
                }
                gaps.forEach { (s, e) ->
                    Box(
                        Modifier.offset(x = labelW, y = (s + 6).dp).width(laneW)
                            .clip(RoundedCornerShape(10.dp)).border(1.dp, t.ok.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .clickable { onCreate(dayStart + s * MIN) }.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        T("free ${hm(s)} → ${hm(e)} · ${Fmt.duration((e - s) * MIN)}   + add", color = t.ok, size = 11.sp, maxLines = 1)
                    }
                }
                placed.forEach { p ->
                    val c = Color(p.e.color or 0xFF000000.toInt())
                    val w = laneW / p.cols
                    val sel = p.e.instanceKey == selected
                    Row(
                        Modifier.offset(x = labelW + w * p.col, y = p.startMin.dp)
                            .width(w - 3.dp).height((p.endMin - p.startMin).coerceAtLeast(22).dp - 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(c.copy(alpha = if (sel) 0.45f else 0.22f))
                            .clickable { onSelect(p.e) },
                    ) {
                        Box(Modifier.width(4.dp).fillMaxSize().background(c))
                        Column(Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
                            T(p.e.title, size = 12.sp, weight = FontWeight.SemiBold, maxLines = 1)
                            if (p.endMin - p.startMin >= 45) {
                                T("${Fmt.time(p.e.begin)}${if (p.e.location.isNotBlank()) " · ${p.e.location}" else ""}", color = t.dim, size = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
                if (isToday) {
                    Row(Modifier.offset(y = (nowMin - 6).dp).fillMaxWidth()) {
                        T("now", color = t.err, size = 10.sp, weight = FontWeight.Bold, modifier = Modifier.width(labelW))
                    }
                    Box(Modifier.offset(x = labelW - 4.dp, y = nowMin.dp).width(laneW + 4.dp).height(2.dp).background(t.err))
                }
            }
        }
    }
}

private fun hm(min: Int) = "%02d:%02d".format(min / 60, min % 60)
