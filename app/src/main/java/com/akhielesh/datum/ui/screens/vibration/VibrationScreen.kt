package com.akhielesh.datum.ui.screens.vibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.sensors.VibrationEngine
import com.akhielesh.datum.core.sensors.mercalli
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.common.EngineLifecycle
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SectionCard
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.InterText
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlin.math.max

@Composable
fun VibrationScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val c = Datum.colors
    KeepScreenOn()
    val engine = remember { VibrationEngine(app.sensors) }
    EngineLifecycle(engine, engine::start, engine::stop)
    val s by engine.state.collectAsStateWithLifecycle()
    val pgaG = s.peak / 9.80665
    val (mmi, mmiText) = mercalli(pgaG)
    Column(Modifier.fillMaxSize()) {
        TopBar("Vibration", subtitle = "Set the phone on the machine", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Readout("%.3f".format(s.rms), "m/s² RMS", style = Datum.type.readoutL)
                StatusChip("Intensity $mmi · $mmiText", if (pgaG < 0.014) c.green else if (pgaG < 0.092) c.orange else c.red)
            }
            Spacer(Modifier.height(16.dp))
            Seismograph(s.trace, Modifier.fillMaxWidth().height(170.dp).card(Shapes.xl))
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Spectrum · 0–%.0f Hz".format(s.nyquistHz)) {
                Spectrum(s.spectrum, s.nyquistHz, s.dominantHz, Modifier.fillMaxWidth().height(120.dp).padding(14.dp))
            }
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Readings") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    KeyValueRow("Dominant frequency", if (s.dominantHz > 0) "%.1f".format(s.dominantHz) else "—", unit = "Hz", hint = if (s.dominantHz > 0) "≈ %.0f RPM".format(s.dominantHz * 60) else "no clear peak")
                    KeyValueRow("Peak", "%.3f".format(s.peak), unit = "m/s²", hint = "%.4f g".format(pgaG))
                    KeyValueRow("Sample rate", "%.0f".format(s.sampleRateHz), unit = "Hz")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(DatumIcons.Download, "Save", {
                app.library.add(
                    Measurement(
                        kind = MeasureKind.VIBRATION, title = "Vibration", value = s.rms, quantity = Quantity.ACCEL, method = "Accelerometer + FFT",
                        details = listOf(
                            Detail("Peak (m/s²)", s.peak, Quantity.PLAIN),
                            Detail("Dominant (Hz)", s.dominantHz, Quantity.PLAIN),
                        ),
                    ),
                )
                hud.show(DatumIcons.Check, "Saved")
            })
        }
    }
}

@Composable
private fun Seismograph(trace: FloatArray, modifier: Modifier) {
    val c = Datum.colors
    Canvas(modifier.padding(12.dp)) {
        drawLine(c.separator, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.dp.toPx())
        if (trace.size < 2) return@Canvas
        val peak = max(trace.max(), 0.05f)
        val step = size.width / (trace.size - 1)
        val path = Path()
        // Alternate the sign so the magnitude reads like a classic seismograph trace.
        trace.forEachIndexed { i, v ->
            val y = size.height / 2 - (if (i % 2 == 0) v else -v) / peak * size.height * 0.45f
            if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
        }
        drawPath(path, c.accent.copy(alpha = 0.25f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, c.accent, style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun Spectrum(bars: FloatArray, nyquist: Double, dominant: Double, modifier: Modifier) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = InterText, fontSize = 10.sp, color = c.labelTertiary)
    Canvas(modifier) {
        if (bars.isEmpty()) {
            val l = measurer.measure("Collecting samples…", style)
            drawText(l, topLeft = Offset(size.width / 2 - l.size.width / 2, size.height / 2 - l.size.height / 2))
            return@Canvas
        }
        val maxV = max(bars.max(), 0.002f)
        val w = size.width / bars.size
        val labelH = 14.dp.toPx()
        bars.forEachIndexed { i, v ->
            val h = (v / maxV) * (size.height - labelH)
            val hz = (i + 0.5) / bars.size * nyquist
            val isDom = dominant > 0 && kotlin.math.abs(hz - dominant) < nyquist / bars.size
            drawRoundRect(
                if (isDom) Brush.verticalGradient(listOf(c.orange, c.orange.copy(alpha = 0.6f))) else Brush.verticalGradient(listOf(c.accent, c.accent.copy(alpha = 0.45f))),
                Offset(i * w + w * 0.15f, size.height - labelH - h), Size(w * 0.7f, h.coerceAtLeast(1f)), CornerRadius(w * 0.3f),
            )
        }
        listOf(0.0, 0.5, 1.0).forEach { f ->
            val l = measurer.measure("%.0f Hz".format(f * nyquist), style)
            drawText(l, topLeft = Offset((f * size.width - l.size.width * f).toFloat(), size.height - l.size.height))
        }
    }
}
