package com.akhielesh.datum.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import kotlin.math.roundToInt

/** macOS segmented control: a recessed track with a raised thumb that glides between segments. */
@Composable
fun SegmentedControl(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    icons: List<ImageVector?>? = null,
    height: Dp = 34.dp,
) {
    val c = Datum.colors
    val haptics = LocalHaptics.current
    BoxWithConstraints(
        modifier
            .height(height)
            .clip(Shapes.sm)
            .background(c.fillTertiary)
            .padding(2.5.dp),
    ) {
        val segment = maxWidth / items.size
        val thumbX by animateDpAsState(segment * selected, spring(dampingRatio = 0.78f, stiffness = 520f), label = "thumb")
        Box(
            Modifier
                .offset(x = thumbX)
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, Shapes.sm, clip = false, ambientColor = c.shadow, spotColor = c.shadow)
                .clip(Shapes.sm)
                .background(if (c.isDark) Color(0xFF5B5B60) else Color.White),
        )
        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { i, label ->
                val isSel = i == selected
                val color by animateColorAsState(if (isSel) c.label else c.labelSecondary, tween(180), label = "segText")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            if (i != selected) {
                                haptics?.tick(0.6f)
                                onSelect(i)
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icons?.getOrNull(i)?.let {
                        DIcon(it, tint = color, size = 15.dp)
                        if (label.isNotEmpty()) Box(Modifier.width(5.dp))
                    }
                    if (label.isNotEmpty()) {
                        DText(label, if (isSel) Datum.type.subhead else Datum.type.subhead.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), color, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Toggle switch; the knob stretches while pressed, like Apple's. */
@Composable
fun MacSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Datum.colors
    val haptics = LocalHaptics.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val track by animateColorAsState(if (checked) c.green else c.fill, tween(200), label = "track")
    val knobW by animateDpAsState(if (pressed) 28.dp else 23.dp, spring(stiffness = Spring.StiffnessMedium), label = "knobW")
    val progress by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.72f, stiffness = 520f), label = "knob")
    val width = 48.dp
    val h = 28.dp
    Box(
        modifier
            .size(width, h)
            .clip(Shapes.pill)
            .background(track)
            .clickable(source, indication = null, enabled = enabled) {
                haptics?.tick(0.7f)
                onCheckedChange(!checked)
            }
            .padding(2.5.dp),
    ) {
        val travel = width - 5.dp - knobW
        Box(
            Modifier
                .offset(x = travel * progress)
                .size(knobW, h - 5.dp)
                .shadow(3.dp, Shapes.pill, clip = false)
                .clip(Shapes.pill)
                .background(Color.White),
        )
    }
}

/** Thin-track slider with a round knob; [steps] > 0 snaps and ticks haptically. */
@Composable
fun MacSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: () -> Unit = {},
) {
    val c = Datum.colors
    val haptics = LocalHaptics.current
    val density = LocalDensity.current
    val currentValue by rememberUpdatedState(value)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val knob = 26.dp
    fun fractionToValue(f: Float): Float {
        var v = range.start + f.coerceIn(0f, 1f) * (range.endInclusive - range.start)
        if (steps > 0) {
            val stepSize = (range.endInclusive - range.start) / steps
            v = range.start + ((v - range.start) / stepSize).roundToInt() * stepSize
        }
        return v
    }
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
    BoxWithConstraints(
        modifier
            .height(32.dp)
            .fillMaxWidth()
            .pointerInput(range, steps) {
                detectTapGestures { o ->
                    val knobPx = with(density) { knob.toPx() }
                    val f = (o.x - knobPx / 2) / (size.width - knobPx)
                    onValueChange(fractionToValue(f))
                    onValueChangeFinished()
                }
            }
            .pointerInput(range, steps) {
                val knobPx = with(density) { knob.toPx() }
                detectDragGestures(onDragEnd = onValueChangeFinished) { change, _ ->
                    val f = (change.position.x - knobPx / 2) / (size.width - knobPx)
                    val nv = fractionToValue(f)
                    if (steps > 0 && nv != currentValue) haptics?.tick(0.45f)
                    onValueChange(nv)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        widthPx = with(density) { maxWidth.toPx() }
        val travel = maxWidth - knob
        Box(
            Modifier
                .padding(horizontal = knob / 2)
                .fillMaxWidth()
                .height(4.dp)
                .clip(Shapes.pill)
                .background(c.fill),
        )
        Box(
            Modifier
                .padding(start = knob / 2)
                .width(travel * fraction)
                .height(4.dp)
                .clip(Shapes.pill)
                .background(c.accent),
        )
        Box(
            Modifier
                .offset(x = travel * fraction)
                .size(knob)
                .shadow(4.dp, Shapes.pill, clip = false)
                .clip(Shapes.pill)
                .background(Color.White),
        )
    }
}

/** Selectable capsule chip. */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = Datum.colors
    val bg by animateColorAsState(if (selected) c.accent else c.fillTertiary, tween(180), label = "chipBg")
    val fg by animateColorAsState(if (selected) c.onAccent else c.label, tween(180), label = "chipFg")
    Row(
        modifier
            .pressable(onClick = onClick)
            .clip(Shapes.pill)
            .background(bg)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) DIcon(icon, tint = fg, size = 15.dp)
        DText(text, Datum.type.subhead, fg, maxLines = 1)
    }
}

/** Circular progress (hold-to-capture, calibration). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = Datum.colors.accent,
    track: Color = Datum.colors.fill,
    stroke: Dp = 4.dp,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(120), label = "ring")
    Canvas(modifier) {
        val w = stroke.toPx()
        val inset = w / 2
        val s = Size(size.width - w, size.height - w)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), s, style = Stroke(w))
        drawArc(color, -90f, 360f * animated, false, Offset(inset, inset), s, style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** Small status capsule: colored dot + text (e.g. "Calibrating", "Sliding", "Locked"). */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, pulsing: Boolean = false) {
    val c = Datum.colors
    val pulse = if (pulsing) {
        val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
        t.animateFloat(
            0.35f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(tween(700), androidx.compose.animation.core.RepeatMode.Reverse),
            label = "pulseA",
        ).value
    } else {
        1f
    }
    Row(
        modifier
            .clip(Shapes.pill)
            .background(color.copy(alpha = if (c.isDark) 0.2f else 0.12f))
            .padding(horizontal = 11.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(Shapes.pill)
                .background(color.copy(alpha = pulse)),
        )
        DText(text, Datum.type.subhead, color, maxLines = 1)
    }
}

/** Progress dots for guided multi-step flows. */
@Composable
fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val c = Datum.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 20.dp else 7.dp, spring(dampingRatio = 0.7f), label = "dot")
            val col by animateColorAsState(
                when {
                    i < current -> c.green
                    i == current -> c.accent
                    else -> c.fill
                },
                tween(200), label = "dotC",
            )
            Box(
                Modifier
                    .size(w, 7.dp)
                    .clip(Shapes.pill)
                    .background(col),
            )
        }
    }
}
