package dev.foldterm.launcher.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.BuildConfig
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.data.NowPlaying
import dev.foldterm.launcher.data.NowPlayingStore
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.TwoPane
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun MediaScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val access by vm.notifAccess.collectAsStateWithLifecycle()
    if (!BuildConfig.NOTIFICATIONS || !access) {
        Panel(Modifier.fillMaxSize(), title = "media") {
            T("$ mediactl status", color = t.accent)
            Spacer(Modifier.height(6.dp))
            T(if (!BuildConfig.NOTIFICATIONS) "not available in the lite edition" else "permission denied", color = t.err)
            Spacer(Modifier.height(10.dp))
            T("Android only shares what's playing with apps that have notification access.", color = t.dim)
            if (BuildConfig.NOTIFICATIONS) {
                Spacer(Modifier.height(14.dp))
                TButton("grant access", primary = true) { vm.openNotificationAccess() }
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            TwoPane(Modifier.fillMaxSize(), leftFraction = 0.58f, left = { m -> PlayerPanel(vm, m) }, right = { m -> SessionsPanel(vm, m) })
        } else {
            Column(Modifier.fillMaxSize()) {
                PlayerPanel(vm, Modifier.fillMaxWidth().weight(1.3f))
                Spacer(Modifier.height(10.dp))
                SessionsPanel(vm, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun PlayerPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val np by NowPlayingStore.state.collectAsStateWithLifecycle()
    var clock by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { clock = SystemClock.elapsedRealtime(); delay(500) } }
    Panel(modifier, title = "now playing${np?.let { " · ${vm.appLabelForPackage(it.pkg)}" } ?: ""}") {
        val cur = np
        if (cur == null) {
            Empty("nothing playing — start music in any app and it shows up here")
            return@Panel
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(cur, Modifier.widthIn(max = 180.dp).fillMaxWidth(0.4f).aspectRatio(1f))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f)) {
                    T(cur.title, size = 22.sp, weight = FontWeight.Bold, maxLines = 3)
                    if (cur.artist.isNotBlank()) T(cur.artist, color = t.accent2, maxLines = 1)
                    if (cur.album.isNotBlank()) T(cur.album, color = t.dim, size = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
            Seek(cur, clock)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Control("⏮", 22) { NowPlayingStore.previous() }
                if (cur.canSeek) Control("−15", 14) { NowPlayingStore.seekTo(cur.positionNow(clock) - 15_000) }
                Box(
                    Modifier.padding(horizontal = 14.dp).size(64.dp).clip(CircleShape).background(t.button)
                        .clickable { NowPlayingStore.toggle() },
                    contentAlignment = Alignment.Center,
                ) { T(if (cur.isPlaying) "▮▮" else "▶", color = t.onButton, size = 22.sp, weight = FontWeight.Bold) }
                if (cur.canSeek) Control("+30", 14) { NowPlayingStore.seekTo(cur.positionNow(clock) + 30_000) }
                Control("⏭", 22) { NowPlayingStore.next() }
            }
            Spacer(Modifier.height(14.dp))
            Visualizer(cur.isPlaying, clock)
            Spacer(Modifier.height(10.dp))
            TButton("open ${vm.appLabelForPackage(cur.pkg)}") { vm.openPackage(cur.pkg) }
        }
    }
}

@Composable
private fun Art(np: NowPlaying, modifier: Modifier) {
    val t = LocalTerm.current
    val art = np.art
    if (art != null) {
        Image(art.asImageBitmap(), null, modifier.clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier.clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(t.accent, t.accent2, t.accent3))),
            contentAlignment = Alignment.Center,
        ) { T("♫", color = t.onButton, size = 40.sp) }
    }
}

@Composable
private fun Seek(np: NowPlaying, clock: Long) {
    val t = LocalTerm.current
    val pos = np.positionNow(clock)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    if (np.durationMs > 0) {
        val frac = (pos.toFloat() / np.durationMs).coerceIn(0f, 1f)
        Slider(
            value = if (dragging) dragValue else frac,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { NowPlayingStore.seekTo((dragValue * np.durationMs).toLong()); dragging = false },
            enabled = np.canSeek,
            colors = SliderDefaults.colors(thumbColor = t.accent, activeTrackColor = t.accent, inactiveTrackColor = t.chip),
        )
        Row(Modifier.fillMaxWidth()) {
            T(mmss(if (dragging) (dragValue * np.durationMs).toLong() else pos), color = t.dim, size = 12.sp, modifier = Modifier.weight(1f))
            T(mmss(np.durationMs), color = t.dim, size = 12.sp)
        }
    } else {
        T(mmss(pos), color = t.dim, size = 12.sp)
    }
}

@Composable
private fun Control(glyph: String, size: Int, onClick: () -> Unit) {
    T(glyph, size = size.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClick).padding(12.dp))
}

/** Decorative level meter; Android doesn't expose other apps' audio without extra permissions. */
@Composable
private fun Visualizer(playing: Boolean, clock: Long) {
    val t = LocalTerm.current
    val blocks = "▁▂▃▄▅▆▇█"
    val text = (0 until 36).joinToString("") { i ->
        if (!playing) "▁" else {
            val v = abs(sin(i * 0.55 + clock / 260.0) + sin(i * 0.21 - clock / 410.0)) / 2
            blocks[(v * (blocks.length - 1)).toInt().coerceIn(0, blocks.length - 1)].toString()
        }
    }
    T(text, color = t.accent, size = 14.sp, maxLines = 1)
}

@Composable
private fun SessionsPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val sessions by NowPlayingStore.sessions.collectAsStateWithLifecycle()
    val np by NowPlayingStore.state.collectAsStateWithLifecycle()
    var volume by remember { mutableFloatStateOf(vm.volumeFraction()) }
    val outputs = remember(np?.pkg, np?.isPlaying) { vm.audioOutputs() }
    Panel(modifier, title = "sessions (${sessions.size})") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (sessions.isEmpty()) T("# no media apps active", color = t.dim)
            sessions.forEach { s ->
                val current = s.pkg == np?.pkg
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (current) t.selection else t.panelAlt)
                        .clickable { NowPlayingStore.select(s.pkg) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (s.isPlaying) "▶" else "❚❚", color = if (s.isPlaying) t.accent else t.dim, modifier = Modifier.width(28.dp))
                    Column(Modifier.weight(1f)) {
                        T(vm.appLabelForPackage(s.pkg), weight = FontWeight.SemiBold, maxLines = 1)
                        if (s.title.isNotBlank()) T(s.title, color = t.dim, size = 12.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Divider()
            T("output", color = t.dim, size = 12.sp)
            outputs.forEach { T("→ $it", color = t.accent2) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("volume", color = t.dim, size = 12.sp, modifier = Modifier.weight(1f))
                T("${(volume * 100).toInt()}%", size = 12.sp)
            }
            Slider(
                value = volume,
                onValueChange = { volume = it; vm.setVolumeFraction(it) },
                colors = SliderDefaults.colors(thumbColor = t.accent2, activeTrackColor = t.accent2, inactiveTrackColor = t.chip),
            )
        }
    }
}

private fun mmss(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
