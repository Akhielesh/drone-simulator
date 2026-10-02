package com.akhielesh.datum.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.GlassWeight
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

data class DockItem(val label: String, val icon: ImageVector, val colors: List<Color>)

/**
 * The macOS Dock, adapted to touch: drag a finger across it and icons magnify around the finger,
 * a tooltip names the hovered tool, releasing opens it (with the classic launch bounce). Tapping
 * works as usual. The active tool shows the little "running" dot.
 */
@Composable
fun MagnifyingDock(
    items: List<DockItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptics = LocalHaptics.current
    val c = Datum.colors
    val base = 48.dp
    val gap = 12.dp
    val pad = 10.dp
    val maxScale = 1.6f
    val slot = base + gap
    val slotPx = with(density) { slot.toPx() }
    val padPx = with(density) { pad.toPx() }
    val basePx = with(density) { base.toPx() }
    val gapPx = with(density) { gap.toPx() }
    val n = items.size
    var fingerX by remember { mutableStateOf<Float?>(null) }
    var hovered by remember { mutableStateOf(-1) }
    val onSelectState by rememberUpdatedState(onSelect)

    // Magnification follows a Gaussian centred on the finger (computed on the un-magnified layout,
    // so icons shifting under the finger can't feed back into the scale).
    val scales = items.indices.map { i ->
        val target = fingerX?.let { fx ->
            val center = padPx + i * slotPx + basePx / 2
            val d = (fx - center) / (slotPx * 1.3f)
            1f + (maxScale - 1f) * exp(-d * d)
        } ?: 1f
        animateFloatAsState(target, spring(dampingRatio = 0.72f, stiffness = 900f), label = "dockScale$i").value
    }
    val bounces = remember(n) { List(n) { Animatable(0f) } }
    LaunchedEffect(selected) {
        if (selected in bounces.indices) {
            bounces[selected].animateTo(
                0f,
                keyframes {
                    durationMillis = 620
                    0f at 0
                    -22f at 150
                    0f at 300
                    -8f at 420
                    0f at 520
                },
            )
        }
    }
    val extras = scales.map { (it - 1f) * basePx }
    val growth = extras.sum()
    val baseWidth = slot * n + pad * 2 - gap

    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        // Glass body grows with the magnified icons.
        Box(
            Modifier
                .width(baseWidth + with(density) { growth.toDp() })
                .height(base + pad * 2 + 4.dp)
                .glass(Shapes.xl, GlassWeight.Regular, elevation = 18.dp),
        )
        Row(
            Modifier
                .width(baseWidth)
                .height(base + pad * 2 + 4.dp)
                .pointerInput(n) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun indexAt(x: Float) = floor((x - padPx + gapPx / 2) / slotPx).toInt().coerceIn(0, n - 1)
                        fingerX = down.position.x
                        hovered = indexAt(down.position.x)
                        var lastIndex = hovered
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            fingerX = change.position.x
                            val idx = indexAt(change.position.x)
                            if (idx != lastIndex) {
                                haptics?.tick(0.4f)
                                lastIndex = idx
                            }
                            hovered = idx
                            change.consume()
                        }
                        val target = hovered
                        fingerX = null
                        hovered = -1
                        if (target in 0 until n) onSelectState(target)
                    }
                }
                .padding(horizontal = pad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val shift = extras.take(i).sum() + extras[i] / 2 - growth / 2
                Box(
                    Modifier
                        .width(slot.takeIf { i < n - 1 } ?: base)
                        .height(base + 4.dp),
                    contentAlignment = Alignment.TopStart,
                ) {
                    IconTile(
                        item.icon,
                        item.colors,
                        size = base,
                        modifier = Modifier.graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            scaleX = scales[i]
                            scaleY = scales[i]
                            translationX = shift
                            translationY = bounces[i].value * density.density
                        },
                    )
                    if (i == selected) {
                        Box(
                            Modifier
                                .offset { IntOffset((shift + with(density) { (base / 2 - 2.dp).toPx() }).roundToInt(), with(density) { (base + 1.dp).toPx() }.roundToInt()) }
                                .size(4.dp)
                                .clip(Shapes.pill)
                                .background(c.label.copy(alpha = 0.75f)),
                        )
                    }
                }
            }
        }
        // Tooltip above the hovered icon.
        val tipIndex = hovered
        AnimatedVisibility(
            visible = tipIndex >= 0,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.9f),
            exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val last = remember { mutableStateOf(0) }
            if (tipIndex >= 0) last.value = tipIndex
            val i = last.value
            val centerOffset = (padPx + i * slotPx + basePx / 2) - with(density) { baseWidth.toPx() } / 2 +
                (extras.take(i).sum() + extras.getOrElse(i) { 0f } / 2 - growth / 2)
            Box(
                Modifier
                    .offset { IntOffset(centerOffset.roundToInt(), -with(density) { (base * maxScale + pad + 22.dp).toPx() }.roundToInt()) }
                    .glass(Shapes.sm, GlassWeight.Thick, elevation = 6.dp)
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                DText(items.getOrNull(i)?.label ?: "", Datum.type.subhead, maxLines = 1)
            }
        }
    }
}
