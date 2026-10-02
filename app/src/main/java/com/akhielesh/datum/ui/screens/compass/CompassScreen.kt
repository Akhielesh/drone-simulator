package com.akhielesh.datum.ui.screens.compass

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.LocationManager
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
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.math.wrapDegrees
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.common.rememberPermissionState
import com.akhielesh.datum.ui.common.rememberSensor
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.EmptyState
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.screens.home.cardinal
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.InterText
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private data class Heading(val degrees: Float, val tiltX: Float, val tiltY: Float)

@Composable
fun CompassScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val context = LocalContext.current
    val c = Datum.colors
    KeepScreenOn()
    if (!app.sensors.has(Sensor.TYPE_ROTATION_VECTOR)) {
        Column(Modifier.fillMaxSize()) {
            TopBar("Compass", onBack = { nav.back() })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(DatumIcons.Compass, "No compass", "This phone doesn't have the magnetometer needed for a compass.")
            }
        }
        return
    }
    val reading = rememberSensor(Sensor.TYPE_ROTATION_VECTOR, 20_000) { r ->
        val m = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(m, r.values)
        // Heading of the phone's top edge, or of the camera when the phone is held upright.
        val upright = abs(m[8]) < 0.5f
        val remapped = FloatArray(9)
        val o = FloatArray(3)
        if (upright) {
            SensorManager.remapCoordinateSystem(m, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
            SensorManager.getOrientation(remapped, o)
        } else {
            SensorManager.getOrientation(m, o)
        }
        Heading(((Math.toDegrees(o[0].toDouble()) + 360) % 360).toFloat(), m[6], m[7])
    }
    val fieldReading = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD, 100_000) { it }
    val location = rememberPermissionState(Manifest.permission.ACCESS_COARSE_LOCATION)
    var trueNorth by remember { mutableStateOf(false) }
    val declination = remember(location.granted, trueNorth) { if (location.granted && trueNorth) declinationAt(context) else null }
    var target by remember { mutableStateOf<Float?>(null) }

    val magnetic = reading.value?.degrees
    val heading = magnetic?.let { (it + (declination ?: 0f) + 360f) % 360f }
    val dial = remember { Animatable(0f) }
    LaunchedEffect(heading) {
        val h = heading ?: return@LaunchedEffect
        // Animate along the shortest path; keep the value continuous (no 359→0 jump).
        val delta = wrapDegrees((h - dial.value).toDouble()).toFloat()
        dial.animateTo(dial.value + delta, spring(dampingRatio = 0.8f, stiffness = 180f))
    }
    val field = fieldReading.value
    val strength = field?.let { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }
    val accuracy = field?.accuracy ?: SensorManager.SENSOR_STATUS_ACCURACY_HIGH

    Column(Modifier.fillMaxSize()) {
        TopBar("Compass", subtitle = if (declination != null) "True north" else "Magnetic north", onBack = { nav.back() })
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            DText(heading?.let { "%.0f°".format(it) } ?: "—", Datum.type.readoutXL)
            DText(heading?.let { cardinal(it) } ?: "", Datum.type.title2, c.labelSecondary)
            target?.let { t ->
                val off = heading?.let { wrapDegrees((it - t).toDouble()) } ?: 0.0
                Spacer(Modifier.height(6.dp))
                StatusChip(
                    if (abs(off) < 2) "On bearing %.0f°".format(t) else "%.0f° %s of %.0f°".format(abs(off), if (off > 0) "right" else "left", t),
                    if (abs(off) < 2) c.green else c.accent,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Dial(dial.value, target, reading.value)
        }
        Column(Modifier.padding(horizontal = 18.dp).fillMaxWidth().card(Shapes.xl).padding(horizontal = 16.dp, vertical = 6.dp)) {
            KeyValueRow("Field strength", strength?.let { "%.1f".format(it) } ?: "—", unit = "µT", hint = "Earth: 25–65 µT; much more means metal nearby")
            KeyValueRow(
                "Calibration",
                when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Good"
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Fair"
                    else -> "Wave the phone in a figure-8"
                },
                accent = if (accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) c.green else c.orange,
            )
            declination?.let { KeyValueRow("Declination", "%+.1f°".format(it), hint = "added to magnetic heading") }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(DatumIcons.Target, if (target == null) "Lock bearing" else "Clear", {
                target = if (target == null) heading else null
                app.haptics.click()
            }, active = target != null)
            ToolButton(DatumIcons.Compass, "True north", {
                if (!location.granted) location.request()
                trueNorth = !trueNorth
            }, active = trueNorth && location.granted)
            ToolButton(DatumIcons.Download, "Save", {
                val h = heading ?: return@ToolButton
                app.library.add(
                    Measurement(
                        kind = MeasureKind.COMPASS, title = "Bearing ${cardinal(h)}", value = h.toDouble(), quantity = Quantity.HEADING,
                        method = if (declination != null) "True north" else "Magnetic",
                        details = listOfNotNull(strength?.let { Detail("Field (µT)", it.toDouble(), Quantity.PLAIN) }),
                    ),
                )
                hud.show(DatumIcons.Check, "Saved")
            })
        }
    }
}

@SuppressLint("MissingPermission")
private fun declinationAt(context: Context): Float? = runCatching {
    val lm = context.getSystemService(LocationManager::class.java)
    val loc = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time } ?: return@runCatching null
    GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), System.currentTimeMillis()).declination
}.getOrNull()

@Composable
private fun Dial(rotation: Float, target: Float?, reading: Heading?) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val major = TextStyle(fontFamily = InterText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = c.label)
    val minor = TextStyle(fontFamily = InterText, fontSize = 11.sp, color = c.labelSecondary, fontFeatureSettings = "tnum")
    Canvas(Modifier.size(300.dp)) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(c.surface, r, center)
        drawCircle(c.glassBorder, r, center, style = Stroke(1.dp.toPx()))
        rotate(-rotation, center) {
            for (deg in 0 until 360 step 5) {
                val a = Math.toRadians(deg.toDouble() - 90)
                val len = if (deg % 30 == 0) r * 0.12f else if (deg % 10 == 0) r * 0.08f else r * 0.05f
                val p1 = Offset(center.x + (cos(a) * (r - 6.dp.toPx())).toFloat(), center.y + (sin(a) * (r - 6.dp.toPx())).toFloat())
                val p2 = Offset(center.x + (cos(a) * (r - 6.dp.toPx() - len)).toFloat(), center.y + (sin(a) * (r - 6.dp.toPx() - len)).toFloat())
                drawLine(if (deg == 0) c.red else if (deg % 30 == 0) c.label else c.labelTertiary, p1, p2, if (deg % 30 == 0) 2.dp.toPx() else 1.dp.toPx())
                if (deg % 30 == 0) {
                    val label = when (deg) {
                        0 -> "N"
                        90 -> "E"
                        180 -> "S"
                        270 -> "W"
                        else -> "$deg"
                    }
                    val l = measurer.measure(label, if (deg % 90 == 0) major.copy(color = if (deg == 0) c.red else c.label) else minor)
                    val rr = r * 0.7f
                    val pc = Offset(center.x + (cos(a) * rr).toFloat(), center.y + (sin(a) * rr).toFloat())
                    rotate(rotation, pc) {
                        drawText(l, topLeft = Offset(pc.x - l.size.width / 2, pc.y - l.size.height / 2))
                    }
                }
            }
            target?.let { t ->
                val a = Math.toRadians(t.toDouble() - 90)
                val p = Offset(center.x + (cos(a) * (r - 22.dp.toPx())).toFloat(), center.y + (sin(a) * (r - 22.dp.toPx())).toFloat())
                drawCircle(c.accent, 7.dp.toPx(), p)
            }
        }
        // Fixed lubber line.
        val tri = Path().apply {
            moveTo(center.x, center.y - r + 2.dp.toPx())
            lineTo(center.x - 9.dp.toPx(), center.y - r - 12.dp.toPx())
            lineTo(center.x + 9.dp.toPx(), center.y - r - 12.dp.toPx())
            close()
        }
        drawPath(tri, c.accent)
        // Tilt bubble (keep the phone flat for an accurate heading).
        if (reading != null) {
            val k = r * 0.32f
            drawCircle(c.fillTertiary, r * 0.36f, center)
            drawCircle(c.separator, r * 0.06f, center, style = Stroke(1.dp.toPx()))
            // R[6], R[7]: world-up components of the device x / y axes, so the bubble drifts to the high side.
            drawCircle(c.accent.copy(alpha = 0.8f), r * 0.05f, Offset(center.x + reading.tiltX * k, center.y - reading.tiltY * k))
        }
        drawLine(c.labelTertiary, Offset(center.x, center.y - r * 0.36f), Offset(center.x, center.y + r * 0.36f), 0.8.dp.toPx())
        drawLine(c.labelTertiary, Offset(center.x - r * 0.36f, center.y), Offset(center.x + r * 0.36f, center.y), 0.8.dp.toPx())
    }
}
