package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
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
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.Tabs

@Composable
fun PodcastsScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val eps by vm.episodes.collectAsStateWithLifecycle()
    val status by vm.podcastStatus.collectAsStateWithLifecycle()
    val playing by vm.radio.playing.collectAsStateWithLifecycle()
    val feeds by vm.prefs.podcastFeeds.collectAsStateWithLifecycle()
    var show by rememberSaveable { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (eps.isEmpty()) vm.loadPodcasts() }
    val shows = eps.map { it.show }.distinct()
    val shown = eps.filter { show == null || it.show == show }
    Panel(Modifier.fillMaxSize(), title = "podcasts", trailing = {
        T(status, color = t.dim, size = 12.sp)
        Spacer(Modifier.width(8.dp))
        T("↻", color = t.accent, size = 18.sp, modifier = Modifier.clickable { vm.loadPodcasts() })
    }) {
        if (shows.size > 1) {
            Tabs(listOf("all") + shows.map { it.take(16) }, if (show == null) 0 else shows.indexOf(show) + 1, { i -> show = if (i == 0) null else shows[i - 1] })
            Spacer(Modifier.height(10.dp))
        }
        playing?.let { p ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.selection).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                T("▶ ${p.name}", weight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
                TButton("pause") { vm.radio.pause() }
                Spacer(Modifier.width(6.dp))
                TButton("stop") { vm.radio.stop() }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (shown.isEmpty()) Empty(status.ifBlank { "no episodes yet" }, "refresh") { vm.loadPodcasts() }
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(shown.size, key = { shown[it].audio + it }) { i ->
                val e = shown[i]
                val on = playing?.url == e.audio
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
                        .background(if (on) t.selection else Color.Transparent)
                        .clickable { vm.playEpisode(e) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (on) "▶" else "◖", color = if (on) t.accent else t.dim, modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        T(e.title, weight = FontWeight.SemiBold, maxLines = 2)
                        T("${e.show}${e.published?.let { " · ${Fmt.ago(it)}" } ?: ""}${if (e.duration.isNotBlank()) " · ${e.duration}" else ""}", color = t.accent3, size = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(draft, { draft = it }, "add a podcast feed url (${feeds.size} subscribed)", Modifier.weight(1f), onDone = { vm.addPodcast(draft); draft = "" })
            Spacer(Modifier.width(8.dp))
            TButton("subscribe") { vm.addPodcast(draft); draft = "" }
        }
    }
}
