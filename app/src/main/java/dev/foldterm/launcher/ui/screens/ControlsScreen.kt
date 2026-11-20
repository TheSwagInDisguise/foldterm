package dev.foldterm.launcher.ui.screens

import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.TwoPane

@Composable
fun ControlsScreen(vm: LauncherViewModel) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            TwoPane(Modifier.fillMaxSize(), leftFraction = 0.5f, left = { m -> SoundPanel(vm, m) }, right = { m -> DisplayPanel(vm, m) })
        } else {
            Column(Modifier.fillMaxSize()) {
                SoundPanel(vm, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(10.dp))
                DisplayPanel(vm, Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

@Composable
private fun Toggle(label: String, on: Boolean, onClick: () -> Unit) {
    val t = LocalTerm.current
    Box(
        Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) t.button else t.chip).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { T(label, color = if (on) t.onButton else t.fg, weight = FontWeight.SemiBold, size = 13.sp) }
}

@Composable
private fun LabeledSlider(label: String, initial: Float, enabled: Boolean = true, onChange: (Float) -> Unit) {
    val t = LocalTerm.current
    var v by remember(initial) { mutableFloatStateOf(initial) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        T(label, color = t.dim, size = 12.sp, modifier = Modifier.weight(1f))
        T("${(v * 100).toInt()}%", size = 12.sp)
    }
    Slider(
        value = v, enabled = enabled,
        onValueChange = { v = it; onChange(it) },
        colors = SliderDefaults.colors(thumbColor = t.accent, activeTrackColor = t.accent, inactiveTrackColor = t.chip),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val tick by vm.controlsTick.collectAsStateWithLifecycle()
    val c = vm.controls
    LaunchedEffect(Unit) { vm.controlsTick.value++ }
    Panel(modifier, title = "sound") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val mode = remember(tick) { c.ringerMode() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Toggle("🔔 ring", mode == AudioManager.RINGER_MODE_NORMAL) { vm.control { c.setRingerMode(AudioManager.RINGER_MODE_NORMAL) } }
                Toggle("〰 vibrate", mode == AudioManager.RINGER_MODE_VIBRATE) { vm.control { c.setRingerMode(AudioManager.RINGER_MODE_VIBRATE) } }
                Toggle("🔕 silent", mode == AudioManager.RINGER_MODE_SILENT) { vm.control { c.setRingerMode(AudioManager.RINGER_MODE_SILENT) } }
                val dnd = remember(tick) { c.dndOn() }
                Toggle("⛔ do not disturb", dnd) { vm.control { c.setDnd(!dnd) } }
            }
            if (remember(tick) { !c.dndAccess() }) {
                Spacer(Modifier.height(6.dp))
                T("silent and Do Not Disturb need DND access →", color = t.dim, size = 12.sp,
                    modifier = Modifier.clickable { vm.start(c.dndAccessIntent()) })
            }
            Divider()
            c.streams.forEach { s ->
                LabeledSlider(s.label, remember(tick, s.id) { c.volume(s.id) }) { f -> vm.controls.setVolume(s.id, f)?.let { vm.out(it) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisplayPanel(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val tick by vm.controlsTick.collectAsStateWithLifecycle()
    val torch by vm.torchOn.collectAsStateWithLifecycle()
    val c = vm.controls
    Panel(modifier, title = "display & device") {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            val canWrite = remember(tick) { c.canWrite() }
            if (!canWrite) {
                T("brightness, rotation and timeout need “modify system settings”", color = t.dim, size = 12.sp)
                Spacer(Modifier.height(6.dp))
                TButton("allow", primary = true) { vm.start(c.writeSettingsIntent()) }
                Spacer(Modifier.height(10.dp))
            }
            val auto = remember(tick) { c.autoBrightness() }
            LabeledSlider("brightness${if (auto) " (auto)" else ""}", remember(tick) { c.brightness() }, enabled = canWrite) { f -> c.setBrightness(f) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Toggle("☀ auto brightness", auto) { vm.control { c.setAutoBrightness(!auto) } }
                val rot = remember(tick) { c.autoRotate() }
                Toggle("⟳ auto-rotate", rot) { vm.control { c.setAutoRotate(!rot) } }
                Toggle("🔦 torch", torch) { vm.setTorch(null) }
            }
            Spacer(Modifier.height(12.dp))
            T("screen timeout", color = t.dim, size = 12.sp)
            Spacer(Modifier.height(6.dp))
            val timeout = remember(tick) { c.timeoutMs() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(15_000 to "15s", 30_000 to "30s", 60_000 to "1m", 120_000 to "2m", 300_000 to "5m", 600_000 to "10m").forEach { (ms, label) ->
                    Toggle(label, timeout == ms) { vm.control { c.setTimeout(ms) } }
                }
            }
            Divider()
            T("connectivity", color = t.dim, size = 12.sp)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TButton("internet") { vm.start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) }
                TButton("bluetooth") { vm.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                TButton("nfc") { vm.start(Intent(Settings.Panel.ACTION_NFC)) }
                TButton("hotspot") { vm.start(Intent(Settings.ACTION_WIRELESS_SETTINGS)) }
                TButton("battery saver") { vm.start(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
                TButton("location") { vm.start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            }
        }
    }
}
