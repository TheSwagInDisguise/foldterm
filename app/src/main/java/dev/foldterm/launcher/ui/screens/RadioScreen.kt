package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.data.Station
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton

@Composable
fun RadioScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val stations by vm.stations.collectAsStateWithLifecycle()
    val playing by vm.radio.playing.collectAsStateWithLifecycle()
    val status by vm.radio.status.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Panel(Modifier.fillMaxSize(), title = "radio") {
        val cur = playing
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.panelAlt).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                T(if (cur == null) "off air" else "≋ ${cur.name}", size = 20.sp, weight = FontWeight.Bold, color = if (cur == null) t.dim else t.accent)
                T(status.ifBlank { "pick a station" }, color = t.dim, size = 12.sp)
            }
            if (cur != null) {
                TButton(if (status == "paused") "▶ play" else "▮▮ pause", primary = true) { if (status == "paused") vm.radio.resume() else vm.radio.pause() }
                Spacer(Modifier.width(8.dp))
                TButton("■ stop") { vm.radio.stop() }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(stations.size, key = { stations[it].url }) { i ->
                val s = stations[i]
                val on = s.url == cur?.url
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) t.selection else Color.Transparent)
                        .clickable { vm.radio.play(s) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (on) "▶" else "≋", color = if (on) t.accent else t.dim, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        T(s.name, weight = FontWeight.SemiBold, maxLines = 1)
                        T(s.url.removePrefix("https://").removePrefix("http://"), color = t.dim, size = 11.sp, maxLines = 1)
                    }
                    T("✕", color = t.dim, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.removeStation(s) }.padding(8.dp))
                }
            }
        }
        Divider()
        T("add a station (any https MP3/AAC stream)", color = t.dim, size = 12.sp)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(name, { name = it }, "name", Modifier.weight(0.4f))
            Field(url, { url = it }, "https://…", Modifier.weight(0.6f))
            TButton("add", enabled = url.startsWith("http")) { vm.addStation(Station(name.ifBlank { url.substringAfter("//").substringBefore('/') }, url.trim())); name = ""; url = "" }
        }
    }
}

