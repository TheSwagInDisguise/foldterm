package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import kotlinx.coroutines.delay

@Composable
fun MemosScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val list by vm.memos.list.collectAsStateWithLifecycle()
    val since by vm.memos.recordingSince.collectAsStateWithLifecycle()
    val playing by vm.memos.playing.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) { while (since > 0) { now = System.currentTimeMillis(); delay(200) } }
    val recording = since > 0
    Panel(Modifier.fillMaxSize(), title = "memos (${list.size})") {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).clip(CircleShape).background(if (recording) t.err else t.button)
                    .clickable { if (recording) vm.memos.stop() else vm.startMemo() },
                contentAlignment = Alignment.Center,
            ) { T(if (recording) "■" else "●", color = if (recording) Color.White else t.onButton, size = 36.sp) }
            Spacer(Modifier.height(8.dp))
            T(if (recording) "recording ${clockText(now - since)}" else "tap to record", color = if (recording) t.err else t.dim, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        if (list.isEmpty()) Empty("no memos yet")
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.size, key = { list[it].file.name }) { i ->
                val m = list[i]
                val on = playing == m.file.name
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) t.selection else t.panelAlt).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (on) "■" else "▶", color = t.accent, size = 18.sp, modifier = Modifier.clip(CircleShape)
                        .clickable { if (on) vm.memos.stopPlayback() else vm.memos.play(m) }.padding(8.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        T(m.file.nameWithoutExtension.removePrefix("memo-").let { if (it.all { c -> c.isDigit() }) "memo · ${Fmt.dayLabel(m.created)} ${Fmt.time(m.created)}" else it },
                            weight = FontWeight.SemiBold, maxLines = 1)
                        T("${clockText(m.durationMs)} · ${m.file.length() / 1024} KB", color = t.dim, size = 11.sp)
                    }
                    T("share", color = t.dim, size = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.shareMemo(m) }.padding(6.dp))
                    T("✕", color = t.dim, modifier = Modifier.clip(CircleShape).clickable { vm.memos.delete(m) }.padding(8.dp))
                }
            }
        }
    }
}
