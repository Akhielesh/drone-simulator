package com.akhielesh.datum.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * The haze source for the current screen. Glass panels placed as *siblings above* the content that
 * was marked with `hazeSource(state)` blur whatever is behind them, like macOS vibrancy.
 */
val LocalHazeState = compositionLocalOf<HazeState?> { null }

enum class GlassWeight { Thin, Regular, Thick }

/**
 * Frosted-glass surface: soft shadow, backdrop blur (Android 12+; tinted scrim on older devices or
 * when there is nothing to blur), translucent tint and a hairline border with a top highlight.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    weight: GlassWeight = GlassWeight.Regular,
    elevation: Dp = 0.dp,
    tint: Color? = null,
    hazeState: HazeState? = LocalHazeState.current,
    border: Boolean = true,
): Modifier {
    val c = Datum.colors
    val fill = tint ?: when (weight) {
        GlassWeight.Thin -> c.glass.copy(alpha = c.glass.alpha * 0.8f)
        GlassWeight.Regular -> c.glass
        GlassWeight.Thick -> c.glassStrong
    }
    var m = this
    if (elevation > 0.dp) {
        m = m.shadow(elevation, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
    }
    m = m.clip(shape)
    if (hazeState != null) {
        m = m.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = HazeBlurStyle {
                blurRadius(if (weight == GlassWeight.Thin) 18.dp else 28.dp)
                noiseFactor(0.06f)
                backgroundColor(c.background)
                colorEffects(listOf(HazeColorEffect.tint(fill)))
            },
        )
    } else {
        m = m.background(if (weight == GlassWeight.Thin) fill else fill.copy(alpha = (fill.alpha + 0.12f).coerceAtMost(1f)))
    }
    if (border) {
        m = m.border(
            width = 0.75.dp,
            brush = Brush.verticalGradient(listOf(c.glassHighlight, c.glassBorder, c.glassBorder)),
            shape = shape,
        )
    }
    return m
}

/** Opaque card for grouped content (System Settings style). */
@Composable
fun Modifier.card(shape: Shape = Shapes.lg, elevated: Boolean = false): Modifier {
    val c = Datum.colors
    var m = this
    if (elevated) m = m.shadow(10.dp, shape, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
    return m.clip(shape)
        .background(c.surface)
        .border(0.5.dp, c.glassBorder, shape)
}
