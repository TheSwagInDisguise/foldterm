package dev.foldterm.launcher.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle

val Mono = FontFamily.Monospace

data class TermColors(
    val name: String,
    val dark: Boolean,
    val bg: Color,
    val glowA: Color,
    val glowB: Color,
    val glowC: Color,
    val panel: Color,
    val panelAlt: Color,
    val border: Color,
    val fg: Color,
    val dim: Color,
    val accent: Color,
    val accent2: Color,
    val accent3: Color,
    val warn: Color,
    val err: Color,
    val ok: Color,
    val button: Color,
    val onButton: Color,
    val chip: Color,
    val selection: Color,
)

object Themes {
    val phosphor = TermColors(
        name = "phosphor", dark = true,
        bg = Color(0xFF0A0D12), glowA = Color(0x553D4FB8), glowB = Color(0x4479C0FF), glowC = Color(0x44C04F9A),
        panel = Color(0xD910151D), panelAlt = Color(0xCC161D28), border = Color(0xFF263040),
        fg = Color(0xFFD6E2EE), dim = Color(0xFF6B7A8C), accent = Color(0xFF7EE787), accent2 = Color(0xFF79C0FF),
        accent3 = Color(0xFFD2A8FF), warn = Color(0xFFE3B341), err = Color(0xFFFF7B72), ok = Color(0xFF7EE787),
        button = Color(0xFF7EE787), onButton = Color(0xFF0A0D12), chip = Color(0xFF1A2230), selection = Color(0x337EE787),
    )
    val amber = TermColors(
        name = "amber", dark = true,
        bg = Color(0xFF0E0A04), glowA = Color(0x44FF8C00), glowB = Color(0x22FFB000), glowC = Color(0x33A0522D),
        panel = Color(0xE0140F07), panelAlt = Color(0xCC1C150A), border = Color(0xFF3A2C14),
        fg = Color(0xFFFFD08A), dim = Color(0xFF9C7A45), accent = Color(0xFFFFB000), accent2 = Color(0xFFFFCC66),
        accent3 = Color(0xFFFF8C42), warn = Color(0xFFFFE066), err = Color(0xFFFF6B4A), ok = Color(0xFFC6E07A),
        button = Color(0xFFFFB000), onButton = Color(0xFF0E0A04), chip = Color(0xFF241A0B), selection = Color(0x33FFB000),
    )
    /** Light theme modelled on the pastel mock-ups: lavender → sky → pink → butter. */
    val pastel = TermColors(
        name = "pastel", dark = false,
        bg = Color(0xFFDCE0FA), glowA = Color(0xFFB4BCF5), glowB = Color(0xFFA8DCF7), glowC = Color(0xFFF5B8DD),
        panel = Color(0xE6FFFFFF), panelAlt = Color(0xCCF7F5FF), border = Color(0x1F1B1F3B),
        fg = Color(0xFF12141A), dim = Color(0xFF5E6475), accent = Color(0xFF4B3FD9), accent2 = Color(0xFF1E6FD9),
        accent3 = Color(0xFFC2185B), warn = Color(0xFFC77700), err = Color(0xFFC62828), ok = Color(0xFF2E7D32),
        button = Color(0xFF12141A), onButton = Color(0xFFFFFFFF), chip = Color(0x14000000), selection = Color(0x1F4B3FD9),
    )
    val paper = TermColors(
        name = "paper", dark = false,
        bg = Color(0xFFEDE6D6), glowA = Color(0x55D7C9A8), glowB = Color(0x33F4C7D0), glowC = Color(0x33A9C6B0),
        panel = Color(0xF2F8F4EA), panelAlt = Color(0xE6F1EADB), border = Color(0x332A2418),
        fg = Color(0xFF1F2A44), dim = Color(0xFF7A7263), accent = Color(0xFF1F5C46), accent2 = Color(0xFF2B4C8C),
        accent3 = Color(0xFFD6457A), warn = Color(0xFFB7791F), err = Color(0xFFB83227), ok = Color(0xFF1F5C46),
        button = Color(0xFF1F5C46), onButton = Color(0xFFF8F4EA), chip = Color(0x142A2418), selection = Color(0x26F48FB1),
    )

    val all = listOf(phosphor, amber, pastel, paper)
    val names = all.map { it.name } + "custom"
    fun byName(n: String) = all.firstOrNull { it.name == n } ?: phosphor

    /** Spec format: "dark|#accent|#background|#accent2". */
    const val DEFAULT_CUSTOM = "dark|#FF7EB6|#120A14|#7FD6FF"

    private fun hex(s: String?, fallback: Color): Color {
        val h = s?.trim()?.removePrefix("#") ?: return fallback
        val v = h.toLongOrNull(16) ?: return fallback
        return when (h.length) { 6 -> Color(0xFF000000 or v); 8 -> Color(v); else -> fallback }
    }

    private fun mix(a: Color, b: Color, t: Float) = Color(
        red = a.red + (b.red - a.red) * t, green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t, alpha = 1f,
    )

    /** Builds a full palette from a few user choices, deriving panels, borders and glows. */
    fun custom(spec: String): TermColors {
        val p = spec.split('|')
        val dark = p.getOrNull(0) != "light"
        val base = if (dark) phosphor else pastel
        val accent = hex(p.getOrNull(1), base.accent)
        val bg = hex(p.getOrNull(2), base.bg)
        val accent2 = hex(p.getOrNull(3), base.accent2)
        val ink = if (dark) Color.White else Color.Black
        val fg = if (dark) Color(0xFFE6EDF3) else Color(0xFF12141A)
        val lum = 0.299f * accent.red + 0.587f * accent.green + 0.114f * accent.blue
        return base.copy(
            name = "custom", dark = dark, bg = bg,
            glowA = accent.copy(alpha = if (dark) 0.25f else 0.35f), glowB = accent2.copy(alpha = if (dark) 0.2f else 0.3f),
            glowC = mix(accent, accent2, 0.5f).copy(alpha = if (dark) 0.18f else 0.25f),
            panel = mix(bg, ink, if (dark) 0.06f else 0.6f).copy(alpha = 0.88f),
            panelAlt = mix(bg, ink, if (dark) 0.1f else 0.75f).copy(alpha = 0.85f),
            border = mix(bg, ink, if (dark) 0.18f else 0.2f),
            fg = fg, dim = mix(fg, bg, 0.45f),
            accent = accent, accent2 = accent2, accent3 = mix(accent, accent2, 0.5f), ok = accent,
            button = accent, onButton = if (lum > 0.55f) Color(0xFF0A0D12) else Color.White,
            chip = mix(bg, ink, if (dark) 0.1f else 0.08f), selection = accent.copy(alpha = 0.22f),
        )
    }
}

val LocalTerm = staticCompositionLocalOf { Themes.phosphor }

@Composable
fun TermTheme(colors: TermColors, content: @Composable () -> Unit) {
    val scheme = if (colors.dark) darkColorScheme(
        primary = colors.accent, onPrimary = colors.onButton, background = colors.bg, surface = colors.panel,
        onSurface = colors.fg, onBackground = colors.fg, surfaceContainer = colors.panelAlt,
    ) else lightColorScheme(
        primary = colors.accent, onPrimary = colors.onButton, background = colors.bg, surface = Color.White,
        onSurface = colors.fg, onBackground = colors.fg, surfaceContainer = colors.panelAlt,
    )
    val base = Typography()
    val typo = Typography(
        bodyLarge = base.bodyLarge.copy(fontFamily = Mono),
        bodyMedium = base.bodyMedium.copy(fontFamily = Mono),
        bodySmall = base.bodySmall.copy(fontFamily = Mono),
        labelLarge = base.labelLarge.copy(fontFamily = Mono),
        labelMedium = base.labelMedium.copy(fontFamily = Mono),
        titleMedium = base.titleMedium.copy(fontFamily = Mono),
    )
    MaterialTheme(colorScheme = scheme, typography = typo) {
        CompositionLocalProvider(
            LocalTerm provides colors,
            LocalContentColor provides colors.fg,
            LocalTextStyle provides TextStyle(fontFamily = Mono, fontSize = 14.sp, color = colors.fg),
            content = content,
        )
    }
}
