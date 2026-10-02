package com.akhielesh.datum.ui.screens.onboarding

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.rememberSensor
import com.akhielesh.datum.ui.components.AccentPicker
import com.akhielesh.datum.ui.components.AppearancePicker
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.MacSlider
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.StepDots
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlin.math.sqrt

@Composable
fun OnboardingScreen() {
    val app = LocalApp.current
    var step by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 300f)) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(260)) { if (forward) -it / 3 else it / 3 } + fadeOut(tween(200)))
                },
                label = "onboardingStep",
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (s) {
                        0 -> Welcome()
                        1 -> Personalize()
                        else -> AboutYou()
                    }
                }
            }
        }
        StepDots(3, step, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                PillButton("Back", onClick = { step-- }, style = ButtonStyle.Tinted, modifier = Modifier.weight(1f))
            }
            PillButton(
                if (step < 2) "Continue" else "Start measuring",
                onClick = {
                    if (step < 2) step++ else {
                        app.haptics.success()
                        app.settings.update { it.copy(onboardingDone = true) }
                    }
                },
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun Welcome() {
    val c = Datum.colors
    val gravity = rememberSensor(Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_GAME) {
        val n = sqrt(it.x * it.x + it.y * it.y + it.z * it.z).coerceAtLeast(0.1f)
        Offset(it.x / n, -it.y / n)
    }
    val bx by animateFloatAsState(gravity.value?.x ?: 0.25f, spring(dampingRatio = 0.6f, stiffness = 120f), label = "bx")
    val by by animateFloatAsState(gravity.value?.y ?: -0.2f, spring(dampingRatio = 0.6f, stiffness = 120f), label = "by")
    val spin = rememberInfiniteTransition(label = "logo")
    val sweep by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "sweep")
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        // Live logo: the bubble really floats as you tilt the phone.
        Canvas(Modifier.size(150.dp)) {
            val r = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            drawCircle(Brush.linearGradient(listOf(Color(0xFF3F9BFF), Color(0xFF2F6BF2), Color(0xFF5A4FD8))), r, center)
            drawCircle(Color.White.copy(alpha = 0.18f), r * 0.98f, center, style = Stroke(1.5f))
            val ring = r * 0.42f
            drawCircle(Color.White, ring, center, style = Stroke(r * 0.065f))
            val tick = r * 0.13f
            for (k in 0 until 4) {
                val ang = Math.toRadians(k * 90.0)
                val dx = kotlin.math.cos(ang).toFloat()
                val dy = kotlin.math.sin(ang).toFloat()
                drawLine(Color.White, Offset(center.x + dx * (ring + r * 0.13f), center.y + dy * (ring + r * 0.13f)),
                    Offset(center.x + dx * (ring + r * 0.13f + tick), center.y + dy * (ring + r * 0.13f + tick)), r * 0.065f, StrokeCap.Round)
            }
            drawArc(Color.White.copy(alpha = 0.35f), sweep, 40f, false, Offset(center.x - r * 0.82f, center.y - r * 0.82f),
                androidx.compose.ui.geometry.Size(r * 1.64f, r * 1.64f), style = Stroke(2f, cap = StrokeCap.Round))
            val bubble = Offset(center.x + (bx * r * 1.4f).coerceIn(-ring * 0.55f, ring * 0.55f), center.y + (by * r * 1.4f).coerceIn(-ring * 0.55f, ring * 0.55f))
            drawCircle(Color.White, r * 0.14f, bubble)
        }
        Spacer(Modifier.height(28.dp))
        DText("Welcome to Datum", Datum.type.largeTitle, align = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        DText(
            "A precision toolkit built on the sensors in your phone — tuned for Galaxy S22 and newer.",
            Datum.type.body, c.labelSecondary, align = TextAlign.Center,
        )
        Spacer(Modifier.height(30.dp))
        Feature(Tool.Level, "Level & angles", "Surface and edge level with two-point calibration.")
        Feature(Tool.Slide, "Slide & height", "Glide the phone along a surface or up a wall — it measures as it moves.")
        Feature(Tool.Object, "Objects in 3D", "Corners, edges, AR and photos fuse into one calibrated model.")
        Feature(Tool.Stud, "And more", "Ruler, compass, stud finder, light, sound and vibration meters.")
    }
}

@Composable
private fun Feature(tool: Tool, title: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(tool.icon, tool.colors, size = 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            DText(title, Datum.type.headline)
            DText(text, Datum.type.footnote, Datum.colors.labelSecondary)
        }
    }
}

@Composable
private fun Personalize() {
    val app = LocalApp.current
    val s = LocalSettings.current
    val c = Datum.colors
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(28.dp))
        DText("Make it yours", Datum.type.largeTitle)
        DText("Choose how Datum looks and measures. Change it anytime.", Datum.type.body, c.labelSecondary)
        Spacer(Modifier.height(26.dp))
        DText("APPEARANCE", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(bottom = 10.dp))
        AppearancePicker(s.themeMode, onChange = { m -> app.settings.update { it.copy(themeMode = m) } })
        Spacer(Modifier.height(24.dp))
        DText("ACCENT COLOUR", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(bottom = 12.dp))
        AccentPicker(s.accent, onChange = { a -> app.settings.update { it.copy(accent = a) } })
        Spacer(Modifier.height(26.dp))
        DText("UNITS", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(bottom = 10.dp))
        SegmentedControl(
            listOf("Metric (cm, m)", "Imperial (in, ft)"),
            if (s.units == UnitSystem.METRIC) 0 else 1,
            onSelect = { i -> app.settings.update { it.copy(units = if (i == 0) UnitSystem.METRIC else UnitSystem.IMPERIAL) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AboutYou() {
    val app = LocalApp.current
    val s = LocalSettings.current
    val c = Datum.colors
    val device = app.device
    val av = app.sensors.availability
    var height by remember { mutableFloatStateOf(s.userHeightCm.toFloat()) }
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(28.dp))
        DText("One more thing", Datum.type.largeTitle)
        DText("Your height lets Datum measure tall things by sight, like a surveyor.", Datum.type.body, c.labelSecondary)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth().card(Shapes.xl).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DIcon(DatumIcons.Person, tint = c.accent)
                Spacer(Modifier.width(10.dp))
                DText("Your height", Datum.type.headline, modifier = Modifier.weight(1f))
                val label = if (s.units == UnitSystem.METRIC) "%.0f cm".format(height) else {
                    val inches = height / 2.54f
                    "%d′ %d″".format((inches / 12).toInt(), (inches % 12).toInt())
                }
                DText(label, Datum.type.title3.copy(fontFeatureSettings = "tnum"), c.accent)
            }
            Spacer(Modifier.height(10.dp))
            MacSlider(
                value = height,
                onValueChange = { height = it },
                range = 120f..215f,
                steps = 95,
                onValueChangeFinished = { app.settings.update { it.copy(userHeightCm = height.toDouble()) } },
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth().card(Shapes.xl).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(DatumIcons.Phone, Tool.Sensors.colors, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    DText(device.marketingName, Datum.type.headline)
                    DText(
                        if (device.isTunedGalaxy) "Tuned profile loaded" else "Generic profile — calibrate for best results",
                        Datum.type.footnote, if (device.isTunedGalaxy) c.green else c.orange,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge("Accelerometer", av.accelerometer)
                Badge("Gyroscope", av.gyroscope)
                Badge("Magnetometer", av.magnetometer)
                Badge("Barometer", av.barometer)
                Badge("Light", av.light)
                Badge("Camera", av.camera)
                Badge("UWB", av.uwb)
            }
            Spacer(Modifier.height(10.dp))
            DText(
                "Body %.1f × %.1f mm · screen %.1f″".format(device.heightMm, device.widthMm, device.screenDiagonalIn),
                Datum.type.footnote, c.labelSecondary,
            )
        }
    }
}

@Composable
private fun Badge(name: String, ok: Boolean) {
    val c = Datum.colors
    Row(
        Modifier
            .card(Shapes.pill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DIcon(if (ok) DatumIcons.Check else DatumIcons.Close, tint = if (ok) c.green else c.labelTertiary, size = 13.dp)
        Spacer(Modifier.width(5.dp))
        DText(name, Datum.type.caption, if (ok) c.label else c.labelTertiary)
    }
}
