package dev.foldterm.launcher.ui.screens

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.data.WidgetSlot
import dev.foldterm.launcher.ui.AsyncBitmap
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Mono
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.TwoPane

// ───────────────────────── notes ─────────────────────────

@Composable
fun NotesScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val notes by vm.prefs.notes.collectAsStateWithLifecycle()
    val selectedId by vm.selectedNote.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    val selected = notes.firstOrNull { it.id == selectedId } ?: notes.firstOrNull()

    val list: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "notes (${notes.size})", trailing = { TButton("+ new", primary = true) { vm.newNote() } }) {
            Field(filter, { filter = it }, "grep notes…", Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val shown = notes.filter { filter.isBlank() || it.text.contains(filter, true) }
            if (shown.isEmpty()) Empty(if (notes.isEmpty()) "no notes yet — `note buy milk` works too" else "no matches")
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown.size, key = { shown[it].id }) { i ->
                    val n = shown[i]
                    val sel = n.id == selected?.id
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) t.selection else Color.Transparent)
                            .clickable { vm.selectedNote.value = n.id }.padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(n.title, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                            T(Fmt.ago(n.updated), color = t.dim, size = 12.sp)
                        }
                        val body = n.text.lines().drop(1).joinToString(" ").trim()
                        if (body.isNotEmpty()) T(body, color = t.dim, size = 13.sp, maxLines = 2)
                    }
                }
            }
        }
    }
    val editor: @Composable (Modifier) -> Unit = { m ->
        Panel(m) {
            if (selected == null) {
                Empty("nothing to edit", "+ new note") { vm.newNote() }
                return@Panel
            }
            // local text so typing stays smooth; every change is saved straight away
            var text by remember(selected.id) { mutableStateOf(selected.text) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("~/notes/${selected.id}.md", color = t.accent, size = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                TButton("copy") { vm.copy(text) }
                Spacer(Modifier.width(8.dp))
                TButton("delete") { vm.deleteNote(selected.id) }
            }
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it; vm.updateNote(selected.id, it) },
                textStyle = TextStyle(fontFamily = Mono, fontSize = 15.sp, color = t.fg, lineHeight = 22.sp),
                cursorBrush = SolidColor(t.accent),
                modifier = Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(t.panelAlt)
                    .border(1.dp, t.border, RoundedCornerShape(14.dp)).padding(16.dp),
                decorationBox = { inner ->
                    Box {
                        if (text.isEmpty()) T("start typing…", color = t.dim)
                        inner()
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
            T("${text.length} chars · ${text.lines().size} lines · saved ${Fmt.time(selected.updated)}", color = t.dim, size = 12.sp)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) TwoPane(Modifier.fillMaxSize(), leftFraction = 0.38f, left = list, right = editor)
        else Column(Modifier.fillMaxSize()) {
            list(Modifier.fillMaxWidth().height(260.dp))
            Spacer(Modifier.height(10.dp))
            editor(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

// ───────────────────────── widgets ─────────────────────────

@Composable
fun WidgetsScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val slots by vm.prefs.widgets.collectAsStateWithLifecycle()
    val picker by vm.widgetPickerOpen.collectAsStateWithLifecycle()
    Box(Modifier.fillMaxSize()) {
        Panel(
            Modifier.fillMaxSize(), title = "widgets (${slots.size})",
            trailing = { TButton("+ add widget", primary = true) { vm.widgetPickerOpen.value = true } },
        ) {
            if (slots.isEmpty()) {
                Empty("no widgets yet — add clocks, music players, notes, anything your apps provide", "+ add widget") {
                    vm.widgetPickerOpen.value = true
                }
                return@Panel
            }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val twoCols = maxWidth >= 700.dp
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (twoCols) {
                        slots.chunked(2).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { WidgetCard(vm, it, Modifier.weight(1f)) }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    } else {
                        slots.forEach { WidgetCard(vm, it, Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
        if (picker) WidgetPicker(vm)
    }
}

@Composable
internal fun WidgetCard(vm: LauncherViewModel, slot: WidgetSlot, modifier: Modifier) {
    val t = LocalTerm.current
    val info = remember(slot.id) { vm.widgetsHost.info(slot.id) }
    val density = LocalDensity.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).border(1.dp, t.border, RoundedCornerShape(18.dp)).background(t.panelAlt)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            T(info?.let { vm.widgetsHost.label(it) } ?: "widget #${slot.id}", color = t.dim, size = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
            listOf("↑" to { vm.moveWidget(slot.id, -1) }, "↓" to { vm.moveWidget(slot.id, 1) },
                "−" to { vm.resizeWidget(slot.id, -40) }, "+" to { vm.resizeWidget(slot.id, 40) },
                "✕" to { vm.removeWidget(slot.id) }).forEach { (g, action) ->
                T(g, color = if (g == "✕") t.err else t.fg, size = 15.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { action() }.padding(horizontal = 8.dp, vertical = 2.dp))
            }
        }
        if (info == null) {
            Box(Modifier.fillMaxWidth().height(slot.heightDp.dp), contentAlignment = Alignment.Center) {
                T("# widget unavailable (app removed?)", color = t.dim)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    vm.widgetsHost.host.createView(ctx, slot.id, info).apply { setPadding(0, 0, 0, 0) }
                },
                modifier = Modifier.fillMaxWidth().height(slot.heightDp.dp)
                    .onSizeChanged { s ->
                        with(density) { vm.widgetsHost.updateSize(slot.id, s.width.toDp().value.toInt(), s.height.toDp().value.toInt()) }
                    },
                update = { v: AppWidgetHostView -> v.requestLayout() },
            )
        }
    }
}

@Composable
private fun WidgetPicker(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val ctx = LocalContext.current
    var providers by remember { mutableStateOf<List<AppWidgetProviderInfo>>(emptyList()) }
    var filter by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        providers = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.widgetsHost.providers() }
    }
    val shown = providers.filter {
        filter.isBlank() || vm.widgetsHost.label(it).contains(filter, true) || vm.widgetsHost.appLabel(it).contains(filter, true)
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (t.dark) 0.5f else 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.widgetPickerOpen.value = false },
        contentAlignment = Alignment.Center,
    ) {
        Panel(
            Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.88f).widthIn(max = 900.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            title = "add widget (${providers.size})",
            trailing = { TButton("close") { vm.widgetPickerOpen.value = false } },
        ) {
            Field(filter, { filter = it }, "filter widgets…", Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            if (providers.isEmpty()) Empty("loading widget list…")
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown.size, key = { "${shown[it].provider.flattenToString()}#${shown[it].profile.hashCode()}" }) { i ->
                    val info = shown[i]
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                            .clickable { vm.addWidget(info) }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncBitmap(info.provider.flattenToString(), Modifier.size(width = 96.dp, height = 64.dp).clip(RoundedCornerShape(10.dp))) {
                            val dpi = ctx.resources.displayMetrics.densityDpi
                            val d = runCatching { info.loadPreviewImage(ctx, dpi) ?: info.loadIcon(ctx, dpi) }.getOrNull()
                            d?.let { dr ->
                                val w = dr.intrinsicWidth.coerceIn(1, 480)
                                val h = dr.intrinsicHeight.coerceIn(1, 320)
                                dr.toBitmap(w, h, Bitmap.Config.ARGB_8888)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            T(vm.widgetsHost.label(info), weight = FontWeight.SemiBold, maxLines = 1)
                            T(vm.widgetsHost.appLabel(info), color = t.dim, size = 12.sp, maxLines = 1)
                        }
                        val density = ctx.resources.displayMetrics.density
                        T("${(info.minWidth / density).toInt()}×${(info.minHeight / density).toInt()}dp", color = t.dim, size = 12.sp)
                    }
                }
            }
        }
    }
}
