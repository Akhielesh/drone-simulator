package com.akhielesh.datum.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.ui.theme.Datum
import kotlin.math.max
import kotlin.math.min

/** Filled sparkline; [fixedMin]/[fixedMax] pin the y-range, otherwise it auto-scales. */
@Composable
fun Sparkline(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color = Datum.colors.accent,
    fixedMin: Float? = null,
    fixedMax: Float? = null,
    threshold: Float? = null,
) {
    val sep = Datum.colors.separator
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        var lo = fixedMin ?: values.min()
        var hi = fixedMax ?: values.max()
        if (hi - lo < 1e-6f) {
            hi += 0.5f
            lo -= 0.5f
        }
        fun y(v: Float) = size.height - (v.coerceIn(lo, hi) - lo) / (hi - lo) * size.height
        val step = size.width / (values.size - 1)
        val line = Path()
        values.forEachIndexed { i, v -> if (i == 0) line.moveTo(0f, y(v)) else line.lineTo(i * step, y(v)) }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
        drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        threshold?.let { t ->
            val ty = y(t)
            drawLine(sep, Offset(0f, ty), Offset(size.width, ty), 1.dp.toPx())
        }
        val last = values.last()
        drawCircle(color, 3.5.dp.toPx(), Offset(size.width, y(last)))
    }
}

/** Horizontal level meter with coloured zones (green → yellow → red). */
@Composable
fun ZoneMeter(value: Float, min: Float, max: Float, warn: Float, danger: Float, modifier: Modifier = Modifier, peak: Float? = null) {
    val c = Datum.colors
    Canvas(modifier) {
        val r = size.height / 2
        drawRoundRect(c.fillTertiary, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        fun x(v: Float) = ((v - min) / (max - min)).coerceIn(0f, 1f) * size.width
        val filled = x(value)
        val brush = Brush.horizontalGradient(
            0f to c.green, (x(warn) / size.width) to c.green,
            (x(warn) / size.width + 0.001f) to c.yellow, (x(danger) / size.width) to c.orange,
            (x(danger) / size.width + 0.001f) to c.red, 1f to c.red,
            startX = 0f, endX = size.width,
        )
        drawRoundRect(brush, size = androidx.compose.ui.geometry.Size(max(filled, size.height), size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        peak?.let { p ->
            val px = min(x(p), size.width - 2f)
            drawLine(c.label, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
        }
    }
}
