package com.akhielesh.datum.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.core.feedback.Haptics
import com.akhielesh.datum.core.feedback.Tones
import com.akhielesh.datum.ui.theme.Datum

/** Haptics/sounds for UI components; null in previews and tests. */
val LocalHaptics = staticCompositionLocalOf<Haptics?> { null }
val LocalTones = staticCompositionLocalOf<Tones?> { null }

@Composable
fun DText(
    text: String,
    style: TextStyle = Datum.type.body,
    color: Color = Datum.colors.label,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = if (align != null) style.copy(color = color, textAlign = align) else style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Press feedback shared by every tappable surface: a springy scale-down (like macOS/iOS buttons),
 * no Material ripple, and an optional haptic click.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    haptic: Boolean = true,
    scaleDown: Float = 0.955f,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )
    val haptics = LocalHaptics.current
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            alpha = if (enabled) 1f else 0.4f
        }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (haptic) haptics?.click()
            onClick()
        }
}

/** A value + unit pair typeset like Apple's readouts: big thin numerals, small unit. */
@Composable
fun Readout(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Datum.type.readoutXL,
    unitStyle: TextStyle = Datum.type.title3,
    color: Color = Datum.colors.label,
    unitColor: Color = Datum.colors.labelSecondary,
    gap: Dp = 6.dp,
    rolling: Boolean = false,
) {
    Row(modifier = modifier) {
        if (rolling) {
            RollingText(value, style, color, Modifier.alignByBaseline())
        } else {
            DText(value, style, color, Modifier.alignByBaseline(), maxLines = 1)
        }
        if (unit.isNotEmpty()) {
            Spacer(Modifier.width(gap))
            DText(unit, unitStyle, unitColor, Modifier.alignByBaseline(), maxLines = 1)
        }
    }
}

/** Digits roll vertically when they change (odometer style). */
@Composable
fun RollingText(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier) {
        text.forEachIndexed { i, ch ->
            AnimatedContent(
                targetState = ch,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(tween(260)) { h -> if (up) h else -h } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically(tween(260)) { h -> if (up) -h else h } + fadeOut(tween(160)))
                },
                label = "digit$i",
            ) { c ->
                DText(c.toString(), style, color, maxLines = 1)
            }
        }
    }
}
