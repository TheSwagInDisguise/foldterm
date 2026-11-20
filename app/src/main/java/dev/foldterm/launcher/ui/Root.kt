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
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.Section
import dev.foldterm.launcher.sidebarSections
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
import dev.foldterm.launcher.ui.screens.NotesScreen
import dev.foldterm.launcher.ui.screens.GamesScreen
import dev.foldterm.launcher.ui.screens.RadioScreen
import dev.foldterm.launcher.ui.screens.HabitsScreen
import dev.foldterm.launcher.ui.screens.MoneyScreen
import dev.foldterm.launcher.ui.screens.LinksScreen
import dev.foldterm.launcher.ui.screens.PodcastsScreen
import dev.foldterm.launcher.ui.screens.ToolsScreen
import dev.foldterm.launcher.ui.screens.MemosScreen
import dev.foldterm.launcher.ui.screens.HubScreen
import dev.foldterm.launcher.ui.screens.WeatherScreen
import dev.foldterm.launcher.ui.screens.MediaScreen
import dev.foldterm.launcher.ui.screens.NotificationCentre
import dev.foldterm.launcher.ui.screens.ControlsScreen
import dev.foldterm.launcher.ui.screens.UsageScreen
import dev.foldterm.launcher.ui.screens.FocusScreen
import dev.foldterm.launcher.ui.screens.FocusBanner
import dev.foldterm.launcher.ui.screens.WidgetsScreen
import androidx.compose.ui.input.key.isAltPressed
import dev.foldterm.launcher.ui.screens.PeopleScreen
import dev.foldterm.launcher.ui.screens.PhotosScreen
import dev.foldterm.launcher.ui.screens.SettingsScreen
import dev.foldterm.launcher.data.NotifKind

@Composable
fun LauncherRoot(vm: LauncherViewModel) {
    val themeName by vm.prefs.theme.collectAsStateWithLifecycle()
    val fold by vm.fold.collectAsStateWithLifecycle()
    val iconTint by vm.prefs.iconTint.collectAsStateWithLifecycle()
    val iconPack by vm.prefs.iconPack.collectAsStateWithLifecycle()
    val customSpec by vm.prefs.customTheme.collectAsStateWithLifecycle()
    val auto by vm.prefs.autoTheme.collectAsStateWithLifecycle()
    val dayTheme by vm.prefs.dayTheme.collectAsStateWithLifecycle()
    val nightTheme by vm.prefs.nightTheme.collectAsStateWithLifecycle()
    val now by vm.now.collectAsStateWithLifecycle()
    val weatherNow by vm.weather.collectAsStateWithLifecycle()
    val effective = if (auto) (if (vm.isDaytime(now, weatherNow)) dayTheme else nightTheme) else themeName
    val colors = remember(effective, customSpec) { if (effective == "custom") Themes.custom(customSpec) else Themes.byName(effective) }
    val textScale by vm.prefs.textScale.collectAsStateWithLifecycle()
    val reduceMotion by vm.prefs.reduceMotion.collectAsStateWithLifecycle()
    val baseDensity = LocalDensity.current
    // status/navigation bar icons follow FoldTerm's theme, not the system's
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.context as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !colors.dark
                isAppearanceLightNavigationBars = !colors.dark
            }
        }
    }
    TermTheme(colors) {
        CompositionLocalProvider(
            LocalFold provides fold, LocalIconTint provides parseHexColor(iconTint), LocalIconPack provides iconPack,
            LocalDensity provides androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * textScale),
            LocalReduceMotion provides reduceMotion,
        ) {
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
    val reduceMotionNow = LocalReduceMotion.current
    val dim by vm.prefs.wallpaperDim.collectAsStateWithLifecycle()
    Box(
        Modifier
            .fillMaxSize()
            // the window always shows the wallpaper; without it we paint the theme background opaquely
            .background(if (wallpaper) t.bg.copy(alpha = dim) else t.bg)
            .pointerInput(Unit) {
                // swipe up: app drawer · swipe down: notification shade (or close the drawer)
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        val threshold = 60.dp.toPx()
                        when {
                            vm.shellOpen.value -> Unit
                            total < -threshold && vm.drawerOpen.value -> Unit
                            total < -threshold -> vm.gesture(vm.prefs.gestureUp.value)
                            total > threshold && vm.drawerOpen.value -> vm.drawerOpen.value = false
                            total > threshold -> vm.gesture(vm.prefs.gestureDown.value)
                        }
                    },
                ) { _, dy -> total += dy }
            }
            .pointerInput(Unit) {
                // double-tap on empty space (children that handle taps consume them first)
                detectTapGestures(onDoubleTap = {
                    if (!vm.shellOpen.value && !vm.drawerOpen.value) vm.gesture(vm.prefs.gestureDouble.value)
                })
            }
            .drawBehind {
                // soft colour washes, echoing the gradient of the mock-ups (off with reduce motion)
                if (reduceMotionNow) return@drawBehind
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
                    e.isAltPressed && e.key in sectionKeys -> {
                        sidebarSections(vm.prefs.sectionOrder.value, vm.prefs.hiddenSections.value)
                            .getOrNull(sectionKeys.indexOf(e.key))?.let { vm.go(it) }; true
                    }
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
                        FocusBanner(vm)
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
                    FocusBanner(vm)
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

            RenameDialog(vm)
            FolderDialog(vm)

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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FolderDialog(vm: LauncherViewModel) {
    val target by vm.folderTarget.collectAsStateWithLifecycle()
    val app = target ?: return
    val t = LocalTerm.current
    val folders by vm.prefs.folders.collectAsStateWithLifecycle()
    var name by remember(app.uniqueKey) { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.folderTarget.value = null },
        containerColor = t.panel,
        title = { T("folders for ${app.label}", weight = FontWeight.Bold) },
        text = {
            Column {
                if (folders.isEmpty()) T("no folders yet — make one below", color = t.dim, size = 12.sp)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    folders.keys.sorted().forEach { f ->
                        DrawerChip((if (app.key in folders.getValue(f)) "✓ " else "") + f, app.key in folders.getValue(f)) { vm.prefs.toggleInFolder(f, app.key) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Field(name, { name = it }, "new folder name", Modifier.fillMaxWidth(), onDone = {
                    if (name.isNotBlank()) { vm.prefs.toggleInFolder(name.trim().lowercase(), app.key); name = "" }
                })
            }
        },
        confirmButton = {
            TButton("done", primary = true) {
                if (name.isNotBlank()) vm.prefs.toggleInFolder(name.trim().lowercase(), app.key)
                vm.folderTarget.value = null
            }
        },
    )
}

@Composable
private fun RenameDialog(vm: LauncherViewModel) {
    val target by vm.renameTarget.collectAsStateWithLifecycle()
    val app = target ?: return
    val t = LocalTerm.current
    var text by remember(app.uniqueKey) { mutableStateOf(app.label) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { vm.renameTarget.value = null },
        containerColor = t.panel,
        title = { T("rename ${app.label}", weight = FontWeight.Bold) },
        text = { Field(text, { text = it }, "new name", Modifier.fillMaxWidth(), onDone = { vm.rename(app, text) }) },
        confirmButton = { TButton("save", primary = true) { vm.rename(app, text) } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TButton("reset") { vm.rename(app, null) }
                TButton("cancel") { vm.renameTarget.value = null }
            }
        },
    )
}

/** The app's own shortcuts at the top of its long-press menu. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ShortcutItems(vm: LauncherViewModel, app: AppEntry, open: Boolean, close: () -> Unit) {
    val t = LocalTerm.current
    val shortcuts = remember(open, app.uniqueKey) { if (open) vm.appsRepo.shortcuts(app) else emptyList() }
    if (shortcuts.isEmpty()) return
    shortcuts.forEach { sc ->
        DropdownMenuItem(
            leadingIcon = { AsyncBitmap(sc.id + app.uniqueKey, Modifier.size(22.dp)) { vm.appsRepo.shortcutIcon(sc) } },
            text = { T((sc.shortLabel ?: sc.longLabel ?: sc.id).toString(), maxLines = 1) },
            onClick = { close(); vm.startShortcut(sc) },
        )
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(t.border))
}

private val sectionKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)

@Composable
private fun Badge(count: Int, modifier: Modifier) {
    if (count <= 0) return
    val t = LocalTerm.current
    Box(
        modifier.padding(2.dp).clip(RoundedCornerShape(50)).background(t.err).padding(horizontal = 5.dp, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) { T(if (count > 9) "9+" else count.toString(), color = Color.White, size = 10.sp, weight = FontWeight.Bold) }
}

@Composable
private fun SectionContent(vm: LauncherViewModel, section: Section, modifier: Modifier) {
    val calm = LocalReduceMotion.current
    AnimatedContent(
        targetState = section,
        modifier = modifier,
        transitionSpec = {
            if (calm) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            else (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 40 }) togetherWith fadeOut(tween(90))
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
            Section.WEATHER -> WeatherScreen(vm)
            Section.MEDIA -> MediaScreen(vm)
            Section.NOTIFS -> NotificationCentre(vm)
            Section.CONTROLS -> ControlsScreen(vm)
            Section.USAGE -> UsageScreen(vm)
            Section.FOCUS -> FocusScreen(vm)
            Section.NOTES -> NotesScreen(vm)
            Section.GAMES -> GamesScreen(vm)
            Section.RADIO -> RadioScreen(vm)
            Section.HABITS -> HabitsScreen(vm)
            Section.MONEY -> MoneyScreen(vm)
            Section.LINKS -> LinksScreen(vm)
            Section.PODCASTS -> PodcastsScreen(vm)
            Section.TOOLS -> ToolsScreen(vm)
            Section.MEMOS -> MemosScreen(vm)
            Section.HUB -> HubScreen(vm)
            Section.WIDGETS -> WidgetsScreen(vm)
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
        Section.NOTES -> TButton("+ new note", primary = true) { vm.newNote() }
        Section.WIDGETS -> TButton("+ add widget", primary = true) { vm.widgetPickerOpen.value = true }
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
            .clickable { vm.go(Section.WEATHER) }
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
    val keyClicks by vm.prefs.keyClicks.collectAsStateWithLifecycle()
    val view = androidx.compose.ui.platform.LocalView.current
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
            onValueChange = {
                if (keyClicks && it.length != text.length) view.playSoundEffect(android.view.SoundEffectConstants.CLICK)
                vm.input.value = it; vm.shellOpen.value = true; histIdx = -1; vm.selIndex.value = 0
            },
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
                            if (text.isNotBlank() && vm.selIndex.value > 0) {
                                vm.selIndex.value--
                            } else if (history.isNotEmpty()) {
                                histIdx = if (histIdx < 0) history.lastIndex else (histIdx - 1).coerceAtLeast(0)
                                vm.input.value = history[histIdx]
                            }; true
                        }
                        Key.DirectionDown -> {
                            if (text.isNotBlank() && histIdx < 0) {
                                val n = vm.shell.suggest(text).size
                                if (n > 0) vm.selIndex.value = (vm.selIndex.value + 1).coerceAtMost(n - 1)
                            } else if (histIdx >= 0) {
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
        T("🎤", size = 16.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .clickable { vm.uiRequests.tryEmit(dev.foldterm.launcher.UiRequest.Voice) }.padding(horizontal = 6.dp, vertical = 4.dp))
        Spacer(Modifier.width(4.dp))
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
    val sel by vm.selIndex.collectAsStateWithLifecycle()
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
            val tip = remember { dev.foldterm.launcher.shell.TIPS.random() }
            T("tip: $tip", color = t.dim, size = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Divider()
        }
        if (suggestions.isNotEmpty()) {
            suggestions.forEachIndexed { i, s ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (i == sel) t.selection else Color.Transparent)
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
                        SuggestionKind.EVENT -> "cal"
                        SuggestionKind.NOTE -> "txt"
                        SuggestionKind.MESSAGE -> "msg"
                        SuggestionKind.ALIAS -> "als"
                        SuggestionKind.PAIR -> "par"
                        SuggestionKind.SHORTCUT -> "sc"
                        SuggestionKind.SETTING -> "set"
                    }
                    T(tag, color = t.dim, size = 12.sp, modifier = Modifier.width(40.dp))
                    if (s.app != null) {
                        AppIcon(s.app, 22.dp, iconStyle) { vm.icon(s.app) }
                        Spacer(Modifier.width(10.dp))
                    }
                    T(s.label, weight = if (i == sel) FontWeight.Bold else FontWeight.Normal, maxLines = 1, modifier = Modifier.weight(1f))
                    T(s.detail, color = t.dim, size = 12.sp, maxLines = 1, modifier = Modifier.widthIn(max = 260.dp))
                    if (i == sel) T("  ⏎", color = t.accent, size = 12.sp)
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
    val order by vm.prefs.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by vm.prefs.hiddenSections.collectAsStateWithLifecycle()
    val sidebar = remember(order, hiddenSections) { sidebarSections(order, hiddenSections) }
    val t = LocalTerm.current
    Column(
        Modifier.width(92.dp).fillMaxHeight().padding(end = 12.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        sidebar.forEach { s -> RailItem(s, s == current) { vm.go(s) } }
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
    val order by vm.prefs.sectionOrder.collectAsStateWithLifecycle()
    val hiddenSections by vm.prefs.hiddenSections.collectAsStateWithLifecycle()
    val sidebar = remember(order, hiddenSections) { sidebarSections(order, hiddenSections) }
    val t = LocalTerm.current
    LazyRow(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(sidebar + Section.SETTINGS) { s ->
            val sel = s == current
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (sel) t.panel else t.chip)
                    .clickable { vm.go(s) }.padding(horizontal = 12.dp, vertical = 7.dp),
            ) { T("${s.glyph} ${s.id}", color = if (sel) t.accent else t.dim, size = 13.sp) }
        }
    }
}

// ───────────────────────── dock ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
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
        // Fit the dock to the width we have: smaller tiles and fewer apps on the cover screen.
        val tile = if (maxWidth < 420.dp) 46.dp else 56.dp
        val gap = if (maxWidth < 420.dp) 6.dp else 10.dp
        val reserved = 20.dp + tile + gap + (if (compact) 0.dp else 230.dp + gap)
        val fit = ((maxWidth - reserved) / (tile + gap)).toInt().coerceIn(0, 7)
        Row(
            Modifier.clip(shape).background(t.panel).border(1.dp, t.border, shape).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            Box(
                Modifier.size(tile).clip(RoundedCornerShape(16.dp)).background(t.button)
                    .combinedClickable(
                        onClick = { vm.drawerOpen.value = !vm.drawerOpen.value },
                        onLongClick = { vm.drawerOpen.value = false; vm.shellOpen.value = true },
                    ),
                contentAlignment = Alignment.Center,
            ) { T("⠿", color = t.onButton, size = 26.sp, weight = FontWeight.Bold) }
            dock.take(fit).forEach { app -> DockIcon(vm, app, tile) }
            if (!compact) {
                val cur = np
                Row(
                    Modifier.width(230.dp).clip(RoundedCornerShape(18.dp)).background(t.chip)
                        .clickable { if (cur != null) vm.go(Section.MEDIA) else if (!access) vm.openNotificationAccess() }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val art = cur?.art
                    if (art != null) {
                        Image(
                            art.asImageBitmap(), null,
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).clickable { NowPlayingStore.toggle() },
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(
                            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(Brush.linearGradient(listOf(t.accent, t.accent2, t.accent3)))
                                .clickable(enabled = cur != null) { NowPlayingStore.toggle() },
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
private fun DockIcon(vm: LauncherViewModel, app: AppEntry, tile: androidx.compose.ui.unit.Dp = 56.dp) {
    val t = LocalTerm.current
    val haptic = LocalHapticFeedback.current
    val iconStyle by vm.prefs.iconStyle.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val notifs by vm.notifications.collectAsStateWithLifecycle()
    val muted by vm.prefs.mutedApps.collectAsStateWithLifecycle()
    val unread = remember(notifs, muted, app.packageName) { vm.unreadCounts(notifs, muted)[app.packageName] ?: 0 }
    Box {
        Box(
            Modifier.size(tile).clip(RoundedCornerShape(16.dp)).background(t.chip)
                .combinedClickable(onClick = { vm.launch(app) }, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(app, tile * 0.75f, iconStyle) { vm.icon(app) }
            Badge(unread, Modifier.align(Alignment.TopEnd))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            ShortcutItems(vm, app, menu) { menu = false }
            DropdownMenuItem(text = { T("unpin ${app.label}") }, onClick = { menu = false; vm.unpinFromDock(app) })
            DropdownMenuItem(text = { T("split with…") }, onClick = { menu = false; vm.splitPick.value = app; vm.drawerOpen.value = true })
            DropdownMenuItem(text = { T("pop-up window") }, onClick = { menu = false; vm.popup(app) })
            DropdownMenuItem(text = { T("app info") }, onClick = { menu = false; vm.appsRepo.appInfo(app) })
        }
    }
}

// ───────────────────────── app drawer ─────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(vm: LauncherViewModel) {
    val t = LocalTerm.current
    val notifs by vm.notifications.collectAsStateWithLifecycle()
    val muted by vm.prefs.mutedApps.collectAsStateWithLifecycle()
    val unreadByPkg = remember(notifs, muted) { vm.unreadCounts(notifs, muted) }
    val haptic = LocalHapticFeedback.current
    val iconStyle by vm.prefs.iconStyle.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val splitPick by vm.splitPick.collectAsStateWithLifecycle()
    val pairs by vm.prefs.pairs.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    val sort by vm.prefs.drawerSort.collectAsStateWithLifecycle()
    val usage by vm.prefs.usage.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<Int?>(null) }
    val folders by vm.prefs.folders.collectAsStateWithLifecycle()
    // a folder requested by `folder <name>` applies once, when the drawer opens
    var folder by remember { mutableStateOf(vm.drawerFolder.value) }
    LaunchedEffect(Unit) { vm.drawerFolder.value = null }
    val shown = remember(apps, filter, sort, category, usage, folder, folders) {
        val inFolder = folder?.let { folders[it] }
        val f = apps.filter { filter.isBlank() || it.label.contains(filter, true) || it.packageName.contains(filter, true) }
            .filter { if (inFolder != null) it.key in inFolder else category == null || it.category == category }
        when (sort) {
            "used" -> f.sortedByDescending { usage[it.key] ?: 0 }
            "new" -> f.sortedByDescending { it.installedAt }
            else -> f
        }
    }
    val categories = remember(apps) { apps.map { it.category }.filter { it in CATEGORY_NAMES }.distinct().sortedBy { CATEGORY_NAMES[it] } }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    Box(
        Modifier.fillMaxSize().background(t.bg.copy(alpha = 0.7f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                vm.drawerOpen.value = false; vm.splitPick.value = null
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Panel(
            // sits between the top of the screen and the dock instead of floating over it
            Modifier.padding(top = 8.dp, bottom = 104.dp).fillMaxWidth(0.94f).fillMaxHeight().widthIn(max = 1100.dp)
                .background(t.bg, RoundedCornerShape(22.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            title = "apps (${apps.size})",
            trailing = { TButton("close") { vm.drawerOpen.value = false; vm.splitPick.value = null } },
        ) {
            val pick = splitPick
            if (pick != null) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(t.selection).padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T("◧ ", color = t.accent, size = 16.sp)
                    T("${pick.label} | ?  — tap the app to open on the right", weight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2)
                    TButton("cancel") { vm.splitPick.value = null }
                }
                Spacer(Modifier.height(10.dp))
            } else if (pairs.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    items(pairs.keys.sorted()) { name ->
                        val p = pairs.getValue(name)
                        Row(
                            Modifier.clip(RoundedCornerShape(50)).background(t.chip).border(1.dp, t.border, RoundedCornerShape(50))
                                .clickable { vm.launchPair(name) }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T("◧ $name", color = t.accent, size = 13.sp, weight = FontWeight.Bold)
                            T("  ${vm.labelOf(p.first)} | ${vm.labelOf(p.second)}", color = t.dim, size = 12.sp, maxLines = 1)
                            T("✕", color = t.dim, size = 12.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { vm.prefs.setPair(name, null) }.padding(horizontal = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Field(filter, { filter = it }, if (pick != null) "filter apps to pair with ${pick.label}…" else "filter apps…", Modifier.fillMaxWidth(), onDone = {
                shown.firstOrNull()?.let { if (pick != null && pick.uniqueKey != it.uniqueKey) vm.split(pick, it) else vm.launch(it) }
            })
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                items(listOf("az" to "a–z", "used" to "most used", "new" to "newest")) { (id, label) ->
                    DrawerChip(label, sort == id) { vm.prefs.setDrawerSort(id) }
                }
                item { T("│", color = t.border, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) }
                item { DrawerChip("all", category == null && folder == null) { category = null; folder = null } }
                items(folders.keys.sorted()) { f -> DrawerChip("▸ $f", folder == f) { folder = if (folder == f) null else f; category = null } }
                items(categories) { c -> DrawerChip(CATEGORY_NAMES[c] ?: "?", category == c && folder == null) { category = if (category == c) null else c; folder = null } }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(100.dp),
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(shown.size, key = { shown[it].uniqueKey }) { i ->
                    val app = shown[i]
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .combinedClickable(
                                    onClick = {
                                        val pick = vm.splitPick.value
                                        if (pick != null && pick.uniqueKey != app.uniqueKey) vm.split(pick, app) else vm.launch(app)
                                    },
                                    onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true },
                                )
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box {
                                AppIcon(app, 52.dp, iconStyle) { vm.icon(app) }
                                Badge(unreadByPkg[app.packageName] ?: 0, Modifier.align(Alignment.TopEnd))
                            }
                            Spacer(Modifier.height(6.dp))
                            T(app.label, size = 12.sp, maxLines = 1, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                            if (app.isWork) T("work", color = t.accent2, size = 10.sp, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            ShortcutItems(vm, app, menu) { menu = false }
                            DropdownMenuItem(text = { T("split with…") }, onClick = { menu = false; filter = ""; vm.splitPick.value = app })
                            DropdownMenuItem(text = { T("pop-up window") }, onClick = { menu = false; vm.popup(app) })
                            DropdownMenuItem(text = { T("pin to dock") }, onClick = { menu = false; vm.pinToDock(app) })
                            DropdownMenuItem(text = { T("app info") }, onClick = { menu = false; vm.appsRepo.appInfo(app) })
                            DropdownMenuItem(text = { T("rename") }, onClick = { menu = false; vm.renameTarget.value = app })
                            DropdownMenuItem(text = { T("folders…") }, onClick = { menu = false; vm.folderTarget.value = app })
                            DropdownMenuItem(text = { T("hide") }, onClick = { menu = false; vm.setHidden(app, true) })
                            DropdownMenuItem(text = { T("uninstall") }, onClick = { menu = false; vm.start(vm.appsRepo.uninstallIntent(app)) })
                        }
                    }
                }
            }
            // A–Z jump strip
            if (sort == "az" && filter.isBlank() && category == null && folder == null && shown.size > 30) {
                val letters = remember(shown) {
                    shown.mapIndexedNotNull { i, a -> a.label.firstOrNull()?.uppercaseChar()?.let { c -> (if (c.isLetter()) c else '#') to i } }
                        .distinctBy { it.first }
                }
                Column(
                    Modifier.fillMaxHeight().padding(start = 4.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    letters.forEach { (c, idx) ->
                        T(c.toString(), color = t.dim, size = 11.sp, weight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { scope.launch { gridState.scrollToItem(idx) } }
                                .padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            }
        }
    }
}

private val CATEGORY_NAMES = mapOf(
    android.content.pm.ApplicationInfo.CATEGORY_GAME to "games",
    android.content.pm.ApplicationInfo.CATEGORY_AUDIO to "audio",
    android.content.pm.ApplicationInfo.CATEGORY_VIDEO to "video",
    android.content.pm.ApplicationInfo.CATEGORY_IMAGE to "photo",
    android.content.pm.ApplicationInfo.CATEGORY_SOCIAL to "social",
    android.content.pm.ApplicationInfo.CATEGORY_NEWS to "news",
    android.content.pm.ApplicationInfo.CATEGORY_MAPS to "maps",
    android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY to "work",
    android.content.pm.ApplicationInfo.CATEGORY_ACCESSIBILITY to "access",
)

@Composable
private fun DrawerChip(label: String, on: Boolean, onClick: () -> Unit) {
    val t = LocalTerm.current
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (on) t.button else t.chip).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) { T(label, color = if (on) t.onButton else t.dim, size = 12.sp, weight = FontWeight.SemiBold) }
}
