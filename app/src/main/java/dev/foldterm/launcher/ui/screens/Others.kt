package dev.foldterm.launcher.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.BuildConfig
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.Section
import dev.foldterm.launcher.orderedSections
import dev.foldterm.launcher.data.ContactSummary
import dev.foldterm.launcher.data.NotifKind
import dev.foldterm.launcher.ui.AsyncBitmap
import dev.foldterm.launcher.ui.Avatar
import dev.foldterm.launcher.ui.Chip
import dev.foldterm.launcher.ui.Divider
import dev.foldterm.launcher.ui.Empty
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.Fmt
import dev.foldterm.launcher.ui.KV
import dev.foldterm.launcher.ui.LocalFold
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.Tabs
import dev.foldterm.launcher.ui.Themes
import dev.foldterm.launcher.ui.TwoPane
import dev.foldterm.launcher.ui.nameColor
import dev.foldterm.launcher.ui.parseHexColor

/** Classic terminal phosphors and a few extras for mono icons. */
private val ICON_TINTS = listOf(
    "#7EE787", "#33FF66", "#FFB000", "#79C0FF", "#00E5FF",
    "#D2A8FF", "#F778BA", "#FF7B72", "#E6EDF3", "#8B949E",
)

@Composable
private fun Adaptive(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit, leftFraction: Float = 0.42f) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 640.dp) {
            TwoPane(Modifier.fillMaxSize(), leftFraction = leftFraction, left = left, right = right)
        } else {
            Column(Modifier.fillMaxSize()) {
                left(Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(10.dp))
                right(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

// ───────────────────────── mail / chat ─────────────────────────

@Composable
fun MessagesScreen(vm: LauncherViewModel, kind: NotifKind) {
    val t = LocalTerm.current
    val all by vm.notifications.collectAsStateWithLifecycle()
    val access by vm.notifAccess.collectAsStateWithLifecycle()
    val selectedKey by vm.selectedNotif.collectAsStateWithLifecycle()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val list = remember(all, kind, showAll) {
        all.filter { (it.kind == kind || (showAll && it.kind == NotifKind.OTHER)) }
    }
    val sel = list.firstOrNull { it.key == selectedKey } ?: list.firstOrNull { !it.removed }
    val label = if (kind == NotifKind.MAIL) "mail" else "chat"

    if (!access) {
        Panel(Modifier.fillMaxSize(), title = label) {
            T("$ tail -f /dev/notifications", color = t.accent)
            Spacer(Modifier.height(6.dp))
            if (!BuildConfig.NOTIFICATIONS) {
                T("not available in the lite edition", color = t.err)
                Spacer(Modifier.height(12.dp))
                T("This pane is built from your apps' notifications. The lite APK leaves notification access out so it installs without a Play Protect block. Install the full APK from the releases page to use it.", color = t.dim)
                Spacer(Modifier.height(16.dp))
                TButton("open $label app", primary = true) { openCategoryApp(vm, kind) }
                return@Panel
            }
            T("permission denied", color = t.err)
            Spacer(Modifier.height(12.dp))
            T("FoldTerm reads $label from your apps' notifications. It never signs in to your accounts. Grant notification access to fill this pane (and to enable the now-playing widget).", color = t.dim)
            Spacer(Modifier.height(8.dp))
            T("Switch greyed out? App info → ⋮ → Allow restricted settings, then try again.", color = t.dim, size = 12.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TButton("grant access", primary = true) { vm.openNotificationAccess() }
                TButton("open $label app") { openCategoryApp(vm, kind) }
            }
        }
        return
    }

    Adaptive(
        left = { m ->
            Panel(m, title = "$label (${list.count { !it.removed }})", trailing = {
                T(if (showAll) "all ▾" else "$label ▾", color = t.dim, size = 12.sp, modifier = Modifier.clickable { showAll = !showAll })
            }) {
                if (list.isEmpty()) {
                    Empty("no $label notifications", "open $label app") { openCategoryApp(vm, kind) }
                    return@Panel
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list.size, key = { list[it].key + list[it].time }) { i ->
                        val n = list[i]
                        val isSel = n.key == sel?.key
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) t.selection else Color.Transparent)
                                .clickable { vm.selectedNotif.value = n.key }.padding(12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!n.removed) { T("● ", color = t.accent2, size = 12.sp) }
                                T(n.title.ifBlank { n.app }, weight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f), color = if (n.removed) t.dim else t.fg)
                                T(Fmt.ago(n.time), color = t.dim, size = 12.sp)
                            }
                            T(n.app, color = t.accent3, size = 12.sp, maxLines = 1)
                            T(n.text, color = t.dim, size = 13.sp, maxLines = 2)
                        }
                    }
                }
            }
        },
        right = { m ->
            Panel(m) {
                if (sel == null) { Empty("nothing selected"); return@Panel }
                var reply by remember(sel.key) { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TButton("open", primary = true) { vm.openNotif(sel) }
                    if (!sel.removed) TButton("dismiss") { vm.dismissNotif(sel) }
                    if (!sel.removed) TButton("snooze 1h") { vm.snoozeNotif(sel, 60) }
                }
                Spacer(Modifier.height(16.dp))
                T(sel.title.ifBlank { sel.app }, size = 22.sp, weight = FontWeight.Bold, maxLines = 3)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(sel.app, 40.dp, nameColor(sel.app))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        T(sel.app, weight = FontWeight.SemiBold)
                        T(sel.pkg, color = t.dim, size = 12.sp)
                    }
                    T("${Fmt.dayLabel(sel.time)}, ${Fmt.time(sel.time)}", color = t.dim, size = 13.sp)
                }
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(t.panelAlt)
                        .border(1.dp, t.border, RoundedCornerShape(16.dp)).padding(18.dp),
                ) {
                    T(sel.body, size = 15.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                if (sel.replyAction != null && !sel.removed) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Field(reply, { reply = it }, "reply…", Modifier.weight(1f), onDone = {
                            if (reply.isNotBlank() && vm.replyNotif(sel, reply)) reply = ""
                        })
                        Spacer(Modifier.width(8.dp))
                        TButton("send", primary = true, enabled = reply.isNotBlank()) {
                            if (vm.replyNotif(sel, reply)) reply = ""
                        }
                    }
                }
            }
        },
    )
}

private fun openCategoryApp(vm: LauncherViewModel, kind: NotifKind) {
    val cat = if (kind == NotifKind.MAIL) Intent.CATEGORY_APP_EMAIL else Intent.CATEGORY_APP_MESSAGING
    vm.start(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat))
}

// ───────────────────────── people ─────────────────────────

@Composable
fun PeopleScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val ice by vm.prefs.ice.collectAsStateWithLifecycle()
    val detail by vm.selectedContact.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val groups by vm.contactGroups.collectAsStateWithLifecycle()

    LaunchedEffect(contacts) {
        if (vm.selectedContact.value == null) contacts.firstOrNull { it.id in ice }?.let { vm.selectContact(it) }
            ?: contacts.firstOrNull()?.let { vm.selectContact(it) }
    }

    if (perms["contacts"] != true) {
        Panel(Modifier.fillMaxSize(), title = "people") { Empty("contacts permission denied", "grant") { vm.requestPermission("contacts") } }
        return
    }

    Adaptive(
        leftFraction = 0.48f,
        left = { m ->
            Panel(m) {
                Tabs(listOf("people", "favorites", "companies", "groups"), tab, { tab = it; if (it == 3) vm.loadGroups() })
                Spacer(Modifier.height(10.dp))
                Field(filter, { filter = it }, "grep contacts…", Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                val shown = contacts.filter {
                    filter.isBlank() || it.name.contains(filter, true) || (it.company?.contains(filter, true) == true) ||
                        (it.phone?.filter { c -> c.isDigit() }?.contains(filter.filter { c -> c.isDigit() }.ifEmpty { "\u0000" }) == true)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    fun header(text: String, color: Color) = item(key = "h_$text") {
                        T(text, color = color, weight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                    }
                    when (tab) {
                        0 -> {
                            val iceList = shown.filter { it.id in ice }
                            if (iceList.isNotEmpty() && filter.isBlank()) {
                                header("In case of emergency", t.err)
                                iceList.forEach { c -> item(key = "ice${c.id}") { ContactRow(vm, c, detail?.summary?.id == c.id, ice = true) } }
                            }
                            val favs = shown.filter { it.starred }
                            if (favs.isNotEmpty() && filter.isBlank()) {
                                header("Favorites", t.dim)
                                favs.forEach { c -> item(key = "fav${c.id}") { ContactRow(vm, c, detail?.summary?.id == c.id) } }
                            }
                            shown.groupBy { it.name.first().uppercaseChar().let { ch -> if (ch.isLetter()) ch else '#' } }.forEach { (letter, group) ->
                                header(letter.toString(), t.dim)
                                group.forEach { c -> item(key = "c${c.id}") { ContactRow(vm, c, detail?.summary?.id == c.id) } }
                            }
                        }
                        1 -> shown.filter { it.starred }.forEach { c -> item(key = "f${c.id}") { ContactRow(vm, c, detail?.summary?.id == c.id) } }
                        2 -> shown.filter { it.company != null }.groupBy { it.company!! }.toSortedMap(String.CASE_INSENSITIVE_ORDER).forEach { (co, group) ->
                            header(co, t.accent2)
                            group.forEach { c -> item(key = "co${c.id}") { ContactRow(vm, c, detail?.summary?.id == c.id) } }
                        }
                        else -> {
                            if (groups.isEmpty()) item(key = "nogroups") { Empty("no contact groups — create labels in your contacts app") }
                            groups.forEach { (title, ids) ->
                                val members = shown.filter { it.id in ids }
                                if (members.isNotEmpty()) {
                                    header("$title (${members.size})", t.accent2)
                                    members.forEach { c -> item(key = "gr${title}_${c.id}") { ContactRow(vm, c, detail?.summary?.id == c.id) } }
                                }
                            }
                        }
                    }
                }
            }
        },
        right = { m -> ContactDetailPanel(vm, m, ice) },
    )
}

@Composable
private fun ContactRow(vm: LauncherViewModel, c: ContactSummary, selected: Boolean, ice: Boolean = false) {
    val t = LocalTerm.current
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
            .background(if (selected) t.selection else t.panelAlt)
            .border(1.dp, if (ice) t.err.copy(alpha = 0.5f) else t.border, shape)
            .clickable { vm.selectContact(c) }.padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.name, 40.dp, nameColor(c.name), photo = c.photo)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(c.name, weight = FontWeight.SemiBold, size = 15.sp, maxLines = 1)
            T(c.company ?: c.phone ?: c.email ?: "", color = t.dim, size = 13.sp, maxLines = 1)
        }
        val phone = c.phone
        val email = c.email
        if (phone != null) {
            RoundGlyph("☏") { vm.dial(phone) }
            RoundGlyph("✉") { vm.sms(phone) }
        }
        if (email != null) RoundGlyph("@") { vm.email(email) }
    }
}

@Composable
private fun RoundGlyph(glyph: String, onClick: () -> Unit) {
    val t = LocalTerm.current
    Box(
        Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape).background(t.chip).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { T(glyph, size = 15.sp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactDetailPanel(vm: LauncherViewModel, modifier: Modifier, ice: Set<Long>) {
    val t = LocalTerm.current
    val d by vm.selectedContact.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Panel(modifier) {
        val detail = d
        if (detail == null) { Empty("select a contact"); return@Panel }
        val s = detail.summary
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(s.name, 96.dp, nameColor(s.name), photo = s.photo)
                Spacer(Modifier.width(20.dp))
                Column {
                    T(s.name, size = 28.sp, weight = FontWeight.Bold, maxLines = 2)
                    detail.company?.let { T(it + (detail.jobTitle?.let { j -> " · $j" } ?: ""), color = t.dim, size = 15.sp) }
                    if (s.id in ice) { Spacer(Modifier.height(6.dp)); Chip("In case of emergency", t.err) }
                }
            }
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.phones.firstOrNull()?.let { p ->
                    TButton("☏ call") { vm.dial(p) }
                    TButton("✉ message") { vm.sms(p) }
                }
                detail.emails.firstOrNull()?.let { e -> TButton("@ email") { vm.email(e) } }
                if (vm.allApps.value.any { it.packageName == "com.whatsapp" || it.packageName == "com.whatsapp.w4b" }) {
                    detail.phones.firstOrNull()?.let { p -> TButton("whatsapp") { vm.whatsapp(p) } }
                }
                TButton("share") { vm.start(vm.contactsRepo.shareIntent(s)) }
                TButton(if (s.id in ice) "remove ICE" else "mark ICE") { vm.prefs.toggleIce(s.id) }
                TButton("edit", primary = true) { vm.start(vm.contactsRepo.editIntent(s)) }
            }
            Spacer(Modifier.height(16.dp))
            Tabs(listOf("profile", "raw"), tab, { tab = it })
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(t.panelAlt)
                    .border(1.dp, t.border, RoundedCornerShape(16.dp)).padding(18.dp),
            ) {
                if (tab == 0) {
                    T("contact", weight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (detail.phones.isNotEmpty()) KV("phone", detail.phones.joinToString("\n"))
                    if (detail.emails.isNotEmpty()) KV("email", detail.emails.joinToString("\n"))
                    detail.company?.let { KV("company", it) }
                    detail.birthday?.let { KV("birthday", it) }
                    if (detail.addresses.isNotEmpty()) KV("address", detail.addresses.joinToString("\n"))
                    detail.note?.let { KV("note", it) }
                    KV("saved in", if (detail.accounts.isEmpty()) "only on this phone" else detail.accounts.joinToString(" + "))
                } else {
                    T("$ cat ${s.lookupKey.take(16)}.vcf", color = t.accent)
                    Spacer(Modifier.height(6.dp))
                    val lines = buildList {
                        add("BEGIN:VCARD"); add("FN:${s.name}")
                        detail.phones.forEach { add("TEL:$it") }
                        detail.emails.forEach { add("EMAIL:$it") }
                        detail.company?.let { add("ORG:$it") }
                        detail.birthday?.let { add("BDAY:$it") }
                        detail.addresses.forEach { add("ADR:$it") }
                        add("END:VCARD")
                    }
                    lines.forEach { T(it, size = 13.sp, color = t.fg) }
                }
            }
        }
    }
}

// ───────────────────────── photos ─────────────────────────

@Composable
fun PhotosScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val photos by vm.photos.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    var album by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Int?>(null) }
    val albums = remember(photos) { photos.groupingBy { it.album }.eachCount().entries.filter { it.key.isNotBlank() }.sortedByDescending { it.value }.take(8).map { it.key } }
    val shown = remember(photos, album) { if (album == null) photos else photos.filter { it.album == album } }
    Box(Modifier.fillMaxSize()) {
        Panel(Modifier.fillMaxSize(), title = "photos", trailing = { T("${shown.size}${if (album == null) " recent" else " in $album"}", color = t.dim, size = 12.sp) }) {
            if (perms["photos"] != true) { Empty("photo access denied", "grant") { vm.requestPermission("photos") }; return@Panel }
            if (photos.isEmpty()) { Empty("no photos found"); return@Panel }
            if (albums.size > 1) {
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    item { AlbumChip("all", album == null) { album = null } }
                    items(albums.size) { i -> AlbumChip(albums[i], album == albums[i]) { album = albums[i] } }
                }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(150.dp), modifier = Modifier.fillMaxSize()) {
                items(shown.size, key = { shown[it].id }) { i ->
                    val p = shown[i]
                    Box(Modifier.padding(4.dp).aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable { viewing = i }) {
                        AsyncBitmap(p.id, Modifier.fillMaxSize()) { vm.deviceRepo.thumbnail(p) }
                        val day = Fmt.dayLabel(p.dateMs)
                        if (i == 0 || Fmt.dayLabel(shown[i - 1].dateMs) != day) {
                            Box(Modifier.padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                T(day, color = Color.White, size = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        val start = viewing
        if (start != null && start in shown.indices) PhotoViewer(vm, shown, start) { viewing = null }
    }
}

@Composable
private fun AlbumChip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = LocalTerm.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) t.button else t.chip).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) { T(label, color = if (on) t.onButton else t.dim, size = 12.sp, maxLines = 1) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PhotoViewer(vm: LauncherViewModel, photos: List<dev.foldterm.launcher.data.Photo>, start: Int, onClose: () -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = start) { photos.size }
    androidx.activity.compose.BackHandler(onBack = onClose)
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)).background(Color.Black)) {
        androidx.compose.foundation.pager.HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            val p = photos[page]
            val bmp by produceState<android.graphics.Bitmap?>(null, p.id) {
                value = withContext(Dispatchers.IO) { vm.deviceRepo.large(p) }
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val b = bmp
                if (b != null) androidx.compose.foundation.Image(
                    b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                ) else T("loading…", color = Color.White.copy(alpha = 0.6f))
            }
        }
        val cur = photos.getOrNull(pager.currentPage)
        Row(
            Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                if (cur != null) T("${Fmt.dayLabel(cur.dateMs)} · ${Fmt.time(cur.dateMs)}", color = Color.White, weight = FontWeight.SemiBold, maxLines = 1)
                T("${pager.currentPage + 1} / ${photos.size}${cur?.album?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""}", color = Color.White.copy(alpha = 0.7f), size = 12.sp, maxLines = 1)
            }
            if (cur != null) {
                ViewerButton("share") {
                    vm.start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/*").putExtra(Intent.EXTRA_STREAM, cur.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "share photo"))
                }
                ViewerButton("open") { vm.start(Intent(Intent.ACTION_VIEW).setDataAndType(cur.uri, "image/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
            }
            ViewerButton("✕", onClose)
        }
    }
}

@Composable
private fun ViewerButton(label: String, onClick: () -> Unit) {
    T(label, color = Color.White, size = 14.sp, weight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp))
}

// ───────────────────────── health / vitals ─────────────────────────

@Composable
fun HealthScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val steps by vm.steps.collectAsStateWithLifecycle()
    val goal by vm.prefs.stepGoal.collectAsStateWithLifecycle()
    val vitals by vm.vitals.collectAsStateWithLifecycle()
    val fold by vm.fold.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.vitals.value = vm.deviceRepo.vitals() }
    Adaptive(
        leftFraction = 0.5f,
        left = { m ->
            Panel(m, title = "activity") {
                val s = steps.steps
                when {
                    !steps.available -> Empty("no step counter sensor")
                    !steps.permitted -> Empty("physical activity permission denied", "grant") { vm.requestPermission("activity") }
                    else -> {
                        T("%,d".format(s ?: 0), size = 56.sp, weight = FontWeight.Bold, color = t.accent)
                        T("steps today · goal %,d".format(goal), color = t.dim)
                        Spacer(Modifier.height(14.dp))
                        T("[${Fmt.bar((s ?: 0).toFloat() / goal, 24)}] ${((s ?: 0) * 100 / goal.coerceAtLeast(1))}%", color = t.accent)
                        Spacer(Modifier.height(10.dp))
                        T("≈ %.1f km walked".format((s ?: 0) * 0.00076), color = t.fg)
                        Spacer(Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(6000, 8000, 10000, 12000).forEach { g -> TButton(g.toString(), primary = g == goal) { vm.prefs.setStepGoal(g) } }
                        }
                    }
                }
                val history = remember(steps.steps) { vm.prefs.stepHistory() }
                if (history.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    T("last days", color = t.dim, size = 12.sp)
                    val days = history.toSortedMap().entries.toList().takeLast(6).map { it.key.takeLast(5) to it.value } + ("today" to (steps.steps ?: 0))
                    val max = days.maxOf { it.second }.coerceAtLeast(goal)
                    days.forEach { (d, n) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(d, color = t.dim, size = 12.sp, modifier = Modifier.width(56.dp))
                            T(Fmt.bar(n.toFloat() / max, 16), color = if (n >= goal) t.ok else t.accent, size = 12.sp, maxLines = 1)
                            T("  %,d".format(n), size = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                T("Counted on-device from the step sensor since the first reading today. Sleep and heart data live in your health app.", color = t.dim, size = 12.sp)
            }
        },
        right = { m ->
            Panel(m, title = "htop", trailing = { T("↻", color = t.accent, size = 18.sp, modifier = Modifier.clickable { vm.vitals.value = vm.deviceRepo.vitals() }) }) {
                val v = vitals
                if (v == null) { Empty("reading…"); return@Panel }
                VitalBar("battery", v.battery / 100f, "${v.battery}%${if (v.charging) " ⚡" else ""}", if (v.battery < 20) t.err else t.accent)
                VitalBar("storage", (1 - v.storageFreeGb / v.storageTotalGb).toFloat(), "${Fmt.gb(v.storageTotalGb - v.storageFreeGb)}/${Fmt.gb(v.storageTotalGb)}", t.accent2)
                VitalBar("memory", (1 - v.ramAvailGb / v.ramTotalGb).toFloat(), "${Fmt.gb(v.ramTotalGb - v.ramAvailGb)}/${Fmt.gb(v.ramTotalGb)}", t.accent3)
                Divider()
                KV("temp", "${v.tempC}°C")
                KV("uptime", Fmt.duration(v.uptimeMs))
                KV("posture", fold.label + (fold.hinge?.let { " · hinge ${it.width()}×${it.height()}px" } ?: ""))
                KV("build", "foldterm ${BuildConfig.VERSION_NAME}")
            }
        },
    )
}

@Composable
private fun VitalBar(label: String, frac: Float, value: String, color: Color) {
    val t = LocalTerm.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, color = t.dim, modifier = Modifier.width(80.dp))
        T("[", color = t.dim)
        T(Fmt.bar(frac, 18), color = color, maxLines = 1)
        T("]", color = t.dim)
        Spacer(Modifier.width(10.dp))
        T(value, maxLines = 1)
    }
}

// ───────────────────────── news ─────────────────────────

@Composable
fun NewsScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val news by vm.news.collectAsStateWithLifecycle()
    val status by vm.newsStatus.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val read by vm.newsRead.collectAsStateWithLifecycle()
    val saved by vm.newsSaved.collectAsStateWithLifecycle()
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var savedTab by rememberSaveable { mutableStateOf(false) }
    var hideRead by rememberSaveable { mutableStateOf(false) }
    val sources = remember(news) { news.map { it.source }.distinct() }
    val shown = if (savedTab) saved else news.filter { (source == null || it.source == source) && (!hideRead || it.link !in read) }
    val unread = news.count { it.link !in read }
    Panel(Modifier.fillMaxSize(), title = "news · $unread unread", trailing = { T(status, color = t.dim, size = 12.sp) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tabs(listOf("stories", "saved (${saved.size})"), if (savedTab) 1 else 0, { savedTab = it == 1 })
            Spacer(Modifier.weight(1f))
            if (!savedTab) {
                T(if (hideRead) "show read" else "hide read", color = t.dim, size = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { hideRead = !hideRead }.padding(6.dp))
                T("mark all read", color = t.dim, size = 12.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.markAllRead(news) }.padding(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!savedTab && sources.size > 1) {
            Tabs(listOf("all") + sources.map { it.take(18) }, if (source == null) 0 else sources.indexOf(source) + 1, { i ->
                source = if (i == 0) null else sources[i - 1]
            })
            Spacer(Modifier.height(10.dp))
        }
        if (shown.isEmpty()) {
            if (savedTab) Empty("nothing saved — tap ☆ on a story") else Empty(status.ifBlank { "no headlines" }, "refresh") { vm.reloadNews() }
            return@Panel
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown.size, key = { shown[it].link + it }) { i ->
                val n = shown[i]
                val isRead = n.link in read
                val isSaved = saved.any { it.link == n.link }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.openStory(n) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                ) {
                    T(if (isRead) "  " else "● ", color = t.accent2, size = 12.sp, modifier = Modifier.padding(top = 3.dp))
                    Column(Modifier.weight(1f)) {
                        T(n.title, size = 16.sp, weight = if (isRead) FontWeight.Normal else FontWeight.SemiBold, maxLines = 3, color = if (isRead) t.dim else t.fg)
                        T("${n.source}${n.published?.let { " · ${Fmt.ago(it, now)}" } ?: ""}", color = t.accent3, size = 12.sp)
                    }
                    T(if (isSaved) "★" else "☆", color = if (isSaved) t.warn else t.dim, size = 18.sp,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.toggleSaved(n) }.padding(8.dp))
                }
            }
        }
    }
}

// ───────────────────────── settings ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val theme by vm.prefs.theme.collectAsStateWithLifecycle()
    val perms by vm.perms.collectAsStateWithLifecycle()
    val access by vm.notifAccess.collectAsStateWithLifecycle()
    val isHome by vm.isDefaultHome.collectAsStateWithLifecycle()
    val fahrenheit by vm.prefs.fahrenheit.collectAsStateWithLifecycle()
    val feedsPref by vm.prefs.feeds.collectAsStateWithLifecycle()
    val namePref by vm.prefs.userName.collectAsStateWithLifecycle()
    val locPref by vm.prefs.manualLocation.collectAsStateWithLifecycle()
    val iconStyle by vm.prefs.iconStyle.collectAsStateWithLifecycle()
    val wallpaper by vm.prefs.showWallpaper.collectAsStateWithLifecycle()
    val dim by vm.prefs.wallpaperDim.collectAsStateWithLifecycle()
    val iconTint by vm.prefs.iconTint.collectAsStateWithLifecycle()
    val customSpec by vm.prefs.customTheme.collectAsStateWithLifecycle()
    val engine by vm.prefs.searchEngine.collectAsStateWithLifecycle()
    val keyClicks by vm.prefs.keyClicks.collectAsStateWithLifecycle()
    val summaryHidden by vm.prefs.summaryHidden.collectAsStateWithLifecycle()
    val autoTheme by vm.prefs.autoTheme.collectAsStateWithLifecycle()
    val dayTheme by vm.prefs.dayTheme.collectAsStateWithLifecycle()
    val nightTheme by vm.prefs.nightTheme.collectAsStateWithLifecycle()
    val textScale by vm.prefs.textScale.collectAsStateWithLifecycle()
    val morning by vm.prefs.morningBrief.collectAsStateWithLifecycle()
    val iconPack by vm.prefs.iconPack.collectAsStateWithLifecycle()
    var customTint by remember(iconTint) { mutableStateOf(if (iconTint.startsWith("#")) iconTint else "") }
    val hiddenKeys by vm.prefs.hidden.collectAsStateWithLifecycle()
    val sectionOrder by vm.prefs.sectionOrder.collectAsStateWithLifecycle()
    val coverMode by vm.prefs.coverMode.collectAsStateWithLifecycle()
    val gUp by vm.prefs.gestureUp.collectAsStateWithLifecycle()
    val gDown by vm.prefs.gestureDown.collectAsStateWithLifecycle()
    val gDouble by vm.prefs.gestureDouble.collectAsStateWithLifecycle()
    val ctlTick by vm.controlsTick.collectAsStateWithLifecycle()
    val lockEnabled = remember(ctlTick) { dev.foldterm.launcher.data.LockAdmin.enabled(vm.getApplication()) }
    val homeLayout by vm.prefs.homeLayout.collectAsStateWithLifecycle()
    val coverCards by vm.prefs.coverCards.collectAsStateWithLifecycle()
    val hiddenSections by vm.prefs.hiddenSections.collectAsStateWithLifecycle()
    val allApps by vm.allApps.collectAsStateWithLifecycle()
    val hiddenApps = remember(hiddenKeys, allApps) { allApps.filter { it.key in hiddenKeys } }
    var feeds by remember(feedsPref) { mutableStateOf(feedsPref) }
    var name by remember(namePref) { mutableStateOf(namePref) }
    var loc by remember(locPref) { mutableStateOf(locPref) }
    val fold = LocalFold.current

    Adaptive(
        leftFraction = 0.5f,
        left = { m ->
            Panel(m, title = "system") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    T("theme", weight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        (Themes.all + Themes.custom(customSpec)).forEach { th ->
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(th.bg)
                                    .border(2.dp, if (th.name == theme) th.accent else th.border, RoundedCornerShape(50))
                                    .clickable { vm.prefs.setTheme(th.name) }.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(th.accent))
                                Spacer(Modifier.width(8.dp))
                                T(th.name, color = th.fg, size = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("auto theme", modifier = Modifier.width(110.dp))
                        Tabs(listOf("off", "day / night"), if (autoTheme) 1 else 0, { vm.prefs.setAutoTheme(it == 1) })
                    }
                    if (autoTheme) {
                        listOf(Triple("day", dayTheme, { n: String -> vm.prefs.setDayTheme(n) }), Triple("night", nightTheme, { n: String -> vm.prefs.setNightTheme(n) })).forEach { (label, cur, set) ->
                            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                T(label, color = t.dim, size = 12.sp, modifier = Modifier.width(56.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Themes.names.forEach { n ->
                                        Box(
                                            Modifier.clip(RoundedCornerShape(50)).background(if (n == cur) t.button else t.chip).clickable { set(n) }
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                        ) { T(n, color = if (n == cur) t.onButton else t.dim, size = 12.sp) }
                                    }
                                }
                            }
                        }
                        T("switches at sunrise and sunset from the forecast (07:00/19:00 without weather)", color = t.dim, size = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                    if (theme == "custom") {
                        Spacer(Modifier.height(10.dp))
                        val parts = customSpec.split('|')
                        fun setPart(i: Int, v: String) {
                            val p = (parts + List(4) { "" }).take(4).toMutableList()
                            p[i] = v
                            vm.prefs.setCustomTheme(p.joinToString("|"))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T("base", modifier = Modifier.width(110.dp))
                            Tabs(listOf("dark", "light"), if (parts.getOrNull(0) == "light") 1 else 0, { i ->
                                setPart(0, if (i == 1) "light" else "dark")
                                setPart(2, if (i == 1) "#F4F1FA" else "#0A0D12")
                            })
                        }
                        val accents = listOf("#7EE787", "#33FF66", "#FFB000", "#FF7EB6", "#79C0FF", "#00E5FF", "#D2A8FF", "#FF7B72", "#4B3FD9", "#1F5C46")
                        val darkBgs = listOf("#0A0D12", "#000000", "#120A14", "#0B1320", "#101010", "#14100A", "#0E1A12")
                        val lightBgs = listOf("#F4F1FA", "#FFFFFF", "#EDE6D6", "#E8F1FB", "#FBEFF4", "#EEF5EC")
                        listOf(
                            Triple(1, "accent", accents),
                            Triple(3, "accent 2", accents),
                            Triple(2, "background", if (parts.getOrNull(0) == "light") lightBgs else darkBgs),
                        ).forEach { (idx, label, swatches) ->
                            T(label, color = t.dim, size = 12.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                swatches.forEach { hex ->
                                    val c = parseHexColor(hex)!!
                                    val sel = parts.getOrNull(idx).equals(hex, ignoreCase = true)
                                    Box(
                                        Modifier.size(30.dp).clip(CircleShape).background(c)
                                            .border(if (sel) 3.dp else 1.dp, if (sel) t.fg else t.border, CircleShape)
                                            .clickable { setPart(idx, hex) },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        T("or type colours: base|#accent|#background|#accent2", color = t.dim, size = 11.sp)
                        var specDraft by remember(customSpec) { mutableStateOf(customSpec) }
                        Field(specDraft, { specDraft = it }, Themes.DEFAULT_CUSTOM, Modifier.fillMaxWidth(), onDone = { vm.prefs.setCustomTheme(specDraft.trim()) })
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("text size", modifier = Modifier.width(110.dp))
                        val scales = listOf(0.9f, 1f, 1.15f, 1.3f)
                        Tabs(listOf("small", "normal", "large", "huge"), scales.indexOfFirst { kotlin.math.abs(it - textScale) < 0.01f }.coerceAtLeast(0), {
                            vm.prefs.setTextScale(scales[it])
                        })
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("icons", modifier = Modifier.width(110.dp))
                        Tabs(
                            listOf("color", "mono", "pack"),
                            when (iconStyle) { "mono" -> 1; "pack" -> 2; else -> 0 },
                            { vm.prefs.setIconStyle(listOf("color", "mono", "pack")[it]) },
                        )
                    }
                    if (iconStyle == "pack") {
                        Spacer(Modifier.height(8.dp))
                        val packs by produceState(emptyList<Pair<dev.foldterm.launcher.data.IconPacks.Pack, Int>>(), allApps) {
                            value = withContext(Dispatchers.IO) {
                                vm.iconPacks.installed().map { it to vm.iconPacks.coverage(it.pkg, allApps) }
                                    .also { vm.prefs.iconPack.value.takeIf { p -> p.isNotEmpty() }?.let { p -> vm.iconPacks.coverage(p, allApps) } }
                            }
                        }
                        if (packs.isEmpty()) {
                            T("No icon packs installed. Any standard pack from the Play Store works; Arcticons (free) suits the terminal look.", color = t.dim, size = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            TButton("find icon packs") { vm.openUrl("https://play.google.com/store/search?q=icon%20pack&c=apps") }
                        }
                        packs.forEach { (pack, cov) ->
                            val sel = pack.pkg == iconPack
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (sel) t.selection else Color.Transparent)
                                    .clickable { vm.prefs.setIconPack(pack.pkg) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(if (sel) "●" else "○", color = if (sel) t.accent else t.dim, modifier = Modifier.width(24.dp))
                                T(pack.label, modifier = Modifier.weight(1f), maxLines = 1)
                                T("$cov% of your apps", color = t.dim, size = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        T("apps the pack doesn't cover keep their own icon", color = t.dim, size = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (iconStyle == "mono") {
                        Spacer(Modifier.height(8.dp))
                        T("icon tint", color = t.dim, size = 12.sp)
                        Spacer(Modifier.height(6.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val swatches = listOf("theme") + ICON_TINTS
                            swatches.forEach { hex ->
                                val c = if (hex == "theme") t.accent else parseHexColor(hex)!!
                                val sel = iconTint.equals(hex, ignoreCase = true)
                                Box(
                                    Modifier.size(34.dp).clip(CircleShape).background(c)
                                        .border(if (sel) 3.dp else 1.dp, if (sel) t.fg else t.border, CircleShape)
                                        .clickable { vm.prefs.setIconTint(hex) },
                                    contentAlignment = Alignment.Center,
                                ) { if (hex == "theme") T("T", color = t.onButton, size = 12.sp, weight = FontWeight.Bold) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Field(customTint, { customTint = it }, "custom #RRGGBB", Modifier.weight(1f), onDone = {
                                if (parseHexColor(customTint) != null) vm.prefs.setIconTint("#" + customTint.trim().removePrefix("#"))
                            })
                            Spacer(Modifier.width(8.dp))
                            TButton("apply", enabled = parseHexColor(customTint) != null) {
                                vm.prefs.setIconTint("#" + customTint.trim().removePrefix("#"))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("background", modifier = Modifier.width(110.dp))
                        Tabs(listOf("theme", "wallpaper"), if (wallpaper) 1 else 0, { vm.prefs.setShowWallpaper(it == 1) })
                    }
                    if (wallpaper) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T("dim", modifier = Modifier.width(110.dp))
                            val levels = listOf(0f, 0.3f, 0.5f, 0.7f)
                            Tabs(listOf("none", "light", "medium", "heavy"), levels.indexOfFirst { kotlin.math.abs(it - dim) < 0.05f }.coerceAtLeast(0), {
                                vm.prefs.setWallpaperDim(levels[it])
                            })
                        }
                    }
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("sidebar", weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        T("reset", color = t.dim, size = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.prefs.resetSidebar() }.padding(6.dp))
                    }
                    T("choose which sections appear, and their order", color = t.dim, size = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    val ordered = orderedSections(sectionOrder)
                    ordered.forEachIndexed { i, sec ->
                        val locked = sec == Section.HOME
                        val on = locked || sec.id !in hiddenSections
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(if (on) t.panelAlt else Color.Transparent)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(sec.glyph, color = if (on) t.accent else t.dim, size = 18.sp, modifier = Modifier.width(30.dp))
                            T(sec.title.lowercase(), color = if (on) t.fg else t.dim, modifier = Modifier.weight(1f))
                            T("↑", color = if (i > 0) t.fg else t.dim.copy(alpha = 0.3f), size = 16.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = i > 0) { vm.moveSection(sec, -1) }.padding(horizontal = 10.dp, vertical = 6.dp))
                            T("↓", color = if (i < ordered.lastIndex) t.fg else t.dim.copy(alpha = 0.3f), size = 16.sp,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = i < ordered.lastIndex) { vm.moveSection(sec, 1) }.padding(horizontal = 10.dp, vertical = 6.dp))
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.width(64.dp).clip(RoundedCornerShape(50))
                                    .background(if (on) t.button else t.chip)
                                    .clickable(enabled = !locked) { vm.prefs.setSectionVisible(sec.id, !on) }
                                    .padding(vertical = 5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                T(if (locked) "always" else if (on) "on" else "off", color = if (on) t.onButton else t.dim, size = 12.sp, weight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    Divider()
                    T("gestures", weight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    val actions = listOf("drawer", "shade", "prompt", "alerts", "media", "lock", "torch", "none")
                    listOf(Triple("up", "swipe up", gUp), Triple("down", "swipe down", gDown), Triple("double", "double-tap", gDouble)).forEach { (which, label, cur) ->
                        T(label, color = t.dim, size = 12.sp)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                            actions.forEach { a ->
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (a == cur) t.button else t.chip)
                                        .clickable {
                                            vm.prefs.setGesture(which, a)
                                            if (a == "lock" && !lockEnabled) vm.start(dev.foldterm.launcher.data.LockAdmin.enableIntent(vm.getApplication()))
                                        }.padding(horizontal = 10.dp, vertical = 4.dp),
                                ) { T(a, color = if (a == cur) t.onButton else t.dim, size = 12.sp) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("screen lock", modifier = Modifier.width(110.dp))
                        T(if (lockEnabled) "enabled" else "off", color = if (lockEnabled) t.ok else t.dim, modifier = Modifier.weight(1f))
                        if (lockEnabled) TButton("turn off") { dev.foldterm.launcher.data.LockAdmin.disable(vm.getApplication()); vm.controlsTick.value++ }
                        else TButton("enable") { vm.start(dev.foldterm.launcher.data.LockAdmin.enableIntent(vm.getApplication())) }
                    }
                    T("uses a device-admin permission limited to locking; turn it off here before uninstalling", color = t.dim, size = 11.sp)
                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("home layout", weight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        T("reset", color = t.dim, size = 12.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable { vm.prefs.setHomeLayout(listOf("happening:L", "agenda:L", "summary:R")) }.padding(6.dp))
                    }
                    T("cards on the unfolded Home: order, column (L/R) and on/off", color = t.dim, size = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    T("summary rows", color = t.dim, size = 12.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        SUMMARY_ROWS.forEach { r ->
                            val on = r !in summaryHidden
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(if (on) t.button else t.chip).clickable { vm.prefs.toggleSummaryRow(r) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) { T(r.lowercase(), color = if (on) t.onButton else t.dim, size = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    run {
                        val enabled = homeLayout
                        val ids = enabled.map { it.substringBefore(':') }
                        val rows = ids.mapNotNull { id -> HOME_CARDS.firstOrNull { it.first == id } } + HOME_CARDS.filter { it.first !in ids }
                        rows.forEach { (id, label) ->
                            val idx = ids.indexOf(id)
                            val on = idx >= 0
                            val col = if (on) enabled[idx].substringAfter(':') else "R"
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) t.panelAlt else Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(label, color = if (on) t.fg else t.dim, modifier = Modifier.weight(1f), maxLines = 1)
                                if (on) {
                                    T("↑", size = 16.sp, color = if (idx > 0) t.fg else t.dim.copy(alpha = 0.3f),
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = idx > 0) {
                                            vm.prefs.setHomeLayout(enabled.toMutableList().apply { add(idx - 1, removeAt(idx)) })
                                        }.padding(horizontal = 8.dp, vertical = 6.dp))
                                    T("↓", size = 16.sp, color = if (idx < enabled.lastIndex) t.fg else t.dim.copy(alpha = 0.3f),
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = idx < enabled.lastIndex) {
                                            vm.prefs.setHomeLayout(enabled.toMutableList().apply { add(idx + 1, removeAt(idx)) })
                                        }.padding(horizontal = 8.dp, vertical = 6.dp))
                                    T(col, size = 13.sp, weight = FontWeight.Bold, color = t.accent2,
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).border(1.dp, t.border, RoundedCornerShape(8.dp))
                                            .clickable { vm.prefs.setHomeLayout(enabled.toMutableList().apply { set(idx, "$id:${if (col == "L") "R" else "L"}") }) }
                                            .padding(horizontal = 10.dp, vertical = 4.dp))
                                    Spacer(Modifier.width(6.dp))
                                }
                                Box(
                                    Modifier.width(56.dp).clip(RoundedCornerShape(50)).background(if (on) t.button else t.chip)
                                        .clickable { vm.prefs.setHomeLayout(if (on) enabled.filterIndexed { i, _ -> i != idx } else enabled + "$id:R") }
                                        .padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) { T(if (on) "on" else "off", color = if (on) t.onButton else t.dim, size = 12.sp, weight = FontWeight.Bold) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    Divider()
                    T("cover screen", weight = FontWeight.Bold)
                    T("what Home shows when the phone is folded", color = t.dim, size = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    Tabs(listOf("cover cards", "classic home"), if (coverMode) 0 else 1, { vm.prefs.setCoverMode(it == 0) })
                    if (coverMode) {
                        Spacer(Modifier.height(6.dp))
                        val enabled = coverCards
                        val rows = enabled.mapNotNull { id -> COVER_CARDS.firstOrNull { it.first == id } } + COVER_CARDS.filter { it.first !in enabled }
                        rows.forEach { (id, label) ->
                            val on = id in enabled
                            val idx = enabled.indexOf(id)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) t.panelAlt else Color.Transparent)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                T(label, color = if (on) t.fg else t.dim, modifier = Modifier.weight(1f))
                                if (on) {
                                    T("↑", size = 16.sp, color = if (idx > 0) t.fg else t.dim.copy(alpha = 0.3f),
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = idx > 0) {
                                            vm.prefs.setCoverCards(enabled.toMutableList().apply { add(idx - 1, removeAt(idx)) })
                                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                                    T("↓", size = 16.sp, color = if (idx < enabled.lastIndex) t.fg else t.dim.copy(alpha = 0.3f),
                                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = idx < enabled.lastIndex) {
                                            vm.prefs.setCoverCards(enabled.toMutableList().apply { add(idx + 1, removeAt(idx)) })
                                        }.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                                Box(
                                    Modifier.width(56.dp).clip(RoundedCornerShape(50)).background(if (on) t.button else t.chip)
                                        .clickable { vm.prefs.setCoverCards(if (on) enabled - id else enabled + id) }.padding(vertical = 5.dp),
                                    contentAlignment = Alignment.Center,
                                ) { T(if (on) "on" else "off", color = if (on) t.onButton else t.dim, size = 12.sp, weight = FontWeight.Bold) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                    if (hiddenApps.isNotEmpty()) {
                        Divider()
                        T("hidden apps", weight = FontWeight.Bold)
                        hiddenApps.forEach { app ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                T(app.label, modifier = Modifier.weight(1f), maxLines = 1)
                                TButton("unhide") { vm.setHidden(app, false) }
                            }
                        }
                    }
                    Divider()
                    T("permissions", weight = FontWeight.Bold)
                    perms.forEach { (k, v) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(k, modifier = Modifier.width(110.dp))
                            T(if (v) "granted" else "denied", color = if (v) t.ok else t.err, modifier = Modifier.weight(1f))
                            if (!v) TButton("grant") { vm.requestPermission(k) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("notifs", modifier = Modifier.width(110.dp))
                        if (BuildConfig.NOTIFICATIONS) {
                            T(if (access) "granted" else "denied", color = if (access) t.ok else t.err, modifier = Modifier.weight(1f))
                            TButton(if (access) "manage" else "grant") { vm.openNotificationAccess() }
                        } else {
                            T("lite edition", color = t.dim, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        T("home app", modifier = Modifier.width(110.dp))
                        T(if (isHome) "foldterm" else "other", color = if (isHome) t.ok else t.warn, modifier = Modifier.weight(1f))
                        if (!isHome) TButton("set default", primary = true) { vm.requestDefaultHome() }
                    }
                    Divider()
                    T("device", weight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TButton("android settings") { vm.execShell("settings") }
                        TButton("wifi") { vm.execShell("settings wifi") }
                        TButton("display") { vm.execShell("settings display") }
                        TButton("wallpaper & home") { vm.execShell("settings home") }
                        TButton("reset dock") { vm.prefs.setDock(null) }
                        TButton("screen saver") { vm.execShell("screensaver") }
                        TButton("reload data") { vm.execShell("reload") }
                    }
                    Spacer(Modifier.height(12.dp))
                    KV("posture", fold.label)
                    KV("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                }
            }
        },
        right = { m ->
            Panel(m, title = "~/.foldtermrc") {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    T("name", color = t.dim)
                    Field(name, { name = it }, "used in the greeting", Modifier.fillMaxWidth(), onDone = { vm.prefs.setUserName(name.trim()) })
                    Spacer(Modifier.height(12.dp))
                    T("weather units", color = t.dim)
                    Spacer(Modifier.height(4.dp))
                    Tabs(listOf("°C", "°F"), if (fahrenheit) 1 else 0, { vm.prefs.setFahrenheit(it == 1); vm.reloadWeather() })
                    Spacer(Modifier.height(12.dp))
                    T("location override (lat,lon — blank uses device location)", color = t.dim)
                    Field(loc, { loc = it }, "e.g. 39.74,-104.99", Modifier.fillMaxWidth(), onDone = { vm.prefs.setManualLocation(loc.trim()); vm.reloadWeather() })
                    Spacer(Modifier.height(12.dp))
                    T("news feeds (one RSS/Atom url per line)", color = t.dim)
                    Field(feeds, { feeds = it }, "https://…", Modifier.fillMaxWidth().height(140.dp), singleLine = false)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TButton("save", primary = true) {
                            vm.prefs.setUserName(name.trim())
                            vm.prefs.setManualLocation(loc.trim())
                            vm.prefs.setFeeds(feeds.trim())
                            vm.reloadNews(); vm.reloadWeather()
                            vm.out("~/.foldtermrc saved", dev.foldterm.launcher.shell.LineKind.OK)
                        }
                        TButton("defaults") { feeds = dev.foldterm.launcher.data.Prefs.DEFAULT_FEEDS }
                    }
                    Divider()
                    T("web search", weight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system", "duckduckgo", "google", "brave", "startpage", "kagi").forEach { e ->
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(if (e == engine) t.button else t.chip)
                                    .clickable { vm.prefs.setSearchEngine(e) }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { T(e, color = if (e == engine) t.onButton else t.dim, size = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("morning brief", modifier = Modifier.weight(1f))
                        Tabs(listOf("off", "on"), if (morning) 1 else 0, { vm.prefs.setMorningBrief(it == 1) })
                    }
                    T("prints your day the first time you open FoldTerm each morning; `brief` any time", color = t.dim, size = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("key clicks", modifier = Modifier.weight(1f))
                        Tabs(listOf("off", "on"), if (keyClicks) 1 else 0, { vm.prefs.setKeyClicks(it == 1) })
                    }
                    T("a soft click on each keystroke in the prompt (follows the system touch-sounds setting)", color = t.dim, size = 11.sp)
                    Divider()
                    T("backup", weight = FontWeight.Bold)
                    T("save every setting (themes, layouts, notes, tasks, pairs, aliases…) to a file, or restore one", color = t.dim, size = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TButton("back up…", primary = true) { vm.uiRequests.tryEmit(dev.foldterm.launcher.UiRequest.ExportSettings) }
                        TButton("restore…") { vm.uiRequests.tryEmit(dev.foldterm.launcher.UiRequest.ImportSettings) }
                    }
                }
            }
        },
    )
}
