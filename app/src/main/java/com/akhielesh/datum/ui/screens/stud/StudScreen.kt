package com.akhielesh.datum.ui.screens.stud

import android.hardware.Sensor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.common.rememberSensor
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.EmptyState
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.MacSlider
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.Sparkline
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

@Composable
fun StudScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val c = Datum.colors
    KeepScreenOn()
    if (!app.sensors.availability.magnetometer) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Stud Finder", onBack = { nav.back() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(DatumIcons.Magnet, "No magnetometer", "This phone has no magnetic sensor, so it can't detect metal.")
            }
        }
        return
    }
    val field = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD, 20_000) { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }
    var baseline by remember { mutableStateOf<Float?>(null) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    val history = remember { mutableStateListOf<Float>() }
    var sound by remember { mutableStateOf(true) }
    val b = field.value
    LaunchedEffect(b) { if (baseline == null && b != null) baseline = b }
    val delta = if (b != null && baseline != null) abs(b - baseline!!) else 0f
    // Sensitivity sets the "full scale": 10 µT (very sensitive) … 60 µT.
    val fullScale = 60f - 50f * sensitivity
    val strength = (delta / fullScale).coerceIn(0f, 1f)
    val detected = delta > fullScale * 0.18f
    LaunchedEffect(b) {
        if (b != null) {
            history += delta
            while (history.size > 150) history.removeAt(0)
        }
    }
    // Feedback pulses quicken with signal strength (like a metal detector).
    val currentStrength by rememberUpdatedState(strength)
    val soundOn by rememberUpdatedState(sound)
    LaunchedEffect(Unit) {
        while (true) {
            val s = currentStrength
            if (s > 0.18f) {
                app.haptics.tick(0.3f + 0.7f * s)
                if (soundOn) app.tones.beep(600.0 + 1200.0 * s, 0.03, force = true)
                delay((520 - 440 * s).toLong())
            } else {
                delay(120)
            }
        }
    }
    val tint by animateColorAsState(if (detected) c.orange else c.accent, tween(250), label = "studTint")

    Column(Modifier.fillMaxSize()) {
        TopBar("Stud Finder", subtitle = "Magnetometer", onBack = { nav.back() })
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Radar(strength, tint)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Readout("%.1f".format(delta), "µT", style = Datum.type.readoutL)
                StatusChip(if (detected) "Metal detected" else "Searching…", if (detected) c.orange else c.labelSecondary, pulsing = detected)
            }
        }
        Column(Modifier.padding(horizontal = 18.dp)) {
            Column(Modifier.fillMaxWidth().card(Shapes.xl).padding(16.dp)) {
                DText("SIGNAL", Datum.type.sectionLabel, c.labelSecondary)
                Spacer(Modifier.height(8.dp))
                Sparkline(history.toList(), Modifier.fillMaxWidth().height(64.dp), color = tint, fixedMin = 0f, fixedMax = fullScale, threshold = fullScale * 0.18f)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    DText("Sensitivity", Datum.type.callout, c.labelSecondary, Modifier.padding(end = 12.dp))
                    MacSlider(sensitivity, { sensitivity = it }, Modifier.weight(1f))
                }
                DText(
                    "Field %.0f µT · baseline %.0f µT. Slide the top of the phone slowly across the wall; screws and nails mark the studs.".format(b ?: 0f, baseline ?: 0f),
                    Datum.type.footnote, c.labelSecondary,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(DatumIcons.Target, "Re-zero", {
                baseline = b
                history.clear()
                app.haptics.click()
            })
            ToolButton(DatumIcons.Speaker, "Sound", { sound = !sound }, active = sound)
            ToolButton(DatumIcons.Download, "Save", {
                app.library.add(Measurement(kind = MeasureKind.STUD, title = "Magnetic anomaly", value = delta.toDouble(), quantity = Quantity.FIELD, method = "Magnetometer"))
                hud.show(DatumIcons.Check, "Saved")
            })
        }
    }
}

@Composable
private fun Radar(strength: Float, tint: androidx.compose.ui.graphics.Color) {
    val t = rememberInfiniteTransition(label = "radar")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween((1600 - 1100 * strength).toInt().coerceAtLeast(300), easing = LinearEasing)), label = "ph")
    val c = Datum.colors
    Canvas(Modifier.size(300.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        drawCircle(Brush.radialGradient(listOf(tint.copy(alpha = 0.10f + 0.25f * strength), tint.copy(alpha = 0f)), center, r), r, center)
        for (k in 0 until 3) {
            val p = (phase + k / 3f) % 1f
            drawCircle(tint.copy(alpha = (1 - p) * (0.25f + 0.6f * strength)), r * (0.25f + 0.75f * p), center, style = Stroke(2.dp.toPx()))
        }
        drawCircle(c.separator, r * 0.35f, center, style = Stroke(1.dp.toPx()))
        drawCircle(c.separator, r * 0.7f, center, style = Stroke(1.dp.toPx()))
    }
}
