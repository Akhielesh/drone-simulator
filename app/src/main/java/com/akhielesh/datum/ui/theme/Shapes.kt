package com.akhielesh.datum.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Apple-style "continuous" rounded rectangle (squircle). Circular corners start abruptly; Apple's
 * curvature ramps up gradually, which is a big part of why macOS UI looks soft. This follows the
 * corner-smoothing construction popularised by Figma: Bézier lead-in → shortened circular arc →
 * Bézier lead-out per corner. Capsules (radius ≥ half the height) degrade gracefully to pills.
 */
class SmoothCornerShape(private val radius: Dp, private val smoothing: Float = 0.6f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        return Outline.Generic(smoothRoundRect(size.width, size.height, r, smoothing))
    }

    override fun equals(other: Any?) = other is SmoothCornerShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode() = radius.hashCode() * 31 + smoothing.hashCode()
}

private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

fun smoothRoundRect(w: Float, h: Float, radius: Float, smoothingIn: Float): Path {
    val path = Path()
    val budget = min(w, h) / 2f
    val r = min(radius, budget)
    if (r <= 0.5f || w <= 0f || h <= 0f) {
        path.addRect(Rect(0f, 0f, w, h))
        return path
    }
    var s = smoothingIn
    var p = (1 + s) * r
    if (p > budget) {
        s = max(0f, min(s, budget / r - 1f))
        p = min(p, budget)
    }
    val arcMeasure = 90f * (1 - s)
    val arcSection = sin(rad(arcMeasure / 2)) * r * sqrt(2f)
    val alpha = (90f - arcMeasure) / 2f
    val p3p4 = r * tan(rad(alpha / 2))
    val beta = 45f * s
    val c = p3p4 * cos(rad(beta))
    val d = c * tan(rad(beta))
    val b = (p - arcSection - c - d) / 3f
    val a = 2 * b

    fun arc(cx: Float, cy: Float, start: Float) {
        path.arcTo(Rect(cx - r, cy - r, cx + r, cy + r), start + alpha, arcMeasure, false)
    }

    path.moveTo(w - p, 0f)
    // top-right
    path.relativeCubicTo(a, 0f, a + b, 0f, a + b + c, d)
    arc(w - r, r, -90f)
    path.relativeCubicTo(d, c, d, b + c, d, a + b + c)
    path.lineTo(w, h - p)
    // bottom-right
    path.relativeCubicTo(0f, a, 0f, a + b, -d, a + b + c)
    arc(w - r, h - r, 0f)
    path.relativeCubicTo(-c, d, -(b + c), d, -(a + b + c), d)
    path.lineTo(p, h)
    // bottom-left
    path.relativeCubicTo(-a, 0f, -(a + b), 0f, -(a + b + c), -d)
    arc(r, h - r, 90f)
    path.relativeCubicTo(-d, -c, -d, -(b + c), -d, -(a + b + c))
    path.lineTo(0f, p)
    // top-left
    path.relativeCubicTo(0f, -a, 0f, -(a + b), d, -(a + b + c))
    arc(r, r, 180f)
    path.relativeCubicTo(c, -d, b + c, -d, a + b + c, -d)
    path.close()
    return path
}

object Shapes {
    val xs = SmoothCornerShape(6.dp)
    val sm = SmoothCornerShape(10.dp)
    val md = SmoothCornerShape(14.dp)
    val lg = SmoothCornerShape(20.dp)
    val xl = SmoothCornerShape(28.dp)
    val xxl = SmoothCornerShape(36.dp)
    val pill = SmoothCornerShape(999.dp)
}
