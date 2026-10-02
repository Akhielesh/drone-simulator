package com.akhielesh.datum.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.core.data.Accent
import com.akhielesh.datum.core.data.ThemeMode
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Palettes
import com.akhielesh.datum.ui.theme.Shapes

/** macOS-style appearance picker: three miniature windows (Light, Dark, Auto). */
@Composable
fun AppearancePicker(mode: ThemeMode, onChange: (ThemeMode) -> Unit, modifier: Modifier = Modifier) {
    val c = Datum.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark", ThemeMode.SYSTEM to "Auto").forEach { (m, label) ->
            val selected = m == mode
            val ring by animateDpAsState(if (selected) 2.5.dp else 0.5.dp, label = "ring")
            Column(
                Modifier.weight(1f).pressable { onChange(m) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.35f)
                        .clip(Shapes.md)
                        .border(ring, if (selected) c.accent else c.glassBorder, Shapes.md),
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        when (m) {
                            ThemeMode.LIGHT -> miniWindow(false, c.accent)
                            ThemeMode.DARK -> miniWindow(true, c.accent)
                            ThemeMode.SYSTEM -> {
                                clipRect(right = size.width / 2) { miniWindow(false, c.accent) }
                                clipRect(left = size.width / 2) { miniWindow(true, c.accent) }
                            }
                        }
                    }
                }
                DText(
                    label,
                    if (selected) Datum.type.subhead else Datum.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
                    if (selected) c.label else c.labelSecondary,
                    Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}

private fun DrawScope.miniWindow(dark: Boolean, accent: Color) {
    val wall = if (dark) Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF3A1F4F)))
    else Brush.linearGradient(listOf(Color(0xFF9FD2FF), Color(0xFFD8C2FF)))
    drawRect(wall)
    val win = if (dark) Color(0xFF2A2A2D) else Color(0xFFFFFFFF)
    val bar = if (dark) Color(0xFF3A3A3D) else Color(0xFFECECEE)
    val w = size.width
    val h = size.height
    val left = w * 0.14f
    val top = h * 0.2f
    val ww = w * 0.72f
    val wh = h * 0.62f
    drawRoundRect(Color.Black.copy(alpha = 0.18f), Offset(left + 2, top + 3), Size(ww, wh), CornerRadius(8f))
    drawRoundRect(win, Offset(left, top), Size(ww, wh), CornerRadius(8f))
    drawRoundRect(bar, Offset(left, top), Size(ww, wh * 0.2f), CornerRadius(8f))
    val dotR = wh * 0.035f
    listOf(Color(0xFFFF5F57), Color(0xFFFEBC2E), Color(0xFF28C840)).forEachIndexed { i, col ->
        drawCircle(col, dotR, Offset(left + wh * 0.09f + i * dotR * 3f, top + wh * 0.1f))
    }
    val line = if (dark) Color(0xFF4A4A4E) else Color(0xFFE2E2E6)
    for (i in 0 until 3) {
        drawRoundRect(line, Offset(left + ww * 0.1f, top + wh * (0.36f + i * 0.17f)), Size(ww * (0.75f - i * 0.15f), wh * 0.07f), CornerRadius(4f))
    }
    drawRoundRect(accent, Offset(left + ww * 0.62f, top + wh * 0.74f), Size(ww * 0.26f, wh * 0.12f), CornerRadius(6f))
}

/** macOS accent colour dots, in System Settings order. */
@Composable
fun AccentPicker(accent: Accent, onChange: (Accent) -> Unit, modifier: Modifier = Modifier) {
    val c = Datum.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Accent.entries.forEach { a ->
            val selected = a == accent
            val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "accentScale")
            val color = Palettes.accentColor(a, c)
            Box(
                Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .pressable { onChange(a) }
                    .clip(Shapes.pill)
                    .background(
                        if (a == Accent.MULTICOLOR) {
                            Brush.sweepGradient(listOf(c.red, c.orange, c.yellow, c.green, c.blue, c.purple, c.pink, c.red))
                        } else {
                            Brush.verticalGradient(listOf(color.copy(alpha = 0.85f), color))
                        },
                    )
                    .border(0.75.dp, Color.Black.copy(alpha = 0.12f), Shapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Box(Modifier.size(10.dp).clip(Shapes.pill).background(Color.White))
                }
            }
        }
    }
}

@Composable
fun CheckMark(visible: Boolean) {
    if (visible) DIcon(DatumIcons.Check, tint = Datum.colors.accent, size = 18.dp)
}
