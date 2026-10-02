package com.akhielesh.datum.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.akhielesh.datum.core.data.Accent
import com.akhielesh.datum.core.data.ThemeMode

/** Apple-style semantic colors. Every value animates when the appearance changes. */
@Immutable
data class DatumColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundTop: Color,
    val elevated: Color,
    val surface: Color,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val fill: Color,
    val fillSecondary: Color,
    val fillTertiary: Color,
    val label: Color,
    val labelSecondary: Color,
    val labelTertiary: Color,
    val labelQuaternary: Color,
    val separator: Color,
    val accent: Color,
    val onAccent: Color,
    val shadow: Color,
    val blue: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val indigo: Color,
    val purple: Color,
    val pink: Color,
    val teal: Color,
    val mint: Color,
    val cyan: Color,
    val brown: Color,
    val gray: Color,
) {
    val accentSoft: Color get() = accent.copy(alpha = if (isDark) 0.22f else 0.13f)
}

object Palettes {
    val light = DatumColors(
        isDark = false,
        background = Color(0xFFF2F2F5),
        backgroundTop = Color(0xFFFAFAFC),
        elevated = Color(0xFFFFFFFF),
        surface = Color(0xFFFFFFFF),
        glass = Color(0xB8FFFFFF),
        glassStrong = Color(0xE6FFFFFF),
        glassBorder = Color(0x14000000),
        glassHighlight = Color(0xB3FFFFFF),
        fill = Color(0x33787880),
        fillSecondary = Color(0x29787880),
        fillTertiary = Color(0x1F767680),
        label = Color(0xFF1D1D1F),
        labelSecondary = Color(0x993C3C43),
        labelTertiary = Color(0x4D3C3C43),
        labelQuaternary = Color(0x2E3C3C43),
        separator = Color(0x243C3C43),
        accent = Color(0xFF007AFF),
        onAccent = Color.White,
        shadow = Color(0x33000000),
        blue = Color(0xFF007AFF),
        green = Color(0xFF28CD41),
        red = Color(0xFFFF3B30),
        orange = Color(0xFFFF9500),
        yellow = Color(0xFFFFCC00),
        indigo = Color(0xFF5856D6),
        purple = Color(0xFFAF52DE),
        pink = Color(0xFFFF2D55),
        teal = Color(0xFF30B0C7),
        mint = Color(0xFF00C7BE),
        cyan = Color(0xFF32ADE6),
        brown = Color(0xFFA2845E),
        gray = Color(0xFF8E8E93),
    )

    val dark = DatumColors(
        isDark = true,
        background = Color(0xFF0B0B0D),
        backgroundTop = Color(0xFF161618),
        elevated = Color(0xFF242426),
        surface = Color(0xFF1C1C1E),
        glass = Color(0xA61E1E21),
        glassStrong = Color(0xE01C1C1E),
        glassBorder = Color(0x1FFFFFFF),
        glassHighlight = Color(0x33FFFFFF),
        fill = Color(0x5C787880),
        fillSecondary = Color(0x52787880),
        fillTertiary = Color(0x3D767680),
        label = Color(0xFFF5F5F7),
        labelSecondary = Color(0x99EBEBF5),
        labelTertiary = Color(0x4DEBEBF5),
        labelQuaternary = Color(0x2EEBEBF5),
        separator = Color(0x40545458),
        accent = Color(0xFF0A84FF),
        onAccent = Color.White,
        shadow = Color(0x80000000),
        blue = Color(0xFF0A84FF),
        green = Color(0xFF32D74B),
        red = Color(0xFFFF453A),
        orange = Color(0xFFFF9F0A),
        yellow = Color(0xFFFFD60A),
        indigo = Color(0xFF5E5CE6),
        purple = Color(0xFFBF5AF2),
        pink = Color(0xFFFF375F),
        teal = Color(0xFF40C8E0),
        mint = Color(0xFF66D4CF),
        cyan = Color(0xFF64D2FF),
        brown = Color(0xFFAC8E68),
        gray = Color(0xFF98989D),
    )

    fun accentColor(accent: Accent, c: DatumColors): Color = when (accent) {
        Accent.MULTICOLOR, Accent.BLUE -> c.blue
        Accent.PURPLE -> c.purple
        Accent.PINK -> c.pink
        Accent.RED -> c.red
        Accent.ORANGE -> c.orange
        Accent.YELLOW -> c.yellow
        Accent.GREEN -> c.green
        Accent.GRAPHITE -> c.gray
    }

    fun resolve(dark: Boolean, accent: Accent): DatumColors {
        val base = if (dark) this.dark else this.light
        val a = accentColor(accent, base)
        // Yellow needs dark text to stay legible, like macOS.
        val on = if (accent == Accent.YELLOW) Color(0xFF1D1D1F) else Color.White
        return base.copy(accent = a, onAccent = on)
    }
}

val LocalColors = staticCompositionLocalOf { Palettes.light }

object Datum {
    val colors: DatumColors
        @Composable get() = LocalColors.current
    val type: DatumTypography
        @Composable get() = LocalTypography.current
}

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
private fun animate(target: Color): State<Color> = animateColorAsState(target, tween(450), label = "palette")

@Composable
fun DatumTheme(dark: Boolean, accent: Accent, content: @Composable () -> Unit) {
    val target = remember(dark, accent) { Palettes.resolve(dark, accent) }
    val background by animate(target.background)
    val backgroundTop by animate(target.backgroundTop)
    val elevated by animate(target.elevated)
    val surface by animate(target.surface)
    val glass by animate(target.glass)
    val glassStrong by animate(target.glassStrong)
    val glassBorder by animate(target.glassBorder)
    val glassHighlight by animate(target.glassHighlight)
    val fill by animate(target.fill)
    val fillSecondary by animate(target.fillSecondary)
    val fillTertiary by animate(target.fillTertiary)
    val label by animate(target.label)
    val labelSecondary by animate(target.labelSecondary)
    val labelTertiary by animate(target.labelTertiary)
    val labelQuaternary by animate(target.labelQuaternary)
    val separator by animate(target.separator)
    val accentC by animate(target.accent)
    val onAccent by animate(target.onAccent)
    val shadow by animate(target.shadow)
    val green by animate(target.green)
    val red by animate(target.red)
    val orange by animate(target.orange)
    val colors = target.copy(
        background = background, backgroundTop = backgroundTop, elevated = elevated, surface = surface,
        glass = glass, glassStrong = glassStrong, glassBorder = glassBorder, glassHighlight = glassHighlight,
        fill = fill, fillSecondary = fillSecondary, fillTertiary = fillTertiary,
        label = label, labelSecondary = labelSecondary, labelTertiary = labelTertiary,
        labelQuaternary = labelQuaternary, separator = separator, accent = accentC, onAccent = onAccent,
        shadow = shadow, green = green, red = red, orange = orange,
    )
    val material = if (dark) {
        darkColorScheme(primary = colors.accent, background = colors.background, surface = colors.surface, onSurface = colors.label)
    } else {
        lightColorScheme(primary = colors.accent, background = colors.background, surface = colors.surface, onSurface = colors.label)
    }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalTypography provides DatumTypography.Default,
    ) {
        MaterialTheme(colorScheme = material, content = content)
    }
}
