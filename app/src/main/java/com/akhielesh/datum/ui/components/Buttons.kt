package com.akhielesh.datum.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.GlassWeight
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass

enum class ButtonStyle { Filled, Tinted, Glass, Plain, Destructive, Success }

/** Capsule button with an optional leading icon. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ButtonStyle = ButtonStyle.Filled,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val c = Datum.colors
    val (bg, fg) = when (style) {
        ButtonStyle.Filled -> c.accent to c.onAccent
        ButtonStyle.Tinted -> c.accentSoft to c.accent
        ButtonStyle.Glass -> Color.Transparent to c.label
        ButtonStyle.Plain -> Color.Transparent to c.accent
        ButtonStyle.Destructive -> c.red.copy(alpha = if (c.isDark) 0.22f else 0.12f) to c.red
        ButtonStyle.Success -> c.green to Color.White
    }
    val base = modifier
        .pressable(enabled = enabled, onClick = onClick)
        .defaultMinSize(minHeight = if (compact) 34.dp else 46.dp)
    val surface = when (style) {
        ButtonStyle.Glass -> base.glass(Shapes.pill, GlassWeight.Regular)
        ButtonStyle.Filled, ButtonStyle.Success -> base
            .shadow(6.dp, Shapes.pill, clip = false, ambientColor = bg.copy(alpha = 0.5f), spotColor = bg.copy(alpha = 0.6f))
            .clip(Shapes.pill)
            .background(Brush.verticalGradient(listOf(bg.copy(alpha = 0.92f), bg)))
        else -> base.clip(Shapes.pill).background(bg)
    }
    Row(
        modifier = surface.padding(horizontal = if (compact) 14.dp else 20.dp, vertical = if (compact) 7.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) DIcon(icon, tint = fg, size = if (compact) 17.dp else 19.dp)
        DText(text, if (compact) Datum.type.subhead else Datum.type.headline, fg, maxLines = 1)
    }
}

/** Round icon button (toolbar style). */
@Composable
fun CircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    style: ButtonStyle = ButtonStyle.Glass,
    tint: Color? = null,
    enabled: Boolean = true,
    iconSize: Dp = size * 0.5f,
) {
    val c = Datum.colors
    val base = modifier.size(size).pressable(enabled = enabled, onClick = onClick)
    val (surface, fg) = when (style) {
        ButtonStyle.Glass -> base.glass(Shapes.pill, GlassWeight.Regular) to (tint ?: c.label)
        ButtonStyle.Filled -> base
            .shadow(6.dp, Shapes.pill, clip = false, ambientColor = c.accent.copy(alpha = 0.5f), spotColor = c.accent.copy(alpha = 0.6f))
            .clip(Shapes.pill)
            .background(tint ?: c.accent) to c.onAccent
        ButtonStyle.Tinted -> base.clip(Shapes.pill).background((tint ?: c.accent).copy(alpha = if (c.isDark) 0.22f else 0.13f)) to (tint ?: c.accent)
        ButtonStyle.Destructive -> base.clip(Shapes.pill).background(c.red.copy(alpha = 0.15f)) to c.red
        ButtonStyle.Success -> base.clip(Shapes.pill).background(c.green) to Color.White
        ButtonStyle.Plain -> base to (tint ?: c.label)
    }
    Box(surface, contentAlignment = Alignment.Center) {
        DIcon(icon, tint = fg, size = iconSize)
    }
}

/**
 * The big round capture button (camera-shutter style) used by measuring screens: a ring with an
 * inner disc that shrinks while pressed.
 */
@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = Datum.colors.accent,
    enabled: Boolean = true,
    size: Dp = 74.dp,
) {
    val c = Datum.colors
    Box(
        modifier
            .size(size)
            .pressable(enabled = enabled, scaleDown = 0.9f, onClick = onClick)
            .glass(Shapes.pill, GlassWeight.Regular, elevation = 8.dp)
            .border(2.5.dp, Color.White.copy(alpha = 0.9f), Shapes.pill)
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size - 14.dp)
                .clip(Shapes.pill)
                .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.9f), color))),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) DIcon(icon, tint = if (color == c.yellow) Color.Black else Color.White, size = size * 0.36f)
        }
    }
}

/** App-icon style tile: glyph on a gradient squircle (used in the dock, home grid and settings rows). */
@Composable
fun IconTile(
    icon: ImageVector,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    glyph: Dp = size * 0.52f,
) {
    Box(
        modifier
            .size(size)
            .clip(com.akhielesh.datum.ui.theme.SmoothCornerShape(size * 0.27f))
            .background(Brush.linearGradient(colors))
            .border(0.6.dp, Color.White.copy(alpha = 0.22f), com.akhielesh.datum.ui.theme.SmoothCornerShape(size * 0.27f)),
        contentAlignment = Alignment.Center,
    ) {
        DIcon(icon, tint = Color.White, size = glyph)
    }
}
