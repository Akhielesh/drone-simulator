package com.akhielesh.datum.core.sensors

import android.hardware.Sensor
import android.view.Surface
import com.akhielesh.datum.core.math.OneEuroFilter
import com.akhielesh.datum.core.math.toDegrees
import com.akhielesh.datum.core.math.wrapDegrees
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Filtered "up" direction (unit vector, device frame) – the raw material of every level reading. */
data class UpVector(val x: Double, val y: Double, val z: Double, val timestampNs: Long, val magnitude: Double)

/**
 * Spirit-level sensor pipeline: accelerometer at ~100 Hz through a One Euro filter, so the bubble
 * is glass-steady at rest yet follows quick tilts without lag.
 */
class LevelEngine(private val hub: SensorHub) {
    private val _up = MutableStateFlow<UpVector?>(null)
    val up: StateFlow<UpVector?> = _up.asStateFlow()

    private val fx = OneEuroFilter(minCutoff = 0.6, beta = 0.6)
    private val fy = OneEuroFilter(minCutoff = 0.6, beta = 0.6)
    private val fz = OneEuroFilter(minCutoff = 0.6, beta = 0.6)
    private var unregister: (() -> Unit)? = null

    fun start() {
        if (unregister != null) return
        fx.reset(); fy.reset(); fz.reset()
        unregister = hub.listen(intArrayOf(Sensor.TYPE_ACCELEROMETER), 10_000) { e ->
            val x = fx.filter(e.values[0].toDouble(), e.timestamp)
            val y = fy.filter(e.values[1].toDouble(), e.timestamp)
            val z = fz.filter(e.values[2].toDouble(), e.timestamp)
            val n = sqrt(x * x + y * y + z * z)
            if (n > 1e-3) _up.value = UpVector(x / n, y / n, z / n, e.timestamp, n)
        }
    }

    fun stop() {
        unregister?.invoke()
        unregister = null
    }
}

/** Offsets measured by the two-point calibration (see [LevelMath.surfaceBias]). */
data class LevelCalibration(
    val biasX: Double = 0.0,
    val biasY: Double = 0.0,
    val edgeOffsetDeg: Double = 0.0,
)

data class LevelAngles(
    /** Surface mode: tilt toward the right edge (+ = right edge high), degrees. */
    val rollDeg: Double,
    /** Surface mode: tilt toward the top edge (+ = top edge high), degrees. */
    val pitchDeg: Double,
    /** Angle between the screen normal and vertical: 0 = perfectly flat. */
    val tiltDeg: Double,
    /** Orientation of "up" within the screen plane, degrees clockwise (0 = portrait upright). */
    val screenAngleDeg: Double,
    /** Edge mode: deviation from the nearest 90° step, degrees. */
    val edgeDeg: Double,
    /** Nearest 90° step: 0 portrait, 1 landscape (left side down), 2 upside down, 3 landscape. */
    val quadrant: Int,
    /** |z| of the up vector: 1 = lying flat, 0 = standing on an edge. */
    val flatness: Double,
    val faceDown: Boolean,
    /** Bubble offset in screen units (−1..1 maps to the vial radius at ~±(range) degrees). */
    val bubbleX: Double,
    val bubbleY: Double,
)

object LevelMath {

    /** Remaps device x/y into the current screen axes (identity in natural portrait). */
    fun toScreen(x: Double, y: Double, rotation: Int): Pair<Double, Double> = when (rotation) {
        Surface.ROTATION_90 -> -y to x
        Surface.ROTATION_180 -> -x to -y
        Surface.ROTATION_270 -> y to -x
        else -> x to y
    }

    fun angles(up: UpVector, cal: LevelCalibration, rotation: Int = Surface.ROTATION_0, bubbleRangeDeg: Double = 10.0): LevelAngles {
        val (sx, sy) = toScreen(up.x - cal.biasX, up.y - cal.biasY, rotation)
        val z = up.z
        val n = sqrt(sx * sx + sy * sy + z * z)
        val ux = sx / n
        val uy = sy / n
        val uz = z / n
        val az = abs(uz)
        val roll = atan2(ux, az).toDegrees()
        val pitch = atan2(uy, az).toDegrees()
        val tilt = acos(az.coerceIn(-1.0, 1.0)).toDegrees()
        val screen = atan2(ux, uy).toDegrees()
        val quadrant = ((screen / 90.0).roundToInt() % 4 + 4) % 4
        val edge = wrapDegrees(screen - quadrant * 90.0 - cal.edgeOffsetDeg)
        // Bubble: drifts toward the high side; screen y grows downward.
        val scale = 1.0 / kotlin.math.sin(Math.toRadians(bubbleRangeDeg))
        return LevelAngles(
            rollDeg = roll,
            pitchDeg = pitch,
            tiltDeg = tilt,
            screenAngleDeg = screen,
            edgeDeg = edge,
            quadrant = quadrant,
            flatness = az,
            faceDown = uz < 0,
            bubbleX = (ux * scale).coerceIn(-1.5, 1.5),
            bubbleY = (-uy * scale).coerceIn(-1.5, 1.5),
        )
    }

    /**
     * Two-point (reversal) calibration: reading 1, rotate the phone 180° in place, reading 2.
     * The surface's own slope flips sign between the readings while the sensor offset doesn't,
     * so their mean is the offset alone.
     */
    fun surfaceBias(first: UpVector, second: UpVector): Pair<Double, Double> =
        (first.x + second.x) / 2 to (first.y + second.y) / 2

    fun edgeOffset(firstEdgeDeg: Double, secondEdgeDeg: Double): Double = (firstEdgeDeg + secondEdgeDeg) / 2
}

/** Slope expressed in the units carpenters, roofers and surveyors actually use. */
enum class SlopeUnit(val label: String) {
    DEGREES("°"), PERCENT("%"), MM_PER_M("mm/m"), PITCH("in/ft"), RATIO("1:x");

    fun format(deg: Double): String {
        val t = kotlin.math.tan(Math.toRadians(deg))
        return when (this) {
            DEGREES -> "%.1f°".format(deg)
            PERCENT -> "%.1f%%".format(t * 100)
            MM_PER_M -> "%.0f mm/m".format(t * 1000)
            PITCH -> "%.1f/12".format(abs(t) * 12)
            RATIO -> if (abs(t) < 1e-4) "flat" else "1:%.0f".format(1 / abs(t))
        }
    }

    fun next(): SlopeUnit = entries[(ordinal + 1) % entries.size]
}
