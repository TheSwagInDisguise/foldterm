package dev.foldterm.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.Section
import dev.foldterm.launcher.data.AppEntry
import dev.foldterm.launcher.data.NowPlayingStore
import dev.foldterm.launcher.data.Posture
import dev.foldterm.launcher.shell.LineKind
import dev.foldterm.launcher.shell.SuggestionKind
import dev.foldterm.launcher.ui.screens.CalendarScreen
import dev.foldterm.launcher.ui.screens.HealthScreen
import dev.foldterm.launcher.ui.screens.HomeScreen
import dev.foldterm.launcher.ui.screens.MessagesScreen
import dev.foldterm.launcher.ui.screens.NewsScreen
import dev.foldterm.launcher.ui.screens.PeopleScreen
import dev.foldterm.launcher.ui.screens.PhotosScreen
import dev.foldterm.launcher.ui.screens.SettingsScreen
import dev.foldterm.launcher.data.NotifKind

@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val themeName by vm.prefs.theme.collectAsStateWithLifecycle()
    val fold by vm.fold.collectAsStateWithLifecycle()
    val colors = Themes.byName(themeName)
    TermTheme(colors) {
        CompositionLocalProvider(LocalFold provides fold) {
            LauncherScaffold(vm)
        }
    }
}

@Composable
private fun LauncherScaffold(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val section by vm.section.collectAsStateWithLifecycle()
    val shellOpen by vm.shellOpen.collectAsStateWithLifecycle()
    val drawerOpen by vm.drawerOpen.collectAsStateWithLifecycle()
    val fold by vm.fold.collectAsStateWithLifecycle()
    val promptFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var promptBottom by remember { mutableIntStateOf(0) }
    var rootTop by remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        if (vm.onBack()) { focusManager.clearFocus(); keyboard?.hide() }
    }
    LaunchedEffect(shellOpen) {
        if (!shellOpen) { focusManager.clearFocus(); keyboard?.hide() }
    }

    val wallpaper by vm.prefs.showWallpaper.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            .background(if (wallpaper) t.bg.copy(alpha = if (t.dark) 0.55f else 0.35f) else t.bg)
            .pointerInput(Unit) {
                // swipe up: app drawer · swipe down: notification shade (or close the drawer)
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        when {
                            vm.shellOpen.value -> Unit
                            total < -threshold && !vm.drawerOpen.value -> vm.drawerOpen.value = true
                            total > threshold && vm.drawerOpen.value -> vm.drawerOpen.value = false
                            total > threshold -> vm.expandNotifications()
                        }
                    },
                ) { _, dy -> total += dy }
            }
            .drawBehind {
                // soft colour washes, echoing the gradient of the mock-ups
                drawRect(Brush.radialGradient(listOf(t.glowA, Color.Transparent), Offset(0f, 0f), size.maxDimension * 0.7f))
                drawRect(Brush.radialGradient(listOf(t.glowB, Color.Transparent), Offset(size.width, 0f), size.maxDimension * 0.6f))
                drawRect(Brush.radialGradient(listOf(t.glowC, Color.Transparent), Offset(size.width * 0.8f, size.height), size.maxDimension * 0.7f))
            }
            .onPreviewKeyEvent { e ->
                // hardware keyboard: "/" focuses the prompt, Esc closes overlays
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.Slash && !vm.shellOpen.value -> { vm.shellOpen.value = true; runCatching { promptFocus.requestFocus() }; true }
                    e.key == Key.Escape -> { vm.onBack(); true }
                    else -> false
                }
            }
            .onGloballyPositioned { rootTop = it.positionInWindow().y.toInt() }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout)),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 600.dp
            val hinge = fold.hinge
            val density = LocalDensity.current
            if (fold.posture == Posture.TABLETOP && hinge != null) {
                // Tabletop: content above the hinge, live console + dock below it.
                val topH = with(density) { (hinge.top - rootTop).coerceAtLeast(0).toDp() }
                val gapH = with(density) { hinge.height().toDp() }
                Column(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxWidth().height(topH).padding(horizontal = 16.dp)) {
                        TopBar(vm, section, expanded, promptFocus, Modifier.onGloballyPositioned { promptBottom = (it.positionInWindow().y + it.size.height).toInt() })
                        Row(Modifier.weight(1f).padding(bottom = 8.dp)) {
                            if (expanded) NavRail(vm, section)
                            SectionContent(vm, section, Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                    Spacer(Modifier.height(gapH))
                    Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp)) {
                        ConsoleView(vm, Modifier.weight(1f).fillMaxWidth())
                        Dock(vm, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = if (expanded) 16.dp else 10.dp)) {
                    TopBar(vm, section, expanded, promptFocus, Modifier.onGloballyPositioned { promptBottom = (it.positionInWindow().y + it.size.height).toInt() })
                    Row(Modifier.weight(1f)) {
                        if (expanded) NavRail(vm, section)
                        SectionContent(vm, section, Modifier.weight(1f).fillMaxHeight())
                    }
                    if (!expanded) CompactTabs(vm, section)
                    Dock(vm, Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 8.dp))
                }
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = fadeIn(tween(160)) + scaleIn(tween(200), initialScale = 0.96f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.98f),
            ) { AppDrawer(vm) }

            AnimatedVisibility(visible = shellOpen, enter = fadeIn(tween(120)), exit = fadeOut(tween(100))) {
                val topPad = with(density) { (promptBottom - rootTop).coerceAtLeast(0).toDp() }
                Box(
                    Modifier.fillMaxSize().padding(top = topPad)
                        .background(Color.Black.copy(alpha = if (t.dark) 0.45f else 0.18f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            vm.shellOpen.value = false; vm.input.value = ""
                        },
                ) {
                    ShellOverlay(
                        vm,
                        Modifier.align(Alignment.TopCenter).widthIn(max = 820.dp).fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp).imePadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionContent(vm: LauncherViewModel, section: Section, modifier: Modifier) {
    AnimatedContent(
        targetState = section,
        modifier = modifier,
        transitionSpec = {
            (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 40 }) togetherWith fadeOut(tween(90))
        },
        label = "section",
    ) { s ->
        when (s) {
            Section.HOME -> HomeScreen(vm)
            Section.CHAT -> MessagesScreen(vm, NotifKind.CHAT)
            Section.MAIL -> MessagesScreen(vm, NotifKind.MAIL)
            Section.CALENDAR -> CalendarScreen(vm)
            Section.PEOPLE -> PeopleScreen(vm)
            Section.PHOTOS -> PhotosScreen(vm)
            Section.HEALTH -> HealthScreen(vm)
            Section.NEWS -> NewsScreen(vm)
            Section.SETTINGS -> SettingsScreen(vm)
        }
    }
}

// ───────────────────────── top bar ─────────────────────────

@Composable
private fun TopBar(vm: LauncherViewModel, section: Section, expanded: Boolean, focus: FocusRequester, promptModifier: Modifier) {
    val t = LocalTerm.current
    val now by vm.now.collectAsStateWithLifecycle()
    val name by vm.prefs.userName.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded || section != Section.HOME) {
            Column(Modifier.widthIn(min = 140.dp).padding(end = 16.dp)) {
                if (section == Section.HOME) {
                    T(Fmt.greeting(now) + if (name.isNotBlank()) ", $name" else "", size = if (expanded) 26.sp else 18.sp, weight = FontWeight.Bold, maxLines = 1)
                    T(Fmt.longDate(now), color = t.dim, size = 13.sp)
                } else {
                    T("~/${section.id}", size = if (expanded) 26.sp else 18.sp, weight = FontWeight.Bold, maxLines = 1)
                    T(Fmt.longDate(now), color = t.dim, size = 13.sp)
                }
            }
        }
        Prompt(vm, focus, promptModifier.weight(1f))
        if (expanded) {
            Spacer(Modifier.width(16.dp))
            TopAction(vm, section)
        }
    }
}

@Composable
private fun TopAction(vm: LauncherViewModel, section: Section) {
    when (section) {
        Section.HOME -> WeatherChip(vm)
        Section.CALENDAR -> TButton("+ new event", primary = true) { vm.newEvent() }
        Section.MAIL -> TButton("+ new email", primary = true) { vm.email("") }
        Section.CHAT -> TButton("+ new message", primary = true) { vm.sms("") }
        Section.PEOPLE -> TButton("+ new contact", primary = true) { vm.start(vm.contactsRepo.newIntent()) }
        Section.NEWS -> TButton("↻ refresh", primary = true) { vm.reloadNews() }
        else -> WeatherChip(vm)
    }
}

@Composable
private fun WeatherChip(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val w by vm.weather.collectAsStateWithLifecycle()
    val status by vm.weatherStatus.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier.clip(shape).background(t.panel).border(1.dp, t.border, shape)
            .clickable { vm.execShell("weather") }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val cur = w
        if (cur == null) {
            T(if (status.isBlank()) "wttr: …" else status, color = t.dim, size = 13.sp, maxLines = 1)
        } else {
            T(cur.glyph, color = t.warn, size = 24.sp)
            Spacer(Modifier.width(10.dp))
            T("${cur.temp.toInt()}°", size = 26.sp, weight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Column {
                T(cur.description, size = 13.sp, weight = FontWeight.SemiBold, maxLines = 1)
                T("H${cur.high.toInt()}° L${cur.low.toInt()}°", color = t.accent2, size = 12.sp)
            }
        }
    }
}

@Composable
private fun Prompt(vm: LauncherViewModel, focus: FocusRequester, modifier: Modifier) {
    val t = LocalTerm.current
    val text by vm.input.collectAsStateWithLifecycle()
    val open by vm.shellOpen.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var histIdx by remember { mutableIntStateOf(-1) }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.heightIn(min = 52.dp).clip(shape).background(t.panel)
            .border(1.dp, if (open) t.accent else t.border, shape)
            .clickable { vm.shellOpen.value = true; runCatching { focus.requestFocus() } }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T("❯", color = t.accent, size = 18.sp, weight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = text,
            onValueChange = { vm.input.value = it; vm.shellOpen.value = true; histIdx = -1 },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Mono, fontSize = 16.sp, color = t.fg),
            cursorBrush = SolidColor(t.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
            keyboardActions = KeyboardActions(onGo = { vm.submit() }),
            modifier = Modifier.weight(1f).focusRequester(focus)
                .onFocusChanged { if (it.isFocused) vm.shellOpen.value = true }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (e.key) {
                        Key.Enter, Key.NumPadEnter -> { vm.submit(); true }
                        Key.DirectionUp -> {
                            if (history.isNotEmpty()) {
                                histIdx = if (histIdx < 0) history.lastIndex else (histIdx - 1).coerceAtLeast(0)
                                vm.input.value = history[histIdx]
                            }; true
                        }
                        Key.DirectionDown -> {
                            if (histIdx >= 0) {
                                histIdx++
                                vm.input.value = if (histIdx > history.lastIndex) { histIdx = -1; "" } else history[histIdx]
                            }; true
                        }
                        Key.Tab -> {
                            vm.shell.suggest(text).firstOrNull()?.let { s ->
                                vm.input.value = when (s.kind) {
                                    SuggestionKind.APP -> if (s.label.contains(' ') && text.contains(' ')) s.label else "open ${s.label}"
                                    SuggestionKind.COMMAND -> s.label.substringBefore(' ') + " "
                                    else -> s.label
                                }
                            }; true
                        }
                        else -> false
                    }
                },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) T("Global search · type `help`", color = t.dim, size = 16.sp, maxLines = 1)
                    inner()
                }
            },
        )
        Box(
            Modifier.clip(RoundedCornerShape(6.dp)).background(t.chip).padding(horizontal = 8.dp, vertical = 2.dp),
        ) { T("/", color = t.dim, size = 13.sp) }
    }
}

// ───────────────────────── shell overlay ─────────────────────────

@Composable
private fun ShellOverlay(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val text by vm.input.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val iconStyle by vm.prefs.iconStyle.collectAsStateWithLifecycle()
    val recentKeys by vm.prefs.recent.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val suggestions = remember(text, apps) { vm.shell.suggest(text) }
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.clip(shape).background(t.panel).border(1.dp, t.accent.copy(alpha = 0.5f), shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(16.dp),
    ) {
        if (text.isBlank()) {
            // empty prompt: recent apps and recent commands, one tap away
            val recent = remember(recentKeys, apps) { vm.recentApps() }
            if (recent.isNotEmpty()) {
                T("recent", color = t.dim, size = 12.sp)
                Spacer(Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(recent, key = { it.uniqueKey }) { app ->
                        Column(
                            Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).clickable { vm.launch(app) }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            AppIcon(app, 40.dp, iconStyle) { vm.icon(app) }
                            Spacer(Modifier.height(4.dp))
                            T(app.label, size = 11.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (history.isNotEmpty()) {
                T("history", color = t.dim, size = 12.sp)
                history.takeLast(4).asReversed().forEach { h ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { vm.input.value = h }.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        T("↺ ", color = t.dim, size = 13.sp)
                        T(h, size = 13.sp, maxLines = 1)
                    }
                }
            }
            if (recent.isNotEmpty() || history.isNotEmpty()) Divider()
        }
        if (suggestions.isNotEmpty()) {
            suggestions.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (i == 0) t.selection else Color.Transparent)
                        .clickable { s.run() }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val tag = when (s.kind) {
                        SuggestionKind.APP -> "app"
                        SuggestionKind.CONTACT -> "ppl"
                        SuggestionKind.SECTION -> "dir"
                        SuggestionKind.COMMAND -> "cmd"
                        SuggestionKind.WEB -> "www"
                    }
                    T(tag, color = t.dim, size = 12.sp, modifier = Modifier.width(40.dp))
                    if (s.app != null) {
                        AppIcon(s.app, 22.dp, iconStyle) { vm.icon(s.app) }
                        Spacer(Modifier.width(10.dp))
                    }
                    T(s.label, weight = if (i == 0) FontWeight.Bold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                    T(s.detail, color = t.dim, size = 12.sp, maxLines = 1, modifier = Modifier.widthIn(max = 260.dp))
                    if (i == 0) T("  ⏎", color = t.accent, size = 12.sp)
                }
            }
            Divider()
        }
        ConsoleView(vm, Modifier.fillMaxWidth().heightIn(max = 360.dp), showHint = suggestions.isEmpty())
    }
}

@Composable
private fun ConsoleView(vm: LauncherViewModel, modifier: Modifier, showHint: Boolean = true) {
    val t = LocalTerm.current
    val log by vm.log.collectAsStateWithLifecycle()
    val state = rememberLazyListState()
    LaunchedEffect(log.size) { if (log.isNotEmpty()) state.scrollToItem(log.lastIndex) }
    Column(modifier) {
        if (showHint && log.size <= 1) {
            T("try: open chrome · call mom · cal 3 · todo add milk · weather · theme pastel · cd mail", color = t.dim, size = 12.sp)
            Spacer(Modifier.height(6.dp))
        }
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            items(log.size) { i ->
                val l = log[i]
                T(
                    l.text,
                    size = 13.sp,
                    color = when (l.kind) {
                        LineKind.CMD -> t.accent
                        LineKind.DIM -> t.dim
                        LineKind.ERR -> t.err
                        LineKind.OK -> t.ok
                        LineKind.ACCENT -> t.accent2
                        LineKind.OUT -> t.fg
                    },
                )
            }
        }
    }
}

// ───────────────────────── navigation ─────────────────────────

@Composable
private fun NavRail(vm: LauncherViewModel, current: Section) {
    val t = LocalTerm.current
    Column(
        Modifier.width(92.dp).fillMaxHeight().padding(end = 12.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Section.entries.filter { it != Section.SETTINGS }.forEach { s -> RailItem(s, s == current) { vm.go(s) } }
        Spacer(Modifier.height(24.dp))
        RailItem(Section.SETTINGS, current == Section.SETTINGS) { vm.go(Section.SETTINGS) }
    }
}

@Composable
private fun RailItem(s: Section, selected: Boolean, onClick: () -> Unit) {
    val t = LocalTerm.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(shape)
            .background(if (selected) t.panel else Color.Transparent)
            .then(if (selected) Modifier.border(1.dp, t.border, shape) else Modifier)
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        T(s.glyph, color = if (selected) t.accent else t.fg, size = 22.sp)
        T(s.title.lowercase(), color = if (selected) t.fg else t.dim, size = 12.sp, weight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun CompactTabs(vm: LauncherViewModel, current: Section) {
    val t = LocalTerm.current
    LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(Section.entries.toList()) { s ->
            val sel = s == current
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (sel) t.panel else t.chip)
                    .clickable { vm.go(s) }.padding(horizontal = 12.dp, vertical = 7.dp),
            ) { T("${s.glyph} ${s.id}", color = if (sel) t.accent else t.dim, size = 13.sp) }
        }
    }
}

// ───────────────────────── dock ─────────────────────────

@Composable
private fun Dock(vm: LauncherViewModel, modifier: Modifier) {
    val t = LocalTerm.current
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dockPref by vm.prefs.dock.collectAsStateWithLifecycle()
    val dock = remember(apps, dockPref) { vm.dockApps() }
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val access by vm.notifAccess.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(30.dp)
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 560.dp
        Row(
            Modifier.clip(shape).background(t.panel).border(1.dp, t.border, shape).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(t.button).clickable { vm.drawerOpen.value = !vm.drawerOpen.value },
                contentAlignment = Alignment.Center,
            ) { T("⠿", color = t.onButton, size = 26.sp, weight = FontWeight.Bold) }
            dock.take(if (compact) 4 else 7).forEach { app -> DockIcon(vm, app) }
            if (!compact) {
                val cur = np
                Row(
                    Modifier.width(230.dp).clip(RoundedCornerShape(18.dp)).background(t.chip)
                        .clickable { if (cur != null) NowPlayingStore.toggle() else if (!access) vm.openNotificationAccess() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val art = cur?.art
                    if (art != null) {
                        Image(art.asImageBitmap(), null, Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(t.accent, t.accent2, t.accent3))),
                            contentAlignment = Alignment.Center,
                        ) { T(if (cur?.isPlaying == true) "▮▮" else "▶", color = t.onButton, size = 14.sp) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T(cur?.title ?: "Now playing", size = 13.sp, weight = FontWeight.Bold, maxLines = 1)
                        T(
                            when {
                                cur != null -> cur.artist.ifBlank { if (cur.isPlaying) "playing" else "paused" }
                                !dev.foldterm.launcher.BuildConfig.NOTIFICATIONS -> "full edition only"
                                !access -> "tap to connect"
                                else -> "nothing playing"
                            },
                            color = t.dim, size = 12.sp, maxLines = 1,
                        )
                    }
                    if (cur != null) {
                        T("⏭", color = t.fg, size = 16.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { NowPlayingStore.next() }.padding(6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockIcon(vm: LauncherViewModel, app: AppEntry) {
    val t = LocalTerm.current
    val haptic = LocalHapticFeedback.current
    val iconStyle by vm.prefs.iconStyle.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(t.chip)
                .combinedClickable(onClick = { vm.launch(app) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(app, 42.dp, iconStyle) { vm.icon(app) }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { T("unpin ${app.label}") }, onClick = { menu = false; vm.unpinFromDock(app) })
            DropdownMenuItem(text = { T("app info") }, onClick = { menu = false; vm.appsRepo.appInfo(app) })
        }
    }
}

// ───────────────────────── app drawer ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val haptic = LocalHapticFeedback.current
    val iconStyle by vm.prefs.iconStyle.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    val shown = remember(apps, filter) {
        if (filter.isBlank()) apps else apps.filter { it.label.contains(filter, true) || it.packageName.contains(filter, true) }
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (t.dark) 0.5f else 0.2f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.drawerOpen.value = false },
        contentAlignment = Alignment.Center,
    ) {
        Panel(
            Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.86f).widthIn(max = 1100.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            title = "apps (${apps.size})",
            trailing = { TButton("close") { vm.drawerOpen.value = false } },
        ) {
            Field(filter, { filter = it }, "filter apps…", Modifier.fillMaxWidth(), onDone = {
                shown.firstOrNull()?.let { vm.launch(it) }
            })
            Spacer(Modifier.height(12.dp))
            LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.fillMaxSize()) {
                items(shown.size, key = { shown[it].uniqueKey }) { i ->
                    val app = shown[i]
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Column(
                            Modifier.padding(4.dp).clip(RoundedCornerShape(14.dp))
                                .combinedClickable(onClick = { vm.launch(app) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                AppIcon(app, 52.dp, iconStyle) { vm.icon(app) }
                            }
                            Spacer(Modifier.height(6.dp))
                            T(app.label, size = 12.sp, maxLines = 1)
                            if (app.isWork) T("work", color = t.accent2, size = 10.sp)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { T("pin to dock") }, onClick = { menu = false; vm.pinToDock(app) })
                            DropdownMenuItem(text = { T("app info") }, onClick = { menu = false; vm.appsRepo.appInfo(app) })
                            DropdownMenuItem(text = { T("hide") }, onClick = { menu = false; vm.setHidden(app, true) })
                            DropdownMenuItem(text = { T("uninstall") }, onClick = { menu = false; vm.start(vm.appsRepo.uninstallIntent(app)) })
                        }
                    }
                }
            }
        }
    }
}
