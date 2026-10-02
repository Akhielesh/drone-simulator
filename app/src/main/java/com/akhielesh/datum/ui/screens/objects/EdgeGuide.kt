package com.akhielesh.datum.ui.screens.objects

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.screens.slide.pill
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.InterText
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private const val COS30 = 0.8660254f
private const val SIN30 = 0.5f

/** Isometric projection: x runs down-right, z down-left, y straight up. */
private fun iso(x: Float, y: Float, z: Float) = Offset((x - z) * COS30, (x + z) * SIN30 - y)

/**
 * A measurable edge in model space. Its label is pushed off the edge along [normal] (a unit vector
 * pointing away from the body) just far enough to clear it, or sits on the edge when [normal] is zero.
 */
private class GuideEdge(val dim: Dim, val a: Offset, val b: Offset, val normal: Offset = Offset.Zero)

private class GuideLabel(val text: String, val anchor: Offset, val normal: Offset, val fill: Color, val style: TextStyle)

private fun defaultProportions(shape: ShapeKind) = when (shape) {
    ShapeKind.BOX -> mapOf(Dim.L to 1.0, Dim.W to 0.68, Dim.H to 0.58)
    ShapeKind.CYLINDER -> mapOf(Dim.L to 0.7, Dim.W to 0.7, Dim.H to 1.0)
    ShapeKind.SPHERE -> mapOf(Dim.L to 1.0, Dim.W to 1.0, Dim.H to 1.0)
}

/**
 * Isometric sketch of the object for the edge-sliding flow. The edge to trace next glows while a
 * little phone glides along it; finished edges turn green and carry their value; and the drawing
 * eases into the measured proportions as each dimension comes in.
 */
@Composable
fun EdgeGuide(
    shape: ShapeKind,
    dims: List<Dim>,
    current: Int,
    measured: Map<Dim, Double>,
    live: Double?,
    units: UnitSystem,
    modifier: Modifier = Modifier,
) {
    val c = Datum.colors
    val accent = c.accent
    val measurer = rememberTextMeasurer()

    // Unknown dimensions borrow the scale of the known ones so the sketch stays plausible.
    val defaults = defaultProportions(shape)
    val known = dims.filter { (measured[it] ?: 0.0) > 0.0 }
    val ref = if (known.isEmpty()) 1.0 else known.map { measured.getValue(it) / defaults.getValue(it) }.average()
    val raw = Dim.entries.associateWith { d -> measured[d]?.takeIf { it > 0 } ?: (defaults.getValue(d) * ref) }
    val shared = if (shape == ShapeKind.BOX) raw else raw + (Dim.W to raw.getValue(Dim.L))
    val maxV = shared.values.max()
    fun norm(d: Dim) = (max(shared.getValue(d), maxV * 0.22) / maxV).toFloat()
    val springSpec = spring<Float>(dampingRatio = 0.78f, stiffness = 110f)
    val l by animateFloatAsState(norm(Dim.L), springSpec, label = "l")
    val w by animateFloatAsState(norm(Dim.W), springSpec, label = "w")
    val h by animateFloatAsState(norm(Dim.H), springSpec, label = "h")

    val loop = rememberInfiniteTransition(label = "guide")
    val glide by loop.animateFloat(
        0f, 1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 2300
                0f at 0
                0f at 380 using FastOutSlowInEasing
                1f at 1800
                1f at 2300
            },
            RepeatMode.Restart,
        ),
        label = "glide",
    )
    val pulse by loop.animateFloat(0.35f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "pulse")

    val labelStyle = TextStyle(fontFamily = InterText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White, fontFeatureSettings = "tnum")
    val pendingStyle = labelStyle.copy(color = c.labelSecondary)

    Canvas(modifier) {
        // ---- Model-space geometry -------------------------------------------------------------
        val outline = mutableListOf<Offset>()
        val edges = mutableListOf<GuideEdge>()
        val rx = 1.2247449f * l / 2 // a horizontal circle of diameter l, seen isometrically
        val ry = 0.70710677f * l / 2
        when (shape) {
            ShapeKind.BOX -> {
                val pts = listOf(
                    iso(0f, 0f, 0f), iso(l, 0f, 0f), iso(0f, 0f, w), iso(l, 0f, w),
                    iso(0f, h, 0f), iso(l, h, 0f), iso(0f, h, w), iso(l, h, w),
                )
                outline += pts
                // All three start from the left-bottom corner region and stay on the silhouette or
                // the near faces, so the labels never pile up on the front corner.
                edges += GuideEdge(Dim.L, iso(0f, 0f, w), iso(l, 0f, w), Offset(-SIN30, COS30))
                edges += GuideEdge(Dim.W, iso(l, 0f, w), iso(l, 0f, 0f), Offset(SIN30, COS30))
                edges += GuideEdge(Dim.H, iso(0f, 0f, w), iso(0f, h, w), Offset(-1f, 0f))
            }
            ShapeKind.CYLINDER -> {
                outline += listOf(Offset(-rx, ry), Offset(rx, ry), Offset(-rx, -h - ry), Offset(rx, -h - ry))
                edges += GuideEdge(Dim.L, Offset(-rx, -h), Offset(rx, -h))
                edges += GuideEdge(Dim.H, Offset(rx, 0f), Offset(rx, -h), Offset(1f, 0f))
            }
            ShapeKind.SPHERE -> {
                val r = l / 2
                outline += listOf(Offset(-r, -r), Offset(r, r))
                edges += GuideEdge(Dim.L, Offset(-r, 0f), Offset(r, 0f))
            }
        }

        // ---- Fit to the card, leaving room for the labels ------------------------------------
        val padX = 52.dp.toPx()
        val padY = 30.dp.toPx()
        val minX = outline.minOf { it.x }
        val maxX = outline.maxOf { it.x }
        val minY = outline.minOf { it.y }
        val maxY = outline.maxOf { it.y }
        val s = min((size.width - 2 * padX) / (maxX - minX), (size.height - 2 * padY) / (maxY - minY))
        val ox = (size.width - (maxX - minX) * s) / 2 - minX * s
        val oy = (size.height - (maxY - minY) * s) / 2 - minY * s
        fun t(o: Offset) = Offset(ox + o.x * s, oy + o.y * s)
        fun poly(p: List<Offset>) = Path().apply {
            p.forEachIndexed { i, o -> t(o).let { if (i == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }

        val line = c.label.copy(alpha = 0.32f)
        val hidden = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))
        val thin = 1.2.dp.toPx()

        // ---- Body -----------------------------------------------------------------------------
        when (shape) {
            ShapeKind.BOX -> {
                val top = poly(listOf(iso(0f, h, 0f), iso(l, h, 0f), iso(l, h, w), iso(0f, h, w)))
                val left = poly(listOf(iso(0f, 0f, w), iso(l, 0f, w), iso(l, h, w), iso(0f, h, w)))
                val right = poly(listOf(iso(l, 0f, 0f), iso(l, 0f, w), iso(l, h, w), iso(l, h, 0f)))
                drawPath(top, accent.copy(alpha = 0.06f))
                drawPath(left, accent.copy(alpha = 0.12f))
                drawPath(right, accent.copy(alpha = 0.19f))
                for ((a, b) in listOf(
                    iso(0f, 0f, 0f) to iso(l, 0f, 0f), iso(0f, 0f, 0f) to iso(0f, 0f, w), iso(0f, 0f, 0f) to iso(0f, h, 0f),
                )) drawLine(c.labelTertiary, t(a), t(b), thin, pathEffect = hidden)
                listOf(top, left, right).forEach { drawPath(it, line, style = Stroke(thin)) }
            }
            ShapeKind.CYLINDER -> {
                val bottom = t(Offset(0f, 0f))
                val topC = t(Offset(0f, -h))
                val erx = rx * s
                val ery = ry * s
                val body = Path().apply {
                    moveTo(bottom.x - erx, topC.y)
                    lineTo(bottom.x - erx, bottom.y)
                    arcTo(androidx.compose.ui.geometry.Rect(bottom.x - erx, bottom.y - ery, bottom.x + erx, bottom.y + ery), 180f, -180f, false)
                    lineTo(bottom.x + erx, topC.y)
                    close()
                }
                drawPath(body, accent.copy(alpha = 0.14f))
                drawPath(body, line, style = Stroke(thin))
                // Back half of the base is hidden.
                drawArc(c.labelTertiary, 180f, 180f, false, Offset(bottom.x - erx, bottom.y - ery), Size(2 * erx, 2 * ery), style = Stroke(thin, pathEffect = hidden))
                // Opaque base first so the top face reads as one flat lid over the body.
                drawOval(c.surface, Offset(topC.x - erx, topC.y - ery), Size(2 * erx, 2 * ery))
                drawOval(accent.copy(alpha = 0.07f), Offset(topC.x - erx, topC.y - ery), Size(2 * erx, 2 * ery))
                drawOval(line, Offset(topC.x - erx, topC.y - ery), Size(2 * erx, 2 * ery), style = Stroke(thin))
            }
            ShapeKind.SPHERE -> {
                val center = t(Offset(0f, 0f))
                val r = l / 2 * s
                drawCircle(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.05f), accent.copy(alpha = 0.2f)),
                        center = Offset(center.x - r * 0.35f, center.y - r * 0.4f), radius = r * 1.6f,
                    ),
                    r, center,
                )
                drawCircle(line, r, center, style = Stroke(thin))
                drawArc(c.labelTertiary, 180f, 180f, false, Offset(center.x - r, center.y - r * 0.32f), Size(2 * r, 0.64f * r), style = Stroke(thin, pathEffect = hidden))
                drawArc(line, 0f, 180f, false, Offset(center.x - r, center.y - r * 0.32f), Size(2 * r, 0.64f * r), style = Stroke(thin))
            }
        }

        // ---- Dimension edges --------------------------------------------------------------------
        val ordered = edges.filter { it.dim in dims }.sortedBy { dims.indexOf(it.dim) }
        val labels = mutableListOf<GuideLabel>()
        ordered.forEachIndexed { i, e ->
            val a = t(e.a)
            val b = t(e.b)
            val mid = Offset((a.x + b.x) / 2, (a.y + b.y) / 2)
            fun label(text: String, fill: Color, style: TextStyle) { labels += GuideLabel(text, mid, e.normal, fill, style) }
            val value = measured[e.dim]
            when {
                value != null && i != current -> {
                    drawLine(c.green, a, b, 3.dp.toPx(), StrokeCap.Round)
                    label(Fmt.length(value, units).toString(), c.green, labelStyle)
                }
                i == current -> {
                    if (live != null) {
                        drawLine(accent.copy(alpha = 0.25f * pulse), a, b, 12.dp.toPx(), StrokeCap.Round)
                        drawLine(accent, a, b, 3.5.dp.toPx(), StrokeCap.Round)
                        label(Fmt.length(live, units).toString(), accent, labelStyle)
                    } else {
                        val pos = a + (b - a) * glide
                        drawLine(accent.copy(alpha = 0.18f), a, b, 10.dp.toPx(), StrokeCap.Round)
                        drawLine(accent.copy(alpha = 0.45f), a, b, 2.dp.toPx(), pathEffect = hidden)
                        drawLine(accent, a, pos, 3.5.dp.toPx(), StrokeCap.Round)
                        // Arrowhead showing which way to glide.
                        val ang = atan2(b.y - a.y, b.x - a.x)
                        val head = 9.dp.toPx()
                        for (sgn in listOf(-1, 1)) {
                            val th = ang + Math.PI.toFloat() + sgn * 0.5f
                            drawLine(accent, b, Offset(b.x + cos(th) * head, b.y + sin(th) * head), 2.dp.toPx(), StrokeCap.Round)
                        }
                        // The phone, lying along the edge.
                        drawCircle(accent.copy(alpha = 0.22f), 16.dp.toPx(), pos)
                        rotate(Math.toDegrees(ang.toDouble()).toFloat(), pivot = pos) {
                            val pw = 30.dp.toPx()
                            val ph = 13.dp.toPx()
                            val tl = Offset(pos.x - pw / 2, pos.y - ph / 2)
                            val cr = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                            drawRoundRect(c.surface, tl, Size(pw, ph), cr)
                            drawRoundRect(accent, tl, Size(pw, ph), cr, style = Stroke(2.dp.toPx()))
                        }
                        label(dimName(e.dim, shape), accent, labelStyle)
                    }
                }
                else -> {
                    drawLine(c.labelTertiary, a, b, 2.dp.toPx(), StrokeCap.Round)
                    label(dimName(e.dim, shape), c.fillSecondary, pendingStyle)
                }
            }
        }
        // Labels last so no line ever crosses them; each clears its edge by its own half-extent.
        val gap = 5.dp.toPx()
        for (lb in labels) {
            val m = measurer.measure(lb.text, lb.style)
            val halfW = m.size.width / 2f + 8.dp.toPx()
            val halfH = m.size.height / 2f + 3.dp.toPx()
            val push = if (lb.normal == Offset.Zero) 0f else gap + abs(lb.normal.x) * halfW + abs(lb.normal.y) * halfH
            pill(measurer, lb.text, lb.style, lb.fill, lb.anchor + lb.normal * push)
        }
    }
}
