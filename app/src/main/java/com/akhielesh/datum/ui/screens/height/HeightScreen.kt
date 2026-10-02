package com.akhielesh.datum.ui.screens.height

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
import com.akhielesh.datum.core.math.LowPass
import com.akhielesh.datum.core.sensors.LevelEngine
import com.akhielesh.datum.core.sensors.MotionTracker
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.CameraPreview
import com.akhielesh.datum.ui.common.DarkChrome
import com.akhielesh.datum.ui.common.EngineLifecycle
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.PermissionGate
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.VolumeShutter
import com.akhielesh.datum.ui.common.aboveDock
import com.akhielesh.datum.ui.common.rememberMotionSession
import com.akhielesh.datum.ui.common.rememberSensor
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.CoachBubble
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.EmptyState
import com.akhielesh.datum.ui.components.GlassSheet
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.MacSlider
import com.akhielesh.datum.ui.components.MacSwitch
import com.akhielesh.datum.ui.components.NumberField
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.ProgressRing
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.ShutterButton
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.screens.slide.MotionCalibrationSheet
import com.akhielesh.datum.ui.screens.slide.TableTape
import com.akhielesh.datum.ui.screens.slide.TapeMark
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.GlassWeight
import com.akhielesh.datum.ui.theme.InterText
import com.akhielesh.datum.ui.theme.LocalHazeState
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/** Content offset below the top bar + mode switcher. */
@Composable
private fun Modifier.belowModeBar(): Modifier = this.windowInsetsPadding(WindowInsets.statusBars).padding(top = 104.dp)

private enum class HeightMode(val label: String) { Sight("Sight"), SlideUp("Slide up"), Baro("Barometer") }

@Composable
fun HeightScreen() {
    val app = LocalApp.current
    val av = app.sensors.availability
    val modes = HeightMode.entries.filter { (it != HeightMode.Baro || av.barometer) && (it != HeightMode.Sight || av.camera) }
    var mode by remember { mutableStateOf(modes.first()) }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(mode, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(200)) }, label = "heightMode") { m ->
            when (m) {
                HeightMode.Sight -> SightMode()
                HeightMode.SlideUp -> SlideUpMode()
                HeightMode.Baro -> BaroMode()
            }
        }
        Column(Modifier.fillMaxWidth()) {
            TopBar("Height", subtitle = mode.label)
            // Phones without a camera or barometer get just the slide method: no one-segment switcher.
            if (modes.size > 1) {
                SegmentedControl(
                    modes.map { it.label }, modes.indexOf(mode),
                    onSelect = { mode = modes[it] },
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Sight: camera clinometer. Height = h·(1 + tanβ / tan|α|), the surveyor's two-angle method.

@Composable
private fun SightMode() {
    val tool = Tool.Height
    PermissionGate(
        Manifest.permission.CAMERA, tool.icon, tool.colors,
        title = "Measure by sight",
        rationale = "Datum uses the camera as a sight: aim at the bottom and the top of something tall, and the tilt sensor does the trigonometry. Nothing is recorded or uploaded.",
        modifier = Modifier.padding(top = 120.dp),
    ) {
        SightContent()
    }
}

@Composable
private fun SightContent() {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    DarkChrome()
    KeepScreenOn()
    val engine = remember { LevelEngine(app.sensors) }
    EngineLifecycle(engine, engine::start, engine::stop)
    val up by engine.up.collectAsStateWithLifecycle()
    // Camera looks along −Z; elevation of that axis above the horizon.
    val elevation = up?.let { Math.toDegrees(asin((-it.z).coerceIn(-1.0, 1.0))) } ?: 0.0
    val recent = remember { ArrayDeque<Pair<Long, Double>>() }
    LaunchedEffect(up) {
        val u = up ?: return@LaunchedEffect
        recent.addLast(u.timestampNs to elevation)
        while (recent.size > 2 && u.timestampNs - recent.first().first > 450_000_000L) recent.removeFirst()
    }
    var step by remember { mutableIntStateOf(0) }
    var baseAngle by remember { mutableDoubleStateOf(0.0) }
    var topAngle by remember { mutableDoubleStateOf(0.0) }
    var showSettings by remember { mutableStateOf(false) }
    var useDistance by remember { mutableStateOf(false) }
    var distanceText by remember { mutableStateOf("") }
    val eye = settings.eyeHeightM
    val knownDistance = distanceText.toDoubleOrNull()?.let { if (settings.units == UnitSystem.METRIC) it else it * 0.3048 }

    fun steadyAngle(): Double = if (recent.isEmpty()) elevation else recent.map { it.second }.average()

    fun compute(base: Double, top: Double): Pair<Double, Double>? {
        val tb = tan(Math.toRadians(base))
        val tt = tan(Math.toRadians(top))
        if (useDistance) {
            val d = knownDistance ?: return null
            return d * (tt - tb) to d
        }
        if (base > -0.8) return null
        val d = eye / -tb
        return eye + d * tt to d
    }

    fun capture() {
        when (step) {
            0 -> {
                baseAngle = steadyAngle()
                step = 1
                app.haptics.capture()
                app.tones.tink()
            }
            1 -> {
                topAngle = steadyAngle()
                step = 2
                app.haptics.success()
                app.tones.success()
            }
        }
    }
    VolumeShutter(enabled = step < 2) { capture() }

    val result = if (step == 2) compute(baseAngle, topAngle) else null
    val liveResult = if (step == 1) compute(baseAngle, elevation) else null

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        CameraPreview(Modifier.fillMaxSize())
        Crosshair(elevation, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalHazeState provides null) {
            Column(Modifier.fillMaxSize().belowModeBar().padding(top = 12.dp)) {
                CoachBubble(
                    when (step) {
                        0 -> "Aim the cross at the base, where it meets the ground"
                        1 -> "Now aim at the very top"
                        else -> "Done — tap reset to measure again"
                    },
                    Modifier.align(Alignment.CenterHorizontally),
                    icon = if (step < 2) DatumIcons.Target else DatumIcons.Check,
                    tint = if (step < 2) c.accent else c.green,
                )
                Spacer(Modifier.weight(1f))
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .glass(Shapes.xl, tint = Color(0xB3151517), elevation = 12.dp)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            val value = result?.first ?: liveResult?.first
                            if (value != null) {
                                val f = Fmt.length(value, settings.units, settings.inchFractions)
                                Readout(f.value, f.unit, style = Datum.type.readoutM, color = Color.White, unitColor = Color.White.copy(alpha = 0.6f))
                                DText(
                                    "Distance " + Fmt.length((result ?: liveResult)!!.second, settings.units) +
                                        if (useDistance) " (entered)" else " · eye " + Fmt.length(eye, settings.units),
                                    Datum.type.footnote, Color.White.copy(alpha = 0.65f),
                                )
                            } else {
                                Readout("%.1f".format(elevation), "°", style = Datum.type.readoutM, color = Color.White, unitColor = Color.White.copy(alpha = 0.6f))
                                DText(
                                    if (step == 2) "Base must be below eye level — try distance mode" else "Camera elevation",
                                    Datum.type.footnote, Color.White.copy(alpha = 0.65f),
                                )
                            }
                        }
                        SightDiagram(
                            eye = if (useDistance) 1.5 else eye,
                            base = if (step >= 1) baseAngle else elevation,
                            top = when (step) {
                                2 -> topAngle
                                1 -> elevation
                                else -> null
                            },
                            Modifier.size(width = 120.dp, height = 96.dp),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().aboveDock().padding(top = 14.dp, start = 18.dp, end = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolButton(DatumIcons.Reset, "Reset", {
                        step = 0
                        app.haptics.click()
                    }, onDark = true)
                    ShutterButton(
                        onClick = { capture() },
                        icon = when (step) {
                            0 -> DatumIcons.ChevronDown
                            1 -> DatumIcons.Height
                            else -> DatumIcons.Check
                        },
                        enabled = step < 2,
                        color = if (step == 2) c.green else c.accent,
                    )
                    ToolButton(if (step == 2) DatumIcons.Download else DatumIcons.Person, if (step == 2) "Save" else "Eye height", {
                        if (step == 2 && result != null) {
                            app.library.add(
                                Measurement(
                                    kind = MeasureKind.HEIGHT,
                                    title = "Height by sight",
                                    value = result.first,
                                    quantity = Quantity.LENGTH,
                                    method = "Sight · camera clinometer",
                                    details = listOf(
                                        Detail("Horizontal distance", result.second, Quantity.LENGTH),
                                        Detail("Base angle", baseAngle, Quantity.ANGLE),
                                        Detail("Top angle", topAngle, Quantity.ANGLE),
                                        Detail(if (useDistance) "Entered distance" else "Eye height", if (useDistance) result.second else eye, Quantity.LENGTH),
                                    ),
                                ),
                            )
                            hud.show(DatumIcons.Check, "Saved")
                        } else {
                            showSettings = true
                        }
                    }, onDark = true)
                }
            }
        }
        SightSettingsSheet(showSettings, useDistance, distanceText, { useDistance = it }, { distanceText = it }) { showSettings = false }
    }
}

@Composable
private fun SightSettingsSheet(
    visible: Boolean,
    useDistance: Boolean,
    distanceText: String,
    onUseDistance: (Boolean) -> Unit,
    onDistance: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val c = Datum.colors
    var eyeCm by remember { mutableDoubleStateOf(settings.eyeHeightM * 100) }
    GlassSheet(visible, onDismiss, title = "Sight settings") {
        DText("EYE HEIGHT", Datum.type.sectionLabel, c.labelSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            DText("Phone held at", Datum.type.body, modifier = Modifier.weight(1f))
            DText(Fmt.length(eyeCm / 100, settings.units).toString(), Datum.type.headline, c.accent)
        }
        MacSlider(
            eyeCm.toFloat(), { eyeCm = it.toDouble() }, range = 50f..220f, steps = 170,
            onValueChangeFinished = { app.settings.update { it.copy(eyeHeightOverrideCm = eyeCm) } },
        )
        DText(
            "Defaults to 93.6 % of your height (${Fmt.length(settings.userHeightCm / 100, settings.units)}). Hold the phone at eye level when aiming.",
            Datum.type.footnote, c.labelSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DText("I know the distance", Datum.type.body)
                DText("Use a measured distance instead of eye height (works on slopes).", Datum.type.footnote, c.labelSecondary)
            }
            MacSwitch(useDistance, onUseDistance)
        }
        if (useDistance) {
            Spacer(Modifier.height(10.dp))
            NumberField(distanceText, onDistance, Modifier.fillMaxWidth(), suffix = if (settings.units == UnitSystem.METRIC) "m" else "ft", placeholder = "Horizontal distance")
        }
    }
}

/** Crosshair with an aircraft-style pitch ladder that follows the camera elevation. */
@Composable
private fun Crosshair(elevation: Double, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = InterText, fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f), fontFeatureSettings = "tnum")
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val white = Color.White
        drawCircle(Color.Black.copy(alpha = 0.25f), 30.dp.toPx(), center, style = Stroke(4.dp.toPx()))
        drawCircle(white, 30.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
        val g = 12.dp.toPx()
        val l = 26.dp.toPx()
        listOf(Offset(1f, 0f), Offset(-1f, 0f), Offset(0f, 1f), Offset(0f, -1f)).forEach { d ->
            drawLine(white, Offset(center.x + d.x * g, center.y + d.y * g), Offset(center.x + d.x * (g + l), center.y + d.y * (g + l)), 1.5.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(white, 2.dp.toPx(), center)
        // Pitch ladder: rungs every 5°, horizon solid.
        val pxPerDeg = 9.dp.toPx()
        val x = size.width * 0.82f
        for (deg in -60..80 step 5) {
            val y = center.y - ((deg - elevation) * pxPerDeg).toFloat()
            if (y < 120.dp.toPx() || y > size.height - 160.dp.toPx()) continue
            val w = if (deg == 0) 46.dp.toPx() else if (deg % 10 == 0) 26.dp.toPx() else 14.dp.toPx()
            drawLine(if (deg == 0) Color(0xFF7CFFB2) else white.copy(alpha = 0.7f), Offset(x - w / 2, y), Offset(x + w / 2, y), if (deg == 0) 2.dp.toPx() else 1.dp.toPx())
            if (deg % 10 == 0) {
                val layout = measurer.measure("$deg°", style)
                drawText(layout, topLeft = Offset(x + w / 2 + 6.dp.toPx(), y - layout.size.height / 2f))
            }
        }
        drawLine(white, Offset(x - 32.dp.toPx(), center.y), Offset(x - 22.dp.toPx(), center.y), 2.dp.toPx())
    }
}

/** Live side-view triangle: eye, aim rays, object. */
@Composable
private fun SightDiagram(eye: Double, base: Double, top: Double?, modifier: Modifier) {
    val accent = Datum.colors.accent
    Canvas(modifier) {
        val ground = size.height * 0.9f
        val eyeX = size.width * 0.12f
        val baseTan = tan(Math.toRadians(base.coerceAtMost(-2.0)))
        val dist = eye / -baseTan
        val topH = top?.let { eye + dist * tan(Math.toRadians(it)) } ?: eye * 1.6
        val worldW = max(dist, 0.5)
        val worldH = max(max(topH, eye) * 1.15, 0.5)
        val sx = size.width * 0.76f / worldW.toFloat()
        val sy = size.height * 0.8f / worldH.toFloat()
        val s = min(sx, sy)
        val eyeP = Offset(eyeX, ground - (eye * s).toFloat())
        val objX = eyeX + (dist * s).toFloat()
        val baseP = Offset(objX, ground)
        val topP = Offset(objX, ground - (topH * s).toFloat())
        val white = Color.White
        drawLine(white.copy(alpha = 0.5f), Offset(0f, ground), Offset(size.width, ground), 1.dp.toPx())
        drawLine(white.copy(alpha = 0.7f), Offset(eyeX, ground), eyeP, 2.dp.toPx(), StrokeCap.Round)
        drawCircle(white, 3.dp.toPx(), eyeP)
        drawLine(white.copy(alpha = 0.4f), eyeP, Offset(objX, eyeP.y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
        drawLine(accent, eyeP, baseP, 1.5.dp.toPx())
        if (top != null) drawLine(Color(0xFF7CFFB2), eyeP, topP, 1.5.dp.toPx())
        drawLine(white, baseP, topP, 3.dp.toPx(), StrokeCap.Round)
        drawPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(objX - 6.dp.toPx(), topP.y, objX + 6.dp.toPx(), ground)) }, white.copy(alpha = 0.15f))
    }
}

// ------------------------------------------------------------------------------------------------
// Slide up: the Slide tool turned vertical, with a wall-fixed tape.

@Composable
private fun SlideUpMode() {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    KeepScreenOn()
    val session = rememberMotionSession()
    val snap by session.state.collectAsStateWithLifecycle()
    val marks = remember { mutableStateListOf<TapeMark>() }
    var addBody by remember { mutableStateOf(false) }
    var showCal by remember { mutableStateOf(false) }
    val vertical = snap.livePosition dot snap.up
    val bodyM = app.device.heightMm / 1000
    val height = abs(vertical) + if (addBody) bodyM else 0.0
    val pxPerMeter = (app.physicalPpi / 0.0254).toFloat()
    val pressure = rememberSensor(Sensor.TYPE_PRESSURE, 100_000) { it.x }
    var baroStart by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(pressure.value) { if (baroStart == null) baroStart = pressure.value }
    val shown = remember { Animatable(0f) }
    LaunchedEffect(height, snap.phase) {
        if (snap.phase == MotionTracker.Phase.MOVING) shown.snapTo(height.toFloat())
        else shown.animateTo(height.toFloat(), spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    fun mark() {
        if (snap.phase == MotionTracker.Phase.CALIBRATING) return
        marks += TapeMark(('A' + marks.size).toString(), vertical)
        app.haptics.capture()
        app.tones.tink()
    }
    VolumeShutter { mark() }
    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        TableTape(vertical, pxPerMeter, settings.units, marks.toList(), Modifier.fillMaxSize().hazeSource(haze), needleFraction = 0.6f)
        CompositionLocalProvider(LocalHazeState provides haze) {
            Column(Modifier.fillMaxSize().belowModeBar()) {
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .glass(Shapes.xl, GlassWeight.Regular, elevation = 10.dp)
                        .padding(vertical = 14.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val (label, color) = when (snap.phase) {
                        MotionTracker.Phase.CALIBRATING -> "Hold still against the wall…" to c.orange
                        MotionTracker.Phase.MOVING -> "Measuring" to c.accent
                        MotionTracker.Phase.STILL -> (if (snap.segmentCount == 0) "Ready — slide up the wall" else "Locked") to c.green
                    }
                    StatusChip(label, color, pulsing = snap.phase != MotionTracker.Phase.STILL)
                    Spacer(Modifier.height(6.dp))
                    val f = Fmt.length(shown.value.toDouble(), settings.units, settings.inchFractions)
                    Readout(f.value, f.unit, style = Datum.type.readoutL)
                    val baro = pressure.value?.let { p -> baroStart?.let { s -> SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, p) - SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, s) } }
                    val extra = buildList {
                        if (snap.sigma > 0) add("± " + Fmt.length(snap.sigma, settings.units))
                        if (baro != null) add("barometer " + Fmt.length(baro.toDouble(), settings.units))
                        if (addBody) add("incl. phone")
                    }
                    DText(extra.joinToString("  ·  ").ifEmpty { "Press the phone flat to the wall, then glide it up." }, Datum.type.footnote, c.labelSecondary, maxLines = 1)
                    if (snap.phase == MotionTracker.Phase.CALIBRATING) {
                        Spacer(Modifier.height(8.dp))
                        ProgressRing(snap.calibrationProgress.toFloat(), Modifier.size(28.dp), color = c.orange, stroke = 3.dp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.fillMaxWidth().aboveDock().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToolButton(DatumIcons.Reset, "Reset", {
                        session.reset(keepCalibration = false)
                        marks.clear()
                        baroStart = pressure.value
                    })
                    ToolButton(DatumIcons.Pin, "Mark", { mark() })
                    ToolButton(DatumIcons.Phone, "+Phone", { addBody = !addBody }, active = addBody)
                    ToolButton(DatumIcons.Download, "Save", {
                        if (height <= 0) return@ToolButton
                        app.library.add(
                            Measurement(
                                kind = MeasureKind.HEIGHT, title = "Height · slide up", value = height,
                                quantity = Quantity.LENGTH, sigma = snap.sigma, method = "IMU slide (vertical)",
                                marks = marks.map { abs(it.at) + if (addBody) bodyM else 0.0 },
                            ),
                        )
                        hud.show(DatumIcons.Check, "Saved")
                    })
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBars).padding(top = 9.dp, end = 14.dp)) {
            CircleButton(DatumIcons.Sparkles, onClick = { showCal = true }, size = 38.dp)
        }
        MotionCalibrationSheet(showCal, abs(snap.position dot snap.up), onDismiss = { showCal = false })
    }
}

// ------------------------------------------------------------------------------------------------
// Barometer: relative altitude with a live chart and floor counter.

@Composable
private fun BaroMode() {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    val raw = rememberSensor(Sensor.TYPE_PRESSURE, 100_000) { it }
    val filter = remember { LowPass(1.2) }
    var hpa by remember { mutableStateOf<Double?>(null) }
    var zero by remember { mutableStateOf<Double?>(null) }
    val history = remember { mutableStateListOf<Pair<Long, Double>>() }
    var showCal by remember { mutableStateOf(false) }
    LaunchedEffect(raw.value) {
        val r = raw.value ?: return@LaunchedEffect
        hpa = filter.update(r.x.toDouble(), r.timestampNs)
    }
    val p = hpa
    val altitude = p?.let { SensorManager.getAltitude(settings.seaLevelPressureHpa.toFloat(), it.toFloat()).toDouble() }
    LaunchedEffect(altitude != null) { if (zero == null && altitude != null) zero = altitude }
    val rel = if (altitude != null && zero != null) altitude - zero!! else 0.0
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            val a = altitude
            if (a != null && zero != null) {
                history += System.currentTimeMillis() to rel
                while (history.size > 240) history.removeAt(0)
            }
        }
    }
    Column(Modifier.fillMaxSize().belowModeBar()) {
        if (p == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(DatumIcons.Altimeter, "Reading the barometer…", "Pressure changes of a few pascals reveal height changes of tens of centimetres.")
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val f = Fmt.length(rel, settings.units)
                Readout((if (rel >= 0) "+" else "") + f.value, f.unit, style = Datum.type.readoutXL)
                val floors = rel / 3.0
                DText(
                    when {
                        abs(floors) < 0.75 -> "Same floor"
                        floors > 0 -> "≈ %.0f floor%s up".format(floors, if (floors >= 1.5) "s" else "")
                        else -> "≈ %.0f floor%s down".format(-floors, if (floors <= -1.5) "s" else "")
                    },
                    Datum.type.headline, c.labelSecondary,
                )
            }
            Spacer(Modifier.height(18.dp))
            AltitudeChart(history.toList(), Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(170.dp))
            Spacer(Modifier.height(14.dp))
            Column(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().glass(Shapes.lg, GlassWeight.Thick).padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                KeyValueRow("Pressure", Fmt.pressure(p, settings.units).toString())
                KeyValueRow("Altitude (approx.)", Fmt.length(altitude ?: 0.0, settings.units).toString(), hint = "Sea level ${"%.1f".format(settings.seaLevelPressureHpa)} hPa")
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().aboveDock().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ToolButton(DatumIcons.Target, "Zero", {
                    zero = altitude
                    history.clear()
                    app.haptics.click()
                })
                ToolButton(DatumIcons.Sparkles, "Calibrate", { showCal = true })
                ToolButton(DatumIcons.Download, "Save", {
                    app.library.add(
                        Measurement(
                            kind = MeasureKind.ALTITUDE, title = "Altitude change", value = rel, quantity = Quantity.LENGTH,
                            method = "Barometer",
                            details = listOf(
                                Detail("Pressure (hPa)", p, Quantity.PLAIN),
                                Detail("Altitude", altitude ?: 0.0, Quantity.LENGTH),
                            ),
                        ),
                    )
                    hud.show(DatumIcons.Check, "Saved")
                })
            }
        }
    }
    var known by remember { mutableStateOf("") }
    GlassSheet(showCal, { showCal = false }, title = "Calibrate altitude") {
        DText("Enter your current altitude (from a map or a sign) to correct for today's weather.", Datum.type.callout, c.labelSecondary)
        Spacer(Modifier.height(12.dp))
        NumberField(known, { known = it }, Modifier.fillMaxWidth(), suffix = if (settings.units == UnitSystem.METRIC) "m" else "ft", placeholder = "Known altitude")
        Spacer(Modifier.height(12.dp))
        PillButton("Apply", onClick = {
            val k = known.toDoubleOrNull() ?: return@PillButton
            val pp = hpa ?: return@PillButton
            val h = if (settings.units == UnitSystem.METRIC) k else k * 0.3048
            val p0 = pp / Math.pow(1 - h / 44330.0, 5.255)
            app.settings.update { it.copy(seaLevelPressureHpa = p0) }
            hud.show(DatumIcons.Check, "Calibrated")
            showCal = false
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        PillButton("Use standard atmosphere", onClick = {
            app.settings.update { it.copy(seaLevelPressureHpa = 1013.25) }
            showCal = false
        }, style = ButtonStyle.Tinted, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun AltitudeChart(points: List<Pair<Long, Double>>, modifier: Modifier) {
    val c = Datum.colors
    val units = LocalSettings.current.units
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = InterText, fontSize = 10.sp, color = c.labelTertiary, fontFeatureSettings = "tnum")
    Box(modifier.glass(Shapes.lg, GlassWeight.Thick)) {
        Canvas(Modifier.fillMaxSize().padding(14.dp)) {
            if (points.size < 2) {
                val layout = measurer.measure("Collecting…", style)
                drawText(layout, topLeft = Offset(size.width / 2 - layout.size.width / 2, size.height / 2 - layout.size.height / 2))
                return@Canvas
            }
            val vals = points.map { it.second }
            var lo = vals.min()
            var hi = vals.max()
            if (hi - lo < 1.0) {
                val mid = (hi + lo) / 2
                lo = mid - 0.5
                hi = mid + 0.5
            }
            fun y(v: Double) = (size.height - (v - lo) / (hi - lo) * size.height).toFloat()
            val n = points.size
            val line = Path()
            points.forEachIndexed { i, (_, v) ->
                val x = size.width * i / (n - 1)
                if (i == 0) line.moveTo(x, y(v)) else line.lineTo(x, y(v))
            }
            val fill = Path().apply {
                addPath(line)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.28f), Color.Transparent)))
            drawPath(line, c.accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            drawLine(c.separator, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
            val top = measurer.measure(Fmt.length(hi, units).toString(), style)
            drawText(top, topLeft = Offset(0f, 0f))
            val bottom = measurer.measure(Fmt.length(lo, units).toString(), style)
            drawText(bottom, topLeft = Offset(0f, size.height - bottom.size.height))
        }
    }
}
