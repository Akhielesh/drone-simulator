package com.akhielesh.datum.ui.screens.light

import android.hardware.Sensor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.common.rememberSensor
import com.akhielesh.datum.ui.components.EmptyState
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SectionCard
import com.akhielesh.datum.ui.components.Sparkline
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.InterText
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

private val references = listOf(
    0.3 to "Full moon", 10.0 to "Candle", 100.0 to "Living room", 500.0 to "Office",
    5_000.0 to "Overcast", 25_000.0 to "Daylight", 100_000.0 to "Direct sun",
)

private fun plantLight(lux: Double) = when {
    lux < 800 -> "Low light — ferns, snake plants"
    lux < 2_500 -> "Medium — pothos, philodendron"
    lux < 10_000 -> "Bright indirect — most houseplants"
    else -> "Direct sun — succulents, herbs"
}

/** Shutter speed for f/2.8 at ISO 100 from EV100, rounded to a familiar stop. */
private fun shutterAt28(ev: Double): String {
    val t = 2.8 * 2.8 / 2.0.pow(ev)
    return if (t >= 1) "%.0f s".format(t) else "1/%.0f s".format(1 / t)
}

@Composable
fun LightScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val c = Datum.colors
    if (!app.sensors.availability.light) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Light", onBack = { nav.back() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(DatumIcons.Light, "No light sensor", "This phone doesn't expose an ambient light sensor.")
            }
        }
        return
    }
    val raw = rememberSensor(Sensor.TYPE_LIGHT, 100_000) { it.x.toDouble() }
    val history = remember { mutableStateListOf<Float>() }
    var min by remember { mutableStateOf(Double.MAX_VALUE) }
    var max by remember { mutableStateOf(0.0) }
    var sum by remember { mutableStateOf(0.0) }
    var count by remember { mutableStateOf(0) }
    val lux = raw.value
    LaunchedEffect(lux) {
        val v = lux ?: return@LaunchedEffect
        history += log10(max(v, 0.1)).toFloat()
        while (history.size > 120) history.removeAt(0)
        min = minOf(min, v)
        max = max(max, v)
        sum += v
        count++
    }
    val ev = lux?.let { ln(max(it, 0.01) / 2.5) / ln(2.0) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Light", subtitle = "Point the screen at the light", onBack = { nav.back() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val f = lux?.let { Fmt.lux(it) }
                Readout(f?.value ?: "—", f?.unit ?: "lx", style = Datum.type.readoutXL)
                DText(lux?.let { "%.1f foot-candles · EV %.1f".format(it / 10.764, ev) } ?: "Waiting for the sensor…", Datum.type.subhead, c.labelSecondary)
            }
            Spacer(Modifier.height(18.dp))
            LogGauge(lux ?: 0.1, Modifier.fillMaxWidth().height(88.dp))
            Spacer(Modifier.height(18.dp))
            SectionCard(title = "Trend") {
                Sparkline(history.toList(), Modifier.fillMaxWidth().height(80.dp).padding(14.dp), color = c.orange, fixedMin = -1f, fixedMax = 5f)
            }
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "What it means") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    KeyValueRow("Plants", lux?.let { plantLight(it).substringBefore(" —") } ?: "—", hint = lux?.let { plantLight(it).substringAfter("— ") })
                    KeyValueRow("Camera (f/2.8, ISO 100)", ev?.let { shutterAt28(it) } ?: "—")
                    KeyValueRow("Reading / desk work", if ((lux ?: 0.0) >= 300) "Comfortable" else "Too dim", accent = if ((lux ?: 0.0) >= 300) c.green else c.orange, hint = "300–500 lx recommended")
                    if (count > 0) {
                        KeyValueRow("Min · avg · max", "%s · %s · %s".format(Fmt.lux(min), Fmt.lux(sum / count), Fmt.lux(max)))
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(DatumIcons.Reset, "Reset", {
                history.clear(); min = Double.MAX_VALUE; max = 0.0; sum = 0.0; count = 0
            })
            ToolButton(DatumIcons.Download, "Save", {
                val v = lux ?: return@ToolButton
                app.library.add(
                    Measurement(
                        kind = MeasureKind.LIGHT, title = "Illuminance", value = v, quantity = Quantity.LUX, method = "Ambient light sensor",
                        details = listOf(Detail("EV100", ev ?: 0.0, Quantity.PLAIN), Detail("Foot-candles", v / 10.764, Quantity.PLAIN)),
                    ),
                )
                hud.show(DatumIcons.Check, "Saved")
            })
        }
    }
}

/** Logarithmic illuminance scale (0.1 lx … 100 klx) with real-world reference marks. */
@Composable
private fun LogGauge(lux: Double, modifier: Modifier) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = InterText, fontSize = 10.sp, color = c.labelSecondary)
    val pos = remember { Animatable(0f) }
    val target = ((log10(max(lux, 0.1)) + 1) / 6).toFloat().coerceIn(0f, 1f)
    LaunchedEffect(target) { pos.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 200f)) }
    Canvas(modifier) {
        val barTop = 26.dp.toPx()
        val barH = 14.dp.toPx()
        drawRoundRect(
            Brush.horizontalGradient(listOf(Color(0xFF1B1F3B), Color(0xFF4B4F8A), Color(0xFFE8B04B), Color(0xFFFFF4C2))),
            Offset(0f, barTop), Size(size.width, barH), CornerRadius(barH / 2),
        )
        for ((v, name) in references) {
            val x = ((log10(v) + 1) / 6 * size.width).toFloat()
            drawLine(c.labelTertiary, Offset(x, barTop + barH + 3.dp.toPx()), Offset(x, barTop + barH + 9.dp.toPx()), 1.dp.toPx())
            val l = measurer.measure(name, style)
            drawText(l, topLeft = Offset((x - l.size.width / 2).coerceIn(0f, size.width - l.size.width), barTop + barH + 11.dp.toPx()))
        }
        val x = pos.value * size.width
        drawCircle(Color.Black.copy(alpha = 0.25f), 12.dp.toPx(), Offset(x, barTop + barH / 2 + 1.dp.toPx()))
        drawCircle(Color.White, 11.dp.toPx(), Offset(x, barTop + barH / 2))
        drawCircle(c.orange, 5.dp.toPx(), Offset(x, barTop + barH / 2))
    }
}
