package com.akhielesh.datum.ui.screens.level

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.sensors.LevelAngles
import com.akhielesh.datum.core.sensors.LevelEngine
import com.akhielesh.datum.core.sensors.LevelMath
import com.akhielesh.datum.core.sensors.SlopeUnit
import com.akhielesh.datum.core.sensors.UpVector
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.common.EngineLifecycle
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveDock
import com.akhielesh.datum.ui.common.displayRotation
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.GlassSheet
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.MacSwitch
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.ProgressRing
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.components.pressable
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private enum class LevelMode { Auto, Surface, Edge }

/** Nonlinear vial scale: fine resolution near level, still shows up to ±15°. */
private fun vialFraction(deg: Double, range: Double = 15.0, k: Double = 4.0): Float =
    ((1 - exp(-abs(deg) / k)) / (1 - exp(-range / k))).toFloat().coerceIn(0f, 1f)

private const val LEVEL_ON = 0.1
private const val LEVEL_OFF = 0.25

@Composable
fun LevelScreen(previewUp: UpVector? = null) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    val engine = remember { LevelEngine(app.sensors) }
    EngineLifecycle(engine, start = engine::start, stop = engine::stop)
    KeepScreenOn()
    val liveUp by engine.up.collectAsStateWithLifecycle()
    val up = previewUp ?: liveUp
    val rotation = displayRotation()

    var mode by remember { mutableStateOf(LevelMode.Auto) }
    var unit by remember { mutableStateOf(SlopeUnit.DEGREES) }
    var held by remember { mutableStateOf<LevelAngles?>(null) }
    var reference by remember { mutableStateOf<LevelAngles?>(null) }
    var showCalibration by remember { mutableStateOf(false) }
    var surfaceActive by remember { mutableStateOf(true) }

    val live = up?.let { LevelMath.angles(it, settings.levelCalibration, rotation) }
    val angles = held ?: live
    // Auto mode with hysteresis so it doesn't flicker around 45°.
    if (mode == LevelMode.Auto && live != null) {
        if (surfaceActive && live.flatness < 0.62) surfaceActive = false
        if (!surfaceActive && live.flatness > 0.74) surfaceActive = true
    }
    val surface = when (mode) {
        LevelMode.Surface -> true
        LevelMode.Edge -> false
        LevelMode.Auto -> surfaceActive
    }
    val ref = reference
    val roll = (angles?.rollDeg ?: 0.0) - if (surface) ref?.rollDeg ?: 0.0 else 0.0
    val pitch = (angles?.pitchDeg ?: 0.0) - if (surface) ref?.pitchDeg ?: 0.0 else 0.0
    val tilt = if (ref != null && surface) hypot(roll, pitch) else angles?.tiltDeg ?: 0.0
    val edge = (angles?.edgeDeg ?: 0.0) - if (!surface) ref?.edgeDeg ?: 0.0 else 0.0
    val deviation = if (surface) tilt else abs(edge)

    // Level detection with hysteresis + haptic / chime on arrival.
    var isLevel by remember { mutableStateOf(false) }
    LaunchedEffect(deviation, angles != null) {
        if (angles == null) return@LaunchedEffect
        if (!isLevel && deviation < LEVEL_ON) {
            isLevel = true
            app.haptics.success()
        } else if (isLevel && deviation > LEVEL_OFF) {
            isLevel = false
        }
    }
    val levelColor by animateColorAsState(if (isLevel) c.green else c.accent, tween(250), label = "levelColor")

    // Eyes-free guidance: beeps speed up and rise in pitch as you approach level.
    val currentDeviation by rememberUpdatedState(deviation)
    LaunchedEffect(settings.levelSoundGuide) {
        if (!settings.levelSoundGuide) return@LaunchedEffect
        while (true) {
            val d = currentDeviation
            if (d < LEVEL_ON) {
                app.tones.beep(1760.0, 0.09, force = true)
                delay(140)
            } else {
                val t = min(d / 10.0, 1.0)
                app.tones.beep(620.0 + (1 - t) * 820.0, 0.045, force = true)
                delay((90 + t * 760).toLong())
            }
        }
    }

    // Throttled numbers so the last digit doesn't shimmer at 100 Hz.
    var shown by remember { mutableStateOf(Triple(0.0, 0.0, 0.0)) }
    val latest by rememberUpdatedState(Triple(if (surface) tilt else edge, roll, pitch))
    LaunchedEffect(Unit) {
        while (true) {
            shown = latest
            delay(110)
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!surface && angles != null) {
            HorizonBackdrop(angles, isLevel, Modifier.fillMaxSize())
        }
        Column(Modifier.fillMaxSize()) {
            TopBar(
                "Level",
                subtitle = when {
                    held != null -> "Holding"
                    ref != null -> "Relative to reference"
                    surface -> "Surface" + if (mode == LevelMode.Auto) " · auto" else ""
                    else -> "Edge" + if (mode == LevelMode.Auto) " · auto" else ""
                },
            ) {
                CircleButton(DatumIcons.Sparkles, onClick = { showCalibration = true }, size = 38.dp)
            }
            SegmentedControl(
                listOf("Auto", "Surface", "Edge"),
                mode.ordinal,
                onSelect = { mode = LevelMode.entries[it] },
                modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            // Primary readout; tap to cycle units.
            Column(
                Modifier
                    .fillMaxWidth()
                    .pressable { unit = unit.next() }
                    .graphicsLayer {
                        if (!surface && angles != null) {
                            rotationZ = angles.quadrant * 90f
                        }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DText(
                    unit.format(shown.first),
                    Datum.type.readoutXL,
                    if (isLevel) c.green else c.label,
                    maxLines = 1,
                )
                DText(
                    when {
                        surface -> "X %s   ·   Y %s".format(unit.format(shown.second), unit.format(shown.third))
                        (angles?.quadrant ?: 0) % 2 == 0 -> "off plumb · vertical edge"
                        else -> "off level · horizontal edge"
                    },
                    Datum.type.subhead, c.labelSecondary, align = TextAlign.Center,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (surface) {
                    SurfaceVial(angles, roll, pitch, tilt, isLevel, levelColor)
                } else {
                    EdgeVial(edge, isLevel, levelColor, Modifier.padding(horizontal = 28.dp))
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = isLevel,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn() + scaleIn(initialScale = 0.8f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                ) {
                    StatusChip("Level", c.green)
                }
            }
            Row(
                Modifier.fillMaxWidth().aboveDock().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                ToolButton(if (held != null) DatumIcons.Unlock else DatumIcons.Lock, if (held != null) "Release" else "Hold", {
                    held = if (held == null) live else null
                }, active = held != null)
                ToolButton(DatumIcons.Target, if (ref != null) "Absolute" else "Zero", {
                    reference = if (ref == null) live else null
                    app.haptics.click()
                }, active = ref != null)
                ToolButton(DatumIcons.Speaker, "Guide", {
                    app.settings.update { it.copy(levelSoundGuide = !it.levelSoundGuide) }
                }, active = settings.levelSoundGuide)
                ToolButton(DatumIcons.Download, "Save", {
                    val a = angles ?: return@ToolButton
                    app.library.add(
                        Measurement(
                            kind = MeasureKind.LEVEL,
                            title = if (surface) "Surface tilt" else "Edge angle",
                            value = if (surface) tilt else edge,
                            quantity = Quantity.ANGLE,
                            method = if (surface) "Surface level" else "Edge level",
                            details = buildList {
                                if (surface) {
                                    add(Detail("Roll (X)", roll, Quantity.ANGLE))
                                    add(Detail("Pitch (Y)", pitch, Quantity.ANGLE))
                                } else {
                                    add(Detail("Phone angle", a.screenAngleDeg, Quantity.ANGLE))
                                }
                                add(Detail("Slope (%)", kotlin.math.tan(Math.toRadians(if (surface) tilt else edge)) * 100, Quantity.PLAIN))
                            },
                        ),
                    )
                    app.haptics.capture()
                    hud.show(DatumIcons.Check, "Saved")
                })
            }
        }
        CalibrationSheet(showCalibration, engine, onDismiss = { showCalibration = false })
    }
}

/** Circular bubble vial for surface mode, with liquid-like bubble motion. */
@Composable
private fun SurfaceVial(angles: LevelAngles?, roll: Double, pitch: Double, tilt: Double, isLevel: Boolean, accent: Color) {
    val c = Datum.colors
    // Bubble direction comes from roll/pitch; distance uses the nonlinear vial scale.
    val dir = atan2(-pitch, roll)
    val frac = vialFraction(tilt)
    val target = Offset((cos(dir) * frac).toFloat(), (sin(dir) * frac).toFloat())
    val bubble = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(target) { bubble.animateTo(target, spring(dampingRatio = 0.62f, stiffness = 170f)) }
    val glow by animateFloatAsState(if (isLevel) 1f else 0f, tween(300), label = "glow")
    // The bubble floats in once the first reading arrives instead of sitting under the placeholder.
    val presence by animateFloatAsState(if (angles != null) 1f else 0f, tween(260), label = "presence")
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
        val d = min(maxWidth.value, 340f).dp
        Canvas(Modifier.size(d)) {
            val r = size.minDimension / 2
            val center = Offset(size.width / 2, size.height / 2)
            val bubbleR = r * 0.17f
            val travel = r - bubbleR - r * 0.03f
            // Body
            drawCircle(Brush.radialGradient(listOf(c.surface, c.fillTertiary), center, r), r, center)
            drawCircle(c.glassBorder, r, center, style = Stroke(1.dp.toPx()))
            if (glow > 0f) drawCircle(c.green.copy(alpha = 0.16f * glow), r, center)
            // Graduation rings (1°, 2°, 5°, 10°)
            for (deg in listOf(1.0, 2.0, 5.0, 10.0)) {
                val rr = travel * vialFraction(deg)
                drawCircle(c.separator, rr, center, style = Stroke(if (deg == 5.0) 1.3.dp.toPx() else 0.8.dp.toPx()))
            }
            // Crosshair with minor ticks
            drawLine(c.separator, Offset(center.x - r, center.y), Offset(center.x + r, center.y), 0.8.dp.toPx())
            drawLine(c.separator, Offset(center.x, center.y - r), Offset(center.x, center.y + r), 0.8.dp.toPx())
            // Target ring = the ±level tolerance zone
            drawCircle(if (isLevel) c.green else c.labelTertiary, bubbleR * 1.12f, center, style = Stroke(1.6.dp.toPx()))
            // Bubble with squash & stretch along its velocity
            val v = bubble.velocity
            val speed = sqrt(v.x * v.x + v.y * v.y)
            val stretch = 1f + min(speed * 0.22f, 0.28f)
            val p = Offset(center.x + bubble.value.x * travel, center.y + bubble.value.y * travel)
            val angle = Math.toDegrees(atan2(v.y.toDouble(), v.x.toDouble())).toFloat()
            if (presence > 0f) {
                withTransform({
                    rotate(angle, p)
                    scale(stretch * presence, presence / stretch, p)
                }) {
                    drawBubble(p, bubbleR, accent)
                }
            }
        }
        if (angles == null) {
            DText(
                "Waiting for sensor…", Datum.type.footnote, c.labelSecondary,
                modifier = Modifier.background(c.surface.copy(alpha = 0.9f), Shapes.pill).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun DrawScope.drawBubble(p: Offset, r: Float, accent: Color) {
    drawCircle(Color.Black.copy(alpha = 0.10f), r * 1.04f, Offset(p.x, p.y + r * 0.12f))
    drawCircle(
        Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.95f), accent.copy(alpha = 0.55f), accent.copy(alpha = 0.85f)),
            center = Offset(p.x - r * 0.3f, p.y - r * 0.35f),
            radius = r * 1.5f,
        ),
        r, p,
    )
    drawCircle(Color.White.copy(alpha = 0.7f), r, p, style = Stroke(r * 0.06f))
    drawCircle(Color.White.copy(alpha = 0.85f), r * 0.22f, Offset(p.x - r * 0.36f, p.y - r * 0.4f))
}

/** Tubular vial for edge mode (a carpenter's level). */
@Composable
private fun EdgeVial(edge: Double, isLevel: Boolean, accent: Color, modifier: Modifier = Modifier) {
    val c = Datum.colors
    val target = (if (edge >= 0) 1 else -1) * vialFraction(edge)
    val pos = remember { Animatable(0f) }
    LaunchedEffect(target) { pos.animateTo(target, spring(dampingRatio = 0.6f, stiffness = 170f)) }
    Canvas(modifier.fillMaxWidth().height(64.dp)) {
        val h = size.height
        val w = size.width
        val tube = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, w, h, h / 2, h / 2))
        }
        drawPath(tube, Brush.verticalGradient(listOf(c.surface, c.fillTertiary)))
        drawPath(tube, c.glassBorder, style = Stroke(1.dp.toPx()))
        val bubbleW = h * 1.55f
        val travel = (w - bubbleW) / 2 - h * 0.12f
        val zone = bubbleW / 2 + 3.dp.toPx()
        drawLine(if (isLevel) c.green else c.labelTertiary, Offset(w / 2 - zone, h * 0.12f), Offset(w / 2 - zone, h * 0.88f), 1.6.dp.toPx(), StrokeCap.Round)
        drawLine(if (isLevel) c.green else c.labelTertiary, Offset(w / 2 + zone, h * 0.12f), Offset(w / 2 + zone, h * 0.88f), 1.6.dp.toPx(), StrokeCap.Round)
        val cx = w / 2 + pos.value * travel
        val v = pos.velocity
        val stretch = 1f + min(abs(v) * 0.2f, 0.25f)
        val bw = bubbleW * stretch
        val bh = h * 0.62f / stretch
        drawRoundRect(
            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), accent.copy(alpha = 0.7f)), startY = h / 2 - bh / 2, endY = h / 2 + bh / 2),
            topLeft = Offset(cx - bw / 2, h / 2 - bh / 2),
            size = Size(bw, bh),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bh / 2, bh / 2),
        )
        drawRoundRect(
            Color.White.copy(alpha = 0.8f),
            topLeft = Offset(cx - bw * 0.32f, h / 2 - bh * 0.32f),
            size = Size(bw * 0.4f, bh * 0.16f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bh * 0.08f, bh * 0.08f),
        )
    }
}

/** iOS-Measure-style split screen: the colored half stays aligned with the real horizon. */
@Composable
private fun HorizonBackdrop(angles: LevelAngles, isLevel: Boolean, modifier: Modifier) {
    val c = Datum.colors
    val fill by animateColorAsState(
        if (isLevel) c.green.copy(alpha = 0.22f) else c.accent.copy(alpha = if (c.isDark) 0.16f else 0.09f),
        tween(250), label = "horizonFill",
    )
    val angle = remember { Animatable(angles.screenAngleDeg.toFloat()) }
    LaunchedEffect(angles.screenAngleDeg) {
        var target = angles.screenAngleDeg.toFloat()
        // Shortest path across the ±180° seam.
        while (target - angle.value > 180f) target -= 360f
        while (target - angle.value < -180f) target += 360f
        angle.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 300f))
    }
    Canvas(modifier) {
        val a = Math.toRadians(angle.value.toDouble())
        val center = Offset(size.width / 2, size.height * 0.52f)
        val dx = cos(a).toFloat()
        val dy = sin(a).toFloat()
        val nx = -dy
        val ny = dx
        val l = hypot(size.width, size.height)
        val half = Path().apply {
            moveTo(center.x + dx * l, center.y + dy * l)
            lineTo(center.x + dx * l + nx * l, center.y + dy * l + ny * l)
            lineTo(center.x - dx * l + nx * l, center.y - dy * l + ny * l)
            lineTo(center.x - dx * l, center.y - dy * l)
            close()
        }
        drawPath(half, fill)
        drawLine(
            if (isLevel) c.green else c.accent.copy(alpha = 0.7f),
            Offset(center.x - dx * l, center.y - dy * l), Offset(center.x + dx * l, center.y + dy * l),
            1.5.dp.toPx(),
        )
    }
}

@Composable
private fun CalibrationSheet(visible: Boolean, engine: LevelEngine, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    val scope = rememberCoroutineScope()
    var kind by remember { mutableIntStateOf(0) }
    var step by remember { mutableIntStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var first by remember { mutableStateOf<UpVector?>(null) }
    var firstEdge by remember { mutableStateOf(0.0) }
    var busy by remember { mutableStateOf(false) }

    suspend fun average(): UpVector? {
        busy = true
        progress = 0f
        val samples = mutableListOf<UpVector>()
        val start = System.nanoTime()
        while (System.nanoTime() - start < 1_500_000_000L) {
            withTimeoutOrNull(200) { engine.up.filterNotNull().first() }?.let { samples += it }
            progress = ((System.nanoTime() - start) / 1.5e9f).coerceAtMost(1f)
            delay(20)
        }
        busy = false
        if (samples.isEmpty()) return null
        val x = samples.map { it.x }.average()
        val y = samples.map { it.y }.average()
        val z = samples.map { it.z }.average()
        val n = sqrt(x * x + y * y + z * z)
        return UpVector(x / n, y / n, z / n, samples.last().timestampNs, samples.last().magnitude)
    }

    GlassSheet(visible, onDismiss = {
        step = 0
        onDismiss()
    }, title = "Calibrate level") {
        DText(
            "Two-point reversal calibration removes your sensor's offset without needing a perfectly flat surface.",
            Datum.type.callout, c.labelSecondary,
        )
        Spacer(Modifier.height(14.dp))
        SegmentedControl(listOf("Lying flat", "On its edge"), kind, onSelect = {
            kind = it
            step = 0
        }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier.fillMaxWidth().card(Shapes.lg).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                ProgressRing(if (busy) progress else if (step > 0) 1f else 0f, Modifier.size(56.dp))
                DIcon(DatumIcons.Phone, tint = c.accent, modifier = Modifier.graphicsLayer { rotationZ = if (step == 1) 180f else 0f }, size = 26.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                DText(if (step == 0) "Step 1 of 2" else "Step 2 of 2", Datum.type.caption, c.labelSecondary)
                DText(
                    when {
                        kind == 0 && step == 0 -> "Lay the phone on a stable surface, screen up."
                        kind == 0 -> "Rotate it 180° on the same spot, then record again."
                        step == 0 -> "Stand the phone on its long edge against something solid."
                        else -> "Flip it onto the opposite edge, same spot, and record."
                    },
                    Datum.type.headline,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        PillButton(
            if (busy) "Hold still…" else if (step == 0) "Record first reading" else "Record second reading",
            enabled = !busy,
            onClick = {
                scope.launch {
                    val v = average() ?: return@launch
                    app.haptics.capture()
                    if (step == 0) {
                        first = v
                        firstEdge = LevelMath.angles(v, settings.levelCalibration.copy(edgeOffsetDeg = 0.0)).edgeDeg
                        step = 1
                    } else {
                        val a = first ?: return@launch
                        if (kind == 0) {
                            val (bx, by) = LevelMath.surfaceBias(a, v)
                            app.settings.update { it.copy(levelCalibration = it.levelCalibration.copy(biasX = bx, biasY = by)) }
                        } else {
                            val second = LevelMath.angles(v, settings.levelCalibration.copy(edgeOffsetDeg = 0.0)).edgeDeg
                            val off = LevelMath.edgeOffset(firstEdge, second)
                            app.settings.update { it.copy(levelCalibration = it.levelCalibration.copy(edgeOffsetDeg = off)) }
                        }
                        app.haptics.success()
                        hud.show(DatumIcons.Check, "Calibrated")
                        step = 0
                        onDismiss()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        val cal = settings.levelCalibration
        Row(verticalAlignment = Alignment.CenterVertically) {
            DText(
                "Offsets: X %.3f  Y %.3f  edge %.2f°".format(cal.biasX, cal.biasY, cal.edgeOffsetDeg),
                Datum.type.footnote, c.labelSecondary, Modifier.weight(1f),
            )
            PillButton("Reset", onClick = {
                app.settings.update { it.copy(levelCalibration = com.akhielesh.datum.core.sensors.LevelCalibration()) }
                hud.show(DatumIcons.Reset, "Reset")
            }, style = ButtonStyle.Destructive, compact = true)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DText("Sound guidance", Datum.type.body)
                DText("Beeps quicken as you approach level — for when you can't see the screen.", Datum.type.footnote, c.labelSecondary)
            }
            MacSwitch(settings.levelSoundGuide, { on -> app.settings.update { it.copy(levelSoundGuide = on) } })
        }
    }
}
