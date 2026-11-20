package dev.foldterm.launcher.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.foldterm.launcher.LauncherViewModel
import dev.foldterm.launcher.ui.Field
import dev.foldterm.launcher.ui.LocalTerm
import dev.foldterm.launcher.ui.Panel
import dev.foldterm.launcher.ui.T
import dev.foldterm.launcher.ui.TButton
import dev.foldterm.launcher.ui.Tabs
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@Composable
fun GamesScreen(vm: LauncherViewModel) {
    var game by rememberSaveable { mutableIntStateOf(0) }
    Panel(Modifier.fillMaxSize(), title = "games") {
        Tabs(listOf("serpent", "mines", "typer"), game, { game = it })
        Spacer(Modifier.height(12.dp))
        // boards fit the space we actually have: on the wide inner screen height is the limit
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val serpentSide = minOf(maxWidth, maxHeight - 130.dp).coerceAtLeast(160.dp)
            val minesWidth = minOf(maxWidth, (maxHeight - 150.dp) * (9f / 11f)).coerceAtLeast(180.dp)
            when (game) {
                0 -> Serpent(vm, serpentSide)
                1 -> Mines(vm, minesWidth)
                else -> Typer(vm)
            }
        }
    }
}

private fun best(vm: LauncherViewModel, key: String) = vm.prefs.gameBest(key)

// ───────────────────────── serpent ─────────────────────────

private data class P(val x: Int, val y: Int)

@Composable
private fun Serpent(vm: LauncherViewModel, side: androidx.compose.ui.unit.Dp) {
    val t = LocalTerm.current
    val n = 18
    val body = remember { mutableStateListOf(P(8, 9), P(7, 9), P(6, 9)) }
    var dir by remember { mutableStateOf(P(1, 0)) }
    var pending by remember { mutableStateOf(P(1, 0)) }
    var food by remember { mutableStateOf(P(13, 9)) }
    var running by remember { mutableStateOf(false) }
    var over by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var top by remember { mutableIntStateOf(best(vm, "serpent")) }
    val focus = remember { FocusRequester() }

    fun reset() {
        body.clear(); body.addAll(listOf(P(8, 9), P(7, 9), P(6, 9)))
        dir = P(1, 0); pending = P(1, 0); food = P(13, 9); score = 0; over = false; running = true
        runCatching { focus.requestFocus() }
    }
    fun turn(d: P) { if (d.x != -dir.x || d.y != -dir.y) pending = d }

    LaunchedEffect(running) {
        while (running) {
            delay((150 - score * 2).coerceAtLeast(70).toLong())
            dir = pending
            val head = body.first()
            val next = P((head.x + dir.x + n) % n, (head.y + dir.y + n) % n)
            if (next in body.dropLast(1)) {
                running = false; over = true
                if (score > top) { top = score; vm.prefs.setGameBest("serpent", score) }
                break
            }
            body.add(0, next)
            if (next == food) {
                score++
                var f: P
                do { f = P(Random.nextInt(n), Random.nextInt(n)) } while (f in body)
                food = f
            } else body.removeAt(body.lastIndex)
        }
    }

    Column(Modifier.widthIn(max = side), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("score $score", color = t.accent, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            T("best $top", color = t.dim)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(t.panelAlt)
                .border(1.dp, t.border, RoundedCornerShape(14.dp))
                .focusRequester(focus).focusable()
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionUp, Key.W -> { turn(P(0, -1)); true }
                        Key.DirectionDown, Key.S -> { turn(P(0, 1)); true }
                        Key.DirectionLeft, Key.A -> { turn(P(-1, 0)); true }
                        Key.DirectionRight, Key.D -> { turn(P(1, 0)); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    var acc = Offset.Zero
                    detectDragGestures(onDragStart = { acc = Offset.Zero }) { _, d ->
                        acc += d
                        if (abs(acc.x) > 40 || abs(acc.y) > 40) {
                            if (abs(acc.x) > abs(acc.y)) turn(P(if (acc.x > 0) 1 else -1, 0)) else turn(P(0, if (acc.y > 0) 1 else -1))
                            acc = Offset.Zero
                        }
                    }
                },
        ) {
            val c = size.width / n
            drawRoundRect(t.warn, Offset(food.x * c + c * 0.15f, food.y * c + c * 0.15f), Size(c * 0.7f, c * 0.7f), CornerRadius(c / 2))
            body.forEachIndexed { i, p ->
                drawRoundRect(
                    if (i == 0) t.accent else t.accent.copy(alpha = 0.55f + 0.45f * (1f - i.toFloat() / body.size)),
                    Offset(p.x * c + 1, p.y * c + 1), Size(c - 2, c - 2), CornerRadius(c / 4),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!running) {
            T(if (over) "game over — score $score" else "swipe or use arrow keys to steer", color = if (over) t.err else t.dim)
            Spacer(Modifier.height(8.dp))
            TButton(if (over) "play again" else "start", primary = true) { reset() }
        } else {
            // on-screen pad for one-handed play
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("◀" to P(-1, 0), "▲" to P(0, -1), "▼" to P(0, 1), "▶" to P(1, 0)).forEach { (g, d) -> TButton(g) { turn(d) } }
            }
        }
    }
}

// ───────────────────────── mines ─────────────────────────

@Composable
private fun Mines(vm: LauncherViewModel, width: androidx.compose.ui.unit.Dp) {
    val t = LocalTerm.current
    val w = 9; val h = 11; val count = 14
    var mines by remember { mutableStateOf(emptySet<Int>()) }
    val open = remember { mutableStateListOf<Int>() }
    val flags = remember { mutableStateListOf<Int>() }
    var state by remember { mutableIntStateOf(0) } // 0 ready, 1 playing, 2 lost, 3 won
    var started by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var top by remember { mutableIntStateOf(best(vm, "mines")) }
    LaunchedEffect(state) { while (state == 1) { now = System.currentTimeMillis(); delay(500) } }

    fun around(i: Int) = buildList {
        val x = i % w; val y = i / w
        for (dy in -1..1) for (dx in -1..1) if ((dx != 0 || dy != 0) && x + dx in 0 until w && y + dy in 0 until h) add((y + dy) * w + x + dx)
    }
    fun near(i: Int) = around(i).count { it in mines }
    fun reset() { mines = emptySet(); open.clear(); flags.clear(); state = 0 }
    fun reveal(i: Int) {
        if (state >= 2 || i in flags || i in open) return
        if (state == 0) {
            // first tap is always safe
            val safe = around(i).toSet() + i
            mines = (0 until w * h).filter { it !in safe }.shuffled().take(count).toSet()
            state = 1; started = System.currentTimeMillis(); now = started
        }
        if (i in mines) { state = 2; open.add(i); return }
        val stack = ArrayDeque(listOf(i))
        while (stack.isNotEmpty()) {
            val c = stack.removeLast()
            if (c in open) continue
            open.add(c)
            if (near(c) == 0) around(c).filter { it !in open && it !in mines }.forEach { stack.add(it) }
        }
        if (open.size == w * h - count) {
            state = 3
            val secs = ((System.currentTimeMillis() - started) / 1000).toInt()
            if (top == 0 || secs < top) { top = secs; vm.prefs.setGameBest("mines", secs) }
        }
    }

    Column(Modifier.widthIn(max = width), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) {
            T("⚑ ${count - flags.size}", color = t.warn, modifier = Modifier.weight(1f))
            T(if (state == 0) "tap to start" else "${((if (state == 1) now else now) - started).coerceAtLeast(0) / 1000}s", color = t.accent)
            Spacer(Modifier.width(14.dp))
            T("best ${if (top == 0) "—" else "${top}s"}", color = t.dim)
        }
        Spacer(Modifier.height(8.dp))
        for (y in 0 until h) {
            Row(Modifier.fillMaxWidth()) {
                for (x in 0 until w) {
                    val i = y * w + x
                    val isOpen = i in open || (state == 2 && i in mines)
                    val k = if (isOpen && i !in mines) near(i) else 0
                    Box(
                        Modifier.weight(1f).aspectRatio(1f).padding(1.5.dp).clip(RoundedCornerShape(6.dp))
                            .background(if (isOpen) t.panelAlt else t.chip)
                            .pointerInput(i, state) {
                                detectTapGestures(
                                    onTap = { reveal(i) },
                                    onLongPress = { if (state < 2 && i !in open) { if (i in flags) flags.remove(i) else flags.add(i) } },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            isOpen && i in mines -> T("✹", color = t.err, weight = FontWeight.Bold)
                            isOpen && k > 0 -> T("$k", color = listOf(t.accent2, t.ok, t.warn, t.err, t.accent3)[(k - 1).coerceAtMost(4)], weight = FontWeight.Bold)
                            !isOpen && i in flags -> T("⚑", color = t.warn)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        when (state) {
            2 -> T("boom. hold a square to flag it next time", color = t.err)
            3 -> T("cleared in ${(now - started) / 1000}s ✓", color = t.ok)
            else -> T("tap to open · hold to flag", color = t.dim, size = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        TButton("new board", primary = state >= 2) { reset() }
    }
}

// ───────────────────────── typer ─────────────────────────

private val PHRASES = listOf(
    "open spotify", "split chrome | notes", "timer 5m tea", "event fri 3pm dentist", "todo add pay rent fri !",
    "100 usd in aud", "weather paris", "focus 25m essay", "alias gm=dnd off; brief", "call mum", "sms maya running late",
    "bright 40", "dnd on", "clocks add tokyo", "note buy milk", "18% of 240", "pair save work slack | chrome", "neofetch",
)

@Composable
private fun Typer(vm: LauncherViewModel) {
    val t = LocalTerm.current
    var target by remember { mutableStateOf(PHRASES.random()) }
    var typed by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var ends by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    var chars by remember { mutableIntStateOf(0) }
    var errors by remember { mutableIntStateOf(0) }
    var done by remember { mutableIntStateOf(0) }
    var top by remember { mutableIntStateOf(best(vm, "typer")) }
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            if (now >= ends) {
                running = false
                val wpm = chars / 5 * 2 // 30 seconds → ×2
                if (wpm > top) { top = wpm; vm.prefs.setGameBest("typer", wpm) }
            }
            delay(200)
        }
    }
    val left = ((ends - now) / 1000).coerceAtLeast(0)
    Column(Modifier.widthIn(max = 560.dp)) {
        Row(Modifier.fillMaxWidth()) {
            T(if (running) "${left}s" else "30s sprint", color = t.accent, weight = FontWeight.Bold, modifier = Modifier.weight(1f))
            T("best ${top} wpm", color = t.dim)
        }
        Spacer(Modifier.height(14.dp))
        Row {
            T("❯ ", color = t.accent, size = 20.sp)
            target.forEachIndexed { i, ch ->
                val c = when {
                    i >= typed.length -> t.dim
                    typed[i] == ch -> t.fg
                    else -> t.err
                }
                T(ch.toString(), color = c, size = 20.sp, weight = if (i == typed.length) FontWeight.Bold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.height(14.dp))
        Field(typed, { v ->
            if (!running && v.isNotEmpty() && (ends == 0L || now >= ends)) {
                running = true; ends = System.currentTimeMillis() + 30_000; now = System.currentTimeMillis(); chars = 0; errors = 0; done = 0
            }
            if (!running) return@Field
            if (v.length > typed.length && v.lastOrNull() != target.getOrNull(v.length - 1)) errors++
            typed = v
            if (typed == target) {
                chars += target.length + 1; done++
                target = PHRASES.filter { it != target }.random(); typed = ""
            }
        }, if (running) "type the command above" else "start typing to begin", Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        if (!running && ends != 0L) {
            val wpm = chars / 5 * 2
            T("$wpm wpm · $done commands · ${errors} typos", color = t.accent, size = 18.sp, weight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TButton("again", primary = true) { ends = 0L; typed = ""; target = PHRASES.random() }
        } else if (!running) {
            T("type as many real FoldTerm commands as you can in 30 seconds", color = t.dim, size = 12.sp)
        }
    }
}

