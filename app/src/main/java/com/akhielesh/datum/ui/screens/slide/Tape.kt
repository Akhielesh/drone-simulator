package com.akhielesh.datum.ui.screens.slide

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akhielesh.datum.core.math.Vec2
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.InterText
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class TapeMark(val label: String, val at: Double)

/**
 * A measuring tape pinned to the table: drawn at true physical scale ([pxPerMeter] from the
 * panel's real density) and scrolled opposite to the phone's motion, so the graduations appear to
 * stay still on the surface while the phone glides over them.
 *
 * [offset] is the phone's displacement along the tape (m): toward the top of the screen when
 * vertical, toward the right when [horizontal].
 */
@Composable
fun TableTape(
    offset: Double,
    pxPerMeter: Float,
    units: UnitSystem,
    marks: List<TapeMark>,
    modifier: Modifier = Modifier,
    horizontal: Boolean = false,
    needleFraction: Float = 0.5f,
    accent: Color = Datum.colors.accent,
) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = InterText, fontSize = 11.sp, color = c.labelSecondary, fontFeatureSettings = "tnum")
    val markStyle = TextStyle(fontFamily = InterText, fontSize = 12.sp, color = Color.White, fontFeatureSettings = "tnum")
    Canvas(modifier) {
        val mainLen = if (horizontal) size.width else size.height
        val needle = mainLen * needleFraction
        // Main-axis screen coordinate of table position t.
        fun at(t: Double): Float = if (horizontal) {
            (needle + (t - offset) * pxPerMeter).toFloat()
        } else {
            (needle - (t - offset) * pxPerMeter).toFloat()
        }
        val tMin = if (horizontal) offset - needle / pxPerMeter else offset - (mainLen - needle) / pxPerMeter
        val tMax = if (horizontal) offset + (mainLen - needle) / pxPerMeter else offset + needle / pxPerMeter

        fun tick(m: Float, len: Float, color: Color, width: Float) {
            if (horizontal) {
                drawLine(color, Offset(m, 0f), Offset(m, len), width)
                drawLine(color, Offset(m, size.height - len), Offset(m, size.height), width)
            } else {
                drawLine(color, Offset(0f, m), Offset(len, m), width)
                drawLine(color, Offset(size.width - len, m), Offset(size.width, m), width)
            }
        }

        fun across(m: Float, color: Color, width: Float, effect: PathEffect? = null) {
            if (horizontal) {
                drawLine(color, Offset(m, 0f), Offset(m, size.height), width, pathEffect = effect)
            } else {
                drawLine(color, Offset(0f, m), Offset(size.width, m), width, pathEffect = effect)
            }
        }

        fun tickLabel(text: String, m: Float, len: Float) {
            val layout = measurer.measure(text, labelStyle)
            val topLeft = if (horizontal) {
                Offset(m - layout.size.width / 2f, len + 4.dp.toPx())
            } else {
                Offset(len + 6.dp.toPx(), m - layout.size.height / 2f)
            }
            drawText(layout, topLeft = topLeft)
        }

        fun centerOf(m: Float, crossFraction: Float) =
            if (horizontal) Offset(m, size.height * crossFraction) else Offset(size.width * crossFraction, m)

        val tickColor = c.labelSecondary
        val minor = c.labelTertiary
        if (units == UnitSystem.METRIC) {
            // 1 mm ticks, 5 mm medium, 1 cm long with a label.
            for (mm in floor(tMin * 1000).toLong()..ceil(tMax * 1000).toLong()) {
                val m = at(mm / 1000.0)
                val len = when {
                    mm % 10 == 0L -> 30.dp.toPx()
                    mm % 5 == 0L -> 20.dp.toPx()
                    else -> 12.dp.toPx()
                }
                val major = mm % 10 == 0L
                tick(m, len, if (major) tickColor else minor, if (major) 1.4.dp.toPx() else 1.dp.toPx())
                if (major) tickLabel(signed(mm / 10), m, len)
            }
        } else {
            // 1/16 in ticks with the usual length hierarchy.
            val per = 0.0254 / 16
            for (k in floor(tMin / per).toLong()..ceil(tMax / per).toLong()) {
                val m = at(k * per)
                val len = when {
                    k % 16 == 0L -> 32.dp.toPx()
                    k % 8 == 0L -> 24.dp.toPx()
                    k % 4 == 0L -> 18.dp.toPx()
                    k % 2 == 0L -> 13.dp.toPx()
                    else -> 9.dp.toPx()
                }
                tick(m, len, if (k % 16 == 0L) tickColor else minor, 1.dp.toPx())
                if (k % 16 == 0L) tickLabel("${signed(k / 16)}″", m, len)
            }
        }
        // The table-fixed start.
        val m0 = at(0.0)
        val startVisible = m0 in -2f..mainLen + 2f
        if (startVisible) across(m0, c.green, 2.dp.toPx(), PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        // Marks left behind on the table.
        val visibleMarks = marks.filter { at(it.at) in -20f..mainLen + 20f }
        for (mk in visibleMarks) across(at(mk.at), c.orange, 1.6.dp.toPx())
        // The needle: where the phone is now.
        across(needle, accent.copy(alpha = 0.22f), 10.dp.toPx())
        across(needle, accent, 2.dp.toPx())
        // Labels ride on top of every line so the needle never strikes through them.
        if (startVisible) pill(measurer, "START", markStyle, c.green, centerOf(m0, 0.5f))
        for (mk in visibleMarks) pill(measurer, mk.label, markStyle, c.orange, centerOf(at(mk.at), 0.72f))
        val tri = 9.dp.toPx()
        if (horizontal) {
            drawPath(Path().apply { moveTo(needle - tri, 0f); lineTo(needle, tri * 1.2f); lineTo(needle + tri, 0f); close() }, accent)
            drawPath(Path().apply { moveTo(needle - tri, size.height); lineTo(needle, size.height - tri * 1.2f); lineTo(needle + tri, size.height); close() }, accent)
        } else {
            drawPath(Path().apply { moveTo(0f, needle - tri); lineTo(tri * 1.2f, needle); lineTo(0f, needle + tri); close() }, accent)
            drawPath(Path().apply { moveTo(size.width, needle - tri); lineTo(size.width - tri * 1.2f, needle); lineTo(size.width, needle + tri); close() }, accent)
        }
    }
}

/**
 * Graph paper pinned to the table for free 2D sliding, with the travelled path and marks.
 * [position] and [path] are table coordinates (m, x right / y forward at the start pose);
 * [yaw] is the phone's rotation about the vertical since the start (rad).
 */
@Composable
fun GraphPaper(
    position: Vec2,
    yaw: Double,
    path: List<Vec2>,
    marks: List<Pair<String, Vec2>>,
    pxPerMeter: Float,
    modifier: Modifier = Modifier,
) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val markStyle = TextStyle(fontFamily = InterText, fontSize = 12.sp, color = Color.White, fontFeatureSettings = "tnum")
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val cosY = cos(-yaw)
        val sinY = sin(-yaw)
        fun toScreen(p: Vec2): Offset {
            val dx = p.x - position.x
            val dy = p.y - position.y
            val rx = dx * cosY - dy * sinY
            val ry = dx * sinY + dy * cosY
            return Offset((center.x + rx * pxPerMeter).toFloat(), (center.y - ry * pxPerMeter).toFloat())
        }
        val reach = (kotlin.math.hypot(size.width.toDouble(), size.height.toDouble()) / pxPerMeter) / 2 + 0.01
        val step = 0.01
        val x0 = floor((position.x - reach) / step).toInt()
        val x1 = ceil((position.x + reach) / step).toInt()
        val y0 = floor((position.y - reach) / step).toInt()
        val y1 = ceil((position.y + reach) / step).toInt()
        for (i in x0..x1) {
            val major = i % 10 == 0
            val col = if (major) c.separator.copy(alpha = c.separator.alpha * 2.2f) else c.separator.copy(alpha = c.separator.alpha * 0.9f)
            drawLine(col, toScreen(Vec2(i * step, y0 * step)), toScreen(Vec2(i * step, y1 * step)), if (major) 1.3f else 0.7f)
        }
        for (j in y0..y1) {
            val major = j % 10 == 0
            val col = if (major) c.separator.copy(alpha = c.separator.alpha * 2.2f) else c.separator.copy(alpha = c.separator.alpha * 0.9f)
            drawLine(col, toScreen(Vec2(x0 * step, j * step)), toScreen(Vec2(x1 * step, j * step)), if (major) 1.3f else 0.7f)
        }
        if (path.size > 1) {
            val p = Path()
            path.forEachIndexed { i, v ->
                val s = toScreen(v)
                if (i == 0) p.moveTo(s.x, s.y) else p.lineTo(s.x, s.y)
            }
            val now = toScreen(position)
            p.lineTo(now.x, now.y)
            drawPath(p, c.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
        val origin = toScreen(Vec2(0.0, 0.0))
        drawCircle(c.green, 7.dp.toPx(), origin)
        drawCircle(Color.White, 3.dp.toPx(), origin)
        for ((label, at) in marks) {
            val s = toScreen(at)
            drawCircle(c.orange, 6.dp.toPx(), s)
            pill(measurer, label, markStyle, c.orange, Offset(s.x, s.y - 18.dp.toPx()))
        }
        // Phone reference point (screen centre).
        drawCircle(c.accent.copy(alpha = 0.2f), 18.dp.toPx(), center)
        drawCircle(c.accent, 6.dp.toPx(), center)
        drawCircle(Color.White, 2.5.dp.toPx(), center)
    }
}

/** Graduation label with a true minus sign (the hyphen is too short and sits low). */
private fun signed(n: Long) = if (n < 0) "\u2212${-n}" else "$n"

/** A capsule label centred on [at]. */
fun DrawScope.pill(measurer: TextMeasurer, text: String, style: TextStyle, color: Color, at: Offset) {
    val layout = measurer.measure(text, style)
    val padX = 8.dp.toPx()
    val padY = 3.dp.toPx()
    val w = layout.size.width + padX * 2
    val h = layout.size.height + padY * 2
    drawRoundRect(
        color,
        topLeft = Offset(at.x - w / 2, at.y - h / 2),
        size = androidx.compose.ui.geometry.Size(w, h),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2, h / 2),
    )
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
