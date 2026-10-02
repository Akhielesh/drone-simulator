package com.akhielesh.datum.ui.screens.angle

import android.Manifest
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.core.sensors.LevelEngine
import com.akhielesh.datum.core.sensors.SlopeUnit
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.common.CameraPreview
import com.akhielesh.datum.ui.common.EngineLifecycle
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.common.rememberPermissionState
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.components.pressable
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.InterText
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private enum class AngleMode(val label: String) { Bevel("Bevel"), Incline("Incline"), Protractor("Protractor") }

@Composable
fun AngleScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val c = Datum.colors
    KeepScreenOn()
    val engine = remember { LevelEngine(app.sensors) }
    EngineLifecycle(engine, engine::start, engine::stop)
    val up by engine.up.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(AngleMode.Bevel) }
    var reference by remember { mutableStateOf<Vec3?>(null) }
    var unit by remember { mutableStateOf(SlopeUnit.DEGREES) }
    var held by remember { mutableStateOf<Double?>(null) }
    // Protractor arms (degrees, screen space, 0 = right, counter-clockwise positive).
    var armA by remember { mutableFloatStateOf(0f) }
    var armB by remember { mutableFloatStateOf(52f) }
    var useCamera by remember { mutableStateOf(false) }
    val camera = rememberPermissionState(Manifest.permission.CAMERA)

    val u = up?.let { Vec3(it.x, it.y, it.z) }
    val bevel = if (u != null && reference != null) Math.toDegrees(u.angleTo(reference!!)) else null
    val longEdge = u?.let { Math.toDegrees(asin(it.y.coerceIn(-1.0, 1.0))) }
    val shortEdge = u?.let { Math.toDegrees(asin(it.x.coerceIn(-1.0, 1.0))) }
    var protractor = abs(armB - armA).toDouble() % 360
    if (protractor > 180) protractor = 360 - protractor
    val live: Double? = when (mode) {
        AngleMode.Bevel -> bevel
        AngleMode.Incline -> longEdge
        AngleMode.Protractor -> protractor
    }
    val value = held ?: live

    Box(Modifier.fillMaxSize()) {
        if (mode == AngleMode.Protractor && useCamera && camera.granted) {
            CameraPreview(Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.15f)))
        }
        Column(Modifier.fillMaxSize()) {
            TopBar("Angle", subtitle = when (mode) {
                AngleMode.Bevel -> "Angle between two surfaces"
                AngleMode.Incline -> "Tilt of the phone's edge"
                AngleMode.Protractor -> "Drag the arms"
            }, onBack = { nav.back() })
            SegmentedControl(
                AngleMode.entries.map { it.label }, mode.ordinal,
                onSelect = {
                    mode = AngleMode.entries[it]
                    held = null
                },
                modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Column(Modifier.fillMaxWidth().pressable { unit = unit.next() }, horizontalAlignment = Alignment.CenterHorizontally) {
                DText(
                    value?.let { if (mode == AngleMode.Incline) unit.format(it) else "%.1f°".format(it) } ?: "—",
                    Datum.type.readoutXL, if (held != null) c.accent else c.label,
                )
                DText(
                    when (mode) {
                        AngleMode.Bevel -> if (reference == null) "Lay the phone on the first surface and tap Set" else "Supplement %.1f°  ·  tap Set to re-zero".format(180 - (value ?: 0.0))
                        AngleMode.Incline -> "Short edge %.1f°  ·  tap the number for slope units".format(shortEdge ?: 0.0)
                        AngleMode.Protractor -> "Reflex %.1f°".format(360 - protractor)
                    },
                    Datum.type.subhead, c.labelSecondary,
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when (mode) {
                    AngleMode.Protractor -> Protractor(armA, armB, { armA = it }, { armB = it }, onDark = useCamera && camera.granted)
                    else -> AngleDial(value ?: 0.0, mode == AngleMode.Bevel && reference == null)
                }
            }
            Row(Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                when (mode) {
                    AngleMode.Bevel -> ToolButton(DatumIcons.Target, if (reference == null) "Set" else "Re-zero", {
                        reference = u
                        held = null
                        app.haptics.capture()
                    }, active = reference != null)
                    AngleMode.Protractor -> ToolButton(DatumIcons.Camera, "Camera", {
                        if (!camera.granted) camera.request()
                        useCamera = !useCamera
                    }, active = useCamera && camera.granted)
                    AngleMode.Incline -> ToolButton(DatumIcons.Axis, "Units", { unit = unit.next() })
                }
                ToolButton(if (held != null) DatumIcons.Unlock else DatumIcons.Lock, if (held != null) "Release" else "Hold", {
                    held = if (held == null) live else null
                }, active = held != null)
                ToolButton(DatumIcons.Download, "Save", {
                    val v = value ?: return@ToolButton
                    app.library.add(
                        Measurement(
                            kind = MeasureKind.ANGLE, title = "Angle · ${mode.label}", value = v, quantity = Quantity.ANGLE,
                            method = mode.label,
                            details = if (mode == AngleMode.Incline) listOf(Detail("Short edge", shortEdge ?: 0.0, Quantity.ANGLE)) else emptyList(),
                        ),
                    )
                    hud.show(DatumIcons.Check, "Saved")
                })
            }
        }
    }
}

/** Semicircular protractor dial with an animated needle. */
@Composable
private fun AngleDial(angle: Double, idle: Boolean) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val style = TextStyle(fontFamily = InterText, fontSize = 10.sp, color = c.labelSecondary, fontFeatureSettings = "tnum")
    val needle = remember { Animatable(0f) }
    LaunchedEffect(angle) { needle.animateTo(angle.toFloat().coerceIn(-180f, 180f), spring(dampingRatio = 0.7f, stiffness = 200f)) }
    Canvas(Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 24.dp)) {
        val r = min(size.width / 2, size.height) * 0.92f
        val center = Offset(size.width / 2, size.height * 0.95f)
        drawArc(c.fillTertiary, 180f, 180f, true, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r))
        for (deg in 0..180 step 5) {
            val a = Math.toRadians(180.0 - deg)
            val inner = if (deg % 30 == 0) r * 0.86f else if (deg % 10 == 0) r * 0.9f else r * 0.94f
            val p1 = Offset(center.x + (cos(a) * inner).toFloat(), center.y - (sin(a) * inner).toFloat())
            val p2 = Offset(center.x + (cos(a) * r).toFloat(), center.y - (sin(a) * r).toFloat())
            drawLine(if (deg % 30 == 0) c.label else c.labelTertiary, p1, p2, if (deg % 30 == 0) 1.5.dp.toPx() else 1.dp.toPx())
            if (deg % 30 == 0) {
                val l = measurer.measure("$deg°", style)
                val lp = Offset(center.x + (cos(a) * r * 0.76f).toFloat() - l.size.width / 2, center.y - (sin(a) * r * 0.76f).toFloat() - l.size.height / 2)
                drawText(l, topLeft = lp)
            }
        }
        if (!idle) {
            val a = Math.toRadians(180.0 - abs(needle.value))
            drawArc(c.accent.copy(alpha = 0.18f), 180f, abs(needle.value), true, Offset(center.x - r * 0.6f, center.y - r * 0.6f), Size(r * 1.2f, r * 1.2f))
            drawLine(c.labelTertiary, center, Offset(center.x - r, center.y), 2.dp.toPx(), StrokeCap.Round)
            drawLine(c.accent, center, Offset(center.x + (cos(a) * r).toFloat(), center.y - (sin(a) * r).toFloat()), 3.dp.toPx(), StrokeCap.Round)
        }
        drawCircle(c.accent, 6.dp.toPx(), center)
    }
}

/** Two draggable arms from a common vertex; reads the angle between them. */
@Composable
private fun Protractor(armA: Float, armB: Float, onA: (Float) -> Unit, onB: (Float) -> Unit, onDark: Boolean) {
    val c = Datum.colors
    val app = LocalApp.current
    val line = if (onDark) Color.White else c.label
    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val vertex = Offset(size.width / 2f, size.height * 0.62f)
                    fun angleOf(p: Offset) = Math.toDegrees(atan2((vertex.y - p.y).toDouble(), (p.x - vertex.x).toDouble())).toFloat()
                    val a = angleOf(down.position)
                    val pickA = abs(wrap(a - armA)) < abs(wrap(a - armB))
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull() ?: break
                        if (!ch.pressed) break
                        val ang = angleOf(ch.position)
                        // Gentle snap to whole degrees.
                        val snapped = Math.round(ang).toFloat()
                        if (pickA) onA(snapped) else onB(snapped)
                        ch.consume()
                    }
                    app.haptics.tick(0.5f)
                }
            },
    ) {
        val vertex = Offset(size.width / 2f, size.height * 0.62f)
        val len = min(size.width, size.height) * 0.46f
        fun tip(a: Float) = Offset(vertex.x + (cos(Math.toRadians(a.toDouble())) * len).toFloat(), vertex.y - (sin(Math.toRadians(a.toDouble())) * len).toFloat())
        val start = -maxOf(armA, armB)
        var sweep = abs(armB - armA)
        if (sweep > 180) sweep = 360 - sweep
        // Draw the smaller of the two arcs between the arms (Compose angles run clockwise).
        val from = if (abs(armB - armA) > 180) -minOf(armA, armB) else start
        drawArc(c.accent.copy(alpha = 0.22f), from, sweep, true, Offset(vertex.x - len * 0.4f, vertex.y - len * 0.4f), Size(len * 0.8f, len * 0.8f))
        drawArc(c.accent, from, sweep, false, Offset(vertex.x - len * 0.4f, vertex.y - len * 0.4f), Size(len * 0.8f, len * 0.8f), style = Stroke(2.dp.toPx()))
        listOf(armA, armB).forEach { a ->
            drawLine(Color.Black.copy(alpha = 0.25f), vertex, tip(a), 6.dp.toPx(), StrokeCap.Round)
            drawLine(line, vertex, tip(a), 3.dp.toPx(), StrokeCap.Round)
            drawCircle(c.accent, 13.dp.toPx(), tip(a))
            drawCircle(Color.White, 5.dp.toPx(), tip(a))
        }
        drawCircle(line, 6.dp.toPx(), vertex)
    }
    if (onDark) StatusChip("Line the arms up with what you see", Color.White)
}

private fun wrap(a: Float): Float {
    var x = a % 360f
    if (x > 180f) x -= 360f
    if (x < -180f) x += 360f
    return x
}
