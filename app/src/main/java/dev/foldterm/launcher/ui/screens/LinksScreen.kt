package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.Tabs

@Composable
fun LinksScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val all by vm.prefs.bookmarks.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    val shown = if (tab == 0) all.filter { !it.read } else all.filter { it.read }
    Panel(Modifier.fillMaxSize(), title = "links · ${all.count { !it.read }} unread") {
        Tabs(listOf("unread", "read"), tab, { tab = it })
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Field(draft, { draft = it }, "paste a link to save", Modifier.weight(1f), onDone = { if (draft.isNotBlank()) { vm.execShell("save ${draft.trim()}"); vm.shellOpen.value = false; draft = "" } })
            Spacer(Modifier.width(8.dp))
            TButton("save", primary = true) { if (draft.isNotBlank()) { vm.execShell("save ${draft.trim()}"); vm.shellOpen.value = false; draft = "" } }
        }
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) Empty(if (tab == 0) "nothing to read — share a link to FoldTerm from any app" else "nothing read yet")
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown.size, key = { shown[it].id }) { i ->
                val b = shown[i]
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).clickable { vm.openBookmark(b) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (b.read) "  " else "● ", color = t.accent2, size = 12.sp)
                    Column(Modifier.weight(1f)) {
                        T(b.title.ifBlank { b.url }, weight = if (b.read) FontWeight.Normal else FontWeight.SemiBold, color = if (b.read) t.dim else t.fg, maxLines = 2)
                        T("${b.url.substringAfter("//").substringBefore('/')} · ${Fmt.ago(b.added)}", color = t.accent3, size = 11.sp, maxLines = 1)
                    }
                    T("share", color = t.dim, size = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                        vm.start(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, b.url), "share link"))
                    }.padding(6.dp))
                    T("✕", color = t.dim, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.prefs.saveBookmarks(all.filterNot { it.id == b.id }) }.padding(6.dp))
                }
            }
        }
    }
}

/** Unread links for Home and the cover screen. */
@Composable
internal fun LinksCard(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val all by vm.prefs.bookmarks.collectAsStateWithLifecycle()
    val unread = all.filter { !it.read }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(t.panel).border(1.dp, t.border, RoundedCornerShape(20.dp))
            .clickable { vm.go(dev.foldterm.launcher.Section.LINKS) }.padding(16.dp),
    ) {
        Row {
            T("read later", color = t.accent, size = 12.sp, weight = FontWeight.Bold)
            T(" · ${unread.size}", color = t.dim, size = 12.sp)
        }
        Spacer(Modifier.height(6.dp))
        if (unread.isEmpty()) T("Share a link to FoldTerm to save it here.", color = t.dim, size = 13.sp)
        unread.take(3).forEach { b ->
            T("↗ ${b.title.ifBlank { b.url }}", maxLines = 1, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { vm.openBookmark(b) }.padding(vertical = 5.dp))
        }
    }
}
