package com.akhielesh.datum.ui.screens.ruler

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.GlassSheet
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.MacSlider
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.GlassWeight
import com.akhielesh.datum.ui.theme.InterText
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot

private enum class RulerMode(val label: String) { Ruler("Ruler"), Calipers("Calipers"), Circle("Circle") }

@Composable
fun RulerScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    KeepScreenOn()
    val pxPerMm = (app.physicalPpi / 25.4).toFloat()
    var mode by remember { mutableStateOf(RulerMode.Ruler) }
    var line by remember { mutableFloatStateOf(0f) }
    var jawA by remember { mutableFloatStateOf(0f) }
    var jawB by remember { mutableFloatStateOf(0f) }
    var radius by remember { mutableFloatStateOf(0f) }
    var showCal by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()
    val metricStyle = TextStyle(fontFamily = InterText, fontSize = 11.sp, color = c.label, fontFeatureSettings = "tnum")
    val minor = TextStyle(fontFamily = InterText, fontSize = 10.sp, color = c.labelSecondary, fontFeatureSettings = "tnum")

    val valueMm: Float = when (mode) {
        RulerMode.Ruler -> line / pxPerMm
        RulerMode.Calipers -> abs(jawB - jawA) / pxPerMm
        RulerMode.Circle -> 2 * radius / pxPerMm
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(
            Modifier
                .fillMaxSize()
                .onSizeChanged { sz ->
                    if (jawB == 0f) {
                        jawA = sz.height * 0.30f
                        jawB = sz.height * 0.30f + 25 * pxPerMm
                        line = sz.height * 0.4f
                        radius = 12 * pxPerMm
                    }
                }
                .pointerInput(mode) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height * 0.45f)
                        // Which handle does this gesture move?
                        val target = when (mode) {
                            RulerMode.Ruler -> 0
                            RulerMode.Calipers -> if (abs(down.position.y - jawA) < abs(down.position.y - jawB)) 1 else 2
                            RulerMode.Circle -> 3
                        }
                        fun apply(p: Offset) {
                            when (target) {
                                0 -> line = p.y.coerceIn(0f, size.height.toFloat())
                                1 -> jawA = p.y.coerceIn(0f, size.height.toFloat())
                                2 -> jawB = p.y.coerceIn(0f, size.height.toFloat())
                                3 -> radius = hypot(p.x - center.x, p.y - center.y).coerceAtMost(size.width / 2f - 8f)
                            }
                        }
                        apply(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull() ?: break
                            if (!ch.pressed) break
                            apply(ch.position)
                            ch.consume()
                        }
                        app.haptics.tick(0.5f)
                    }
                },
        ) {
            drawScales(pxPerMm, metricStyle, minor, measurer)
            when (mode) {
                RulerMode.Ruler -> {
                    drawRect(c.accent.copy(alpha = 0.08f), Offset.Zero, Size(size.width, line))
                    drawLine(c.accent, Offset(0f, line), Offset(size.width, line), 2.dp.toPx())
                    drawCircle(c.accent, 9.dp.toPx(), Offset(size.width / 2, line))
                    drawCircle(Color.White, 4.dp.toPx(), Offset(size.width / 2, line))
                }
                RulerMode.Calipers -> {
                    val top = minOf(jawA, jawB)
                    val bottom = maxOf(jawA, jawB)
                    drawRect(c.accent.copy(alpha = 0.10f), Offset(0f, top), Size(size.width, bottom - top))
                    listOf(jawA, jawB).forEach { y ->
                        drawLine(c.accent, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        drawRoundRect(c.accent, Offset(size.width / 2 - 26.dp.toPx(), y - 9.dp.toPx()), Size(52.dp.toPx(), 18.dp.toPx()), CornerRadius(9.dp.toPx()))
                        drawLine(Color.White, Offset(size.width / 2 - 10.dp.toPx(), y), Offset(size.width / 2 + 10.dp.toPx(), y), 2.dp.toPx())
                    }
                }
                RulerMode.Circle -> {
                    val center = Offset(size.width / 2f, size.height * 0.45f)
                    drawCircle(c.accent.copy(alpha = 0.10f), radius, center)
                    drawCircle(c.accent, radius, center, style = Stroke(2.dp.toPx()))
                    drawLine(c.accent.copy(alpha = 0.6f), Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
                    drawCircle(c.accent, 10.dp.toPx(), Offset(center.x + radius, center.y))
                    drawCircle(Color.White, 4.dp.toPx(), Offset(center.x + radius, center.y))
                }
            }
        }
        Column(Modifier.fillMaxSize()) {
            TopBar("Ruler", subtitle = "True scale · %.0f ppi".format(app.physicalPpi), onBack = { nav.back() })
            Spacer(Modifier.weight(1f))
            Column(
                Modifier
                    .padding(horizontal = 54.dp)
                    .fillMaxWidth()
                    .glass(Shapes.xl, GlassWeight.Thick, elevation = 10.dp)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val primary = Fmt.length(valueMm / 1000.0, settings.units, settings.inchFractions)
                Readout((if (mode == RulerMode.Circle) "Ø " else "") + primary.value, primary.unit, style = Datum.type.readoutM)
                val other = if (settings.units == UnitSystem.METRIC) UnitSystem.IMPERIAL else UnitSystem.METRIC
                DText(
                    Fmt.length(valueMm / 1000.0, other).toString() + when (mode) {
                        RulerMode.Ruler -> "  ·  from the top edge of the screen"
                        RulerMode.Calipers -> "  ·  between the jaws"
                        RulerMode.Circle -> "  ·  circumference " + Fmt.length(Math.PI * valueMm / 1000.0, settings.units)
                    },
                    Datum.type.footnote, c.labelSecondary, maxLines = 1,
                )
            }
            Spacer(Modifier.height(14.dp))
            SegmentedControl(
                RulerMode.entries.map { it.label }, mode.ordinal, onSelect = { mode = RulerMode.entries[it] },
                modifier = Modifier.padding(horizontal = 54.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 54.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToolButton(DatumIcons.Sparkles, "Calibrate", { showCal = true })
                ToolButton(DatumIcons.Download, "Save", {
                    app.library.add(
                        Measurement(
                            kind = MeasureKind.RULER,
                            title = when (mode) {
                                RulerMode.Ruler -> "Screen ruler"
                                RulerMode.Calipers -> "Calipers"
                                RulerMode.Circle -> "Diameter"
                            },
                            value = valueMm / 1000.0,
                            quantity = Quantity.LENGTH,
                            method = "Screen at %.0f ppi".format(app.physicalPpi),
                            details = if (mode == RulerMode.Circle) listOf(Detail("Circumference", Math.PI * valueMm / 1000.0, Quantity.LENGTH)) else emptyList(),
                        ),
                    )
                    hud.show(DatumIcons.Check, "Saved")
                })
            }
        }
        CardCalibrationSheet(showCal) { showCal = false }
    }
}

/** Millimetre scale down the left edge, sixteenth-inch scale down the right. */
private fun DrawScope.drawScales(
    pxPerMm: Float,
    metric: TextStyle,
    minor: TextStyle,
    measurer: androidx.compose.ui.text.TextMeasurer,
) {
    val c1 = metric.color
    val c2 = minor.color
    val totalMm = ceil(size.height / pxPerMm).toInt()
    for (mm in 0..totalMm) {
        val y = mm * pxPerMm
        val len = when {
            mm % 10 == 0 -> 26.dp.toPx()
            mm % 5 == 0 -> 18.dp.toPx()
            else -> 11.dp.toPx()
        }
        drawLine(if (mm % 10 == 0) c1 else c2, Offset(0f, y), Offset(len, y), if (mm % 10 == 0) 1.5f else 1f)
        if (mm % 10 == 0 && mm > 0) {
            val l = measurer.measure("${mm / 10}", metric)
            drawText(l, topLeft = Offset(len + 4.dp.toPx(), y - l.size.height / 2f))
        }
    }
    val pxPerSixteenth = pxPerMm * 25.4f / 16f
    val total = ceil(size.height / pxPerSixteenth).toInt()
    for (k in 0..total) {
        val y = k * pxPerSixteenth
        val len = when {
            k % 16 == 0 -> 28.dp.toPx()
            k % 8 == 0 -> 21.dp.toPx()
            k % 4 == 0 -> 15.dp.toPx()
            k % 2 == 0 -> 11.dp.toPx()
            else -> 7.dp.toPx()
        }
        drawLine(if (k % 16 == 0) c1 else c2, Offset(size.width - len, y), Offset(size.width, y), if (k % 16 == 0) 1.5f else 1f)
        if (k % 16 == 0 && k > 0) {
            val l = measurer.measure("${k / 16}″", metric)
            drawText(l, topLeft = Offset(size.width - len - 4.dp.toPx() - l.size.width, y - l.size.height / 2f))
        }
    }
    val cm = measurer.measure("cm", minor)
    drawText(cm, topLeft = Offset(30.dp.toPx(), 4.dp.toPx() + 40.dp.toPx()))
    val inch = measurer.measure("in", minor)
    drawText(inch, topLeft = Offset(size.width - 32.dp.toPx() - inch.size.width, 44.dp.toPx()))
}

/** Hold a real bank card against the screen and match the outline: fixes any density error. */
@Composable
fun CardCalibrationSheet(visible: Boolean, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    var scale by remember(visible) { mutableFloatStateOf(settings.screenScale.toFloat()) }
    val basePpi = app.physicalPpi / settings.screenScale
    GlassSheet(visible, onDismiss, title = "Calibrate the ruler") {
        DText("Place a bank card on the screen and adjust until the outline matches its edges exactly.", Datum.type.callout, c.labelSecondary)
        Spacer(Modifier.height(14.dp))
        Canvas(Modifier.fillMaxWidth().height(200.dp)) {
            val pxPerMm = (basePpi * scale / 25.4).toFloat()
            val w = 85.60f * pxPerMm
            val h = 53.98f * pxPerMm
            val left = (size.width - w) / 2
            val top = (size.height - h) / 2
            drawRoundRect(c.accent.copy(alpha = 0.12f), Offset(left, top), Size(w, h), CornerRadius(3.18f * pxPerMm))
            drawRoundRect(c.accent, Offset(left, top), Size(w, h), CornerRadius(3.18f * pxPerMm), style = Stroke(2.dp.toPx()))
        }
        Spacer(Modifier.height(8.dp))
        MacSlider(scale, { scale = it }, range = 0.85f..1.15f)
        DText("Scale ×%.3f · %.0f ppi".format(scale, basePpi * scale), Datum.type.footnote, c.labelSecondary)
        Spacer(Modifier.height(12.dp))
        PillButton("Save calibration", onClick = {
            app.settings.update { it.copy(screenScale = scale.toDouble()) }
            hud.show(DatumIcons.Check, "Calibrated")
            onDismiss()
        }, modifier = Modifier.fillMaxWidth())
    }
}
