package dev.foldterm.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.staticCompositionLocalOf
import dev.foldterm.launcher.data.FoldState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

val LocalFold = staticCompositionLocalOf { FoldState() }

@Composable
fun T(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalTerm.current.fg,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontSize = size,
        fontWeight = weight,
        fontFamily = Mono,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        lineHeight = (size.value * 1.4f).sp,
    )
}

/** A rounded "terminal window" with a `┌ title` header line. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    title: String? = null,
    padding: Dp = 18.dp,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTerm.current
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .clip(shape)
            .background(t.panel)
            .border(1.dp, t.border, shape)
            .padding(padding),
    ) {
        if (title != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                T("┌─ ", color = t.dim, size = 13.sp)
                T(title, color = t.accent, size = 15.sp, weight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                trailing?.invoke(this)
            }
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

/** `[ label ]` style pill. Primary = filled. */
@Composable
fun TButton(label: String, primary: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val t = LocalTerm.current
    val shape = RoundedCornerShape(50)
    Box(
        modifier
            .clip(shape)
            .background(if (primary) t.button else t.chip)
            .then(if (primary) Modifier else Modifier.border(1.dp, t.border, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        T(label, color = if (primary) t.onButton else if (enabled) t.fg else t.dim, size = 14.sp, weight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
fun Chip(label: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
    ) { T(label, color = color, size = 12.sp, weight = FontWeight.SemiBold, maxLines = 1) }
}

@Composable
fun Tabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTerm.current
    Row(
        modifier.clip(RoundedCornerShape(50)).background(t.chip).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (sel) t.panel else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) { T(o, color = if (sel) t.fg else t.dim, size = 13.sp, weight = if (sel) FontWeight.Bold else FontWeight.Normal) }
        }
    }
}

@Composable
fun Avatar(name: String, size: Dp = 40.dp, color: Color = LocalTerm.current.accent3) {
    Box(
        Modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { T(Fmt.initials(name), color = color, size = (size.value * 0.36f).sp, weight = FontWeight.Bold) }
}

/** Deterministic accent per name, like the coloured initials in the mock-ups. */
@Composable
fun nameColor(name: String): Color {
    val t = LocalTerm.current
    val palette = listOf(t.accent, t.accent2, t.accent3, t.warn, t.ok)
    return palette[abs(name.hashCode()) % palette.size]
}

@Composable
fun AsyncBitmap(key: Any, modifier: Modifier, loader: suspend () -> Bitmap?) {
    val bmp by produceState<Bitmap?>(null, key) { value = withContext(Dispatchers.IO) { loader() } }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(LocalTerm.current.chip))
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    onDone: (() -> Unit)? = null,
) {
    val t = LocalTerm.current
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        textStyle = TextStyle(fontFamily = Mono, fontSize = 14.sp, color = t.fg),
        cursorBrush = SolidColor(t.accent),
        keyboardOptions = KeyboardOptions(imeAction = if (onDone != null) ImeAction.Done else ImeAction.Default),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.clip(shape).background(t.chip).border(1.dp, t.border, shape).padding(horizontal = 14.dp, vertical = 11.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) T(placeholder, color = t.dim, maxLines = 1)
                inner()
            }
        },
    )
}

@Composable
fun KV(key: String, value: String, valueColor: Color = LocalTerm.current.fg) {
    val t = LocalTerm.current
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        T(key, color = t.dim, modifier = Modifier.width(120.dp))
        T(value, color = valueColor, modifier = Modifier.weight(1f))
    }
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(LocalTerm.current.border))
}

@Composable
fun Empty(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    val t = LocalTerm.current
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
        T("# $text", color = t.dim)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(10.dp))
            TButton(action, onClick = onAction)
        }
    }
}

/**
 * Two panes whose split lines up with a vertical hinge when there is one,
 * so content never sits on the crease of a book-style foldable.
 */
@Composable
fun TwoPane(
    modifier: Modifier = Modifier,
    leftFraction: Float = 0.55f,
    gap: Dp = 16.dp,
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    val fold = LocalFold.current
    val density = LocalDensity.current
    var originX by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }
    Row(
        modifier.onGloballyPositioned {
            originX = it.positionInWindow().x
            widthPx = it.size.width
        },
    ) {
        val hinge = fold.hinge
        val hingeLeft = hinge?.left?.minus(originX) ?: -1f
        val useHinge = fold.vertical && hinge != null && widthPx > 0 &&
            hingeLeft > widthPx * 0.25f && hingeLeft < widthPx * 0.75f
        if (useHinge) {
            val gapPx = with(density) { gap.toPx() }
            val hingeW = hinge!!.width().toFloat()
            val leftW = with(density) { (hingeLeft - gapPx / 2).toDp() }
            left(Modifier.width(leftW).fillMaxHeight())
            Spacer(Modifier.width(with(density) { (hingeW + gapPx).toDp() }))
            right(Modifier.weight(1f).fillMaxHeight())
        } else {
            left(Modifier.weight(leftFraction).fillMaxHeight())
            Spacer(Modifier.width(gap))
            right(Modifier.weight(1f - leftFraction).fillMaxHeight())
        }
    }
}

@Composable
fun ColorBar(color: Color, height: Dp = 22.dp) {
    Box(Modifier.width(4.dp).height(height).clip(RoundedCornerShape(2.dp)).background(color))
}

@Composable
fun Clickable(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) { content() }
}

@Composable
fun FullBox(content: @Composable () -> Unit) { Box(Modifier.fillMaxSize()) { content() } }
