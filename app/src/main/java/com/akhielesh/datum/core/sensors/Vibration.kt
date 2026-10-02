package com.akhielesh.datum.core.sensors

import android.hardware.Sensor
import com.akhielesh.datum.core.math.LowPass
import com.akhielesh.datum.core.math.magnitudeSpectrum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

data class VibrationSnapshot(
    /** Recent acceleration magnitude (gravity removed), m/s², oldest first. */
    val trace: FloatArray = FloatArray(0),
    val rms: Double = 0.0,
    val peak: Double = 0.0,
    val dominantHz: Double = 0.0,
    val spectrum: FloatArray = FloatArray(0),
    val nyquistHz: Double = 0.0,
    val sampleRateHz: Double = 0.0,
)

/** Modified Mercalli intensity from peak ground acceleration (USGS ShakeMap thresholds, in g). */
fun mercalli(pgaG: Double): Pair<String, String> = when {
    pgaG < 0.0017 -> "I" to "Not felt"
    pgaG < 0.014 -> "II–III" to "Weak"
    pgaG < 0.039 -> "IV" to "Light"
    pgaG < 0.092 -> "V" to "Moderate"
    pgaG < 0.18 -> "VI" to "Strong"
    pgaG < 0.34 -> "VII" to "Very strong"
    pgaG < 0.65 -> "VIII" to "Severe"
    pgaG < 1.24 -> "IX" to "Violent"
    else -> "X+" to "Extreme"
}

/**
 * Vibration analyser: high-passes the accelerometer (removing gravity), keeps a seismograph trace
 * and runs an FFT on the most active axis to find the dominant frequency (Hz → RPM for motors).
 */
class VibrationEngine(private val hub: SensorHub) {
    private val _state = MutableStateFlow(VibrationSnapshot())
    val state: StateFlow<VibrationSnapshot> = _state.asStateFlow()
    private var unregister: (() -> Unit)? = null

    private val n = 1024
    private val ax = DoubleArray(n)
    private val ay = DoubleArray(n)
    private val az = DoubleArray(n)
    private val mag = FloatArray(n)
    private var head = 0
    private var filled = 0
    private val gx = LowPass(0.6)
    private val gy = LowPass(0.6)
    private val gz = LowPass(0.6)
    private var lastNs = 0L
    private var rate = 0.0
    private var lastPublish = 0L
    private var lastFft = 0L
    private var spectrum = FloatArray(0)
    private var dominant = 0.0

    fun start() {
        if (unregister != null) return
        filled = 0
        head = 0
        gx.reset(); gy.reset(); gz.reset()
        unregister = hub.listen(intArrayOf(Sensor.TYPE_ACCELEROMETER), 2_500) { e ->
            val t = e.timestamp
            if (lastNs != 0L) {
                val dt = (t - lastNs) / 1e9
                if (dt > 0) rate = if (rate == 0.0) 1 / dt else rate * 0.99 + (1 / dt) * 0.01
            }
            lastNs = t
            val x = e.values[0] - gx.update(e.values[0].toDouble(), t)
            val y = e.values[1] - gy.update(e.values[1].toDouble(), t)
            val z = e.values[2] - gz.update(e.values[2].toDouble(), t)
            ax[head] = x; ay[head] = y; az[head] = z
            mag[head] = sqrt(x * x + y * y + z * z).toFloat()
            head = (head + 1) % n
            filled = (filled + 1).coerceAtMost(n)
            if (t - lastFft > 120_000_000L && filled == n) {
                lastFft = t
                analyse()
            }
            if (t - lastPublish > 33_000_000L) {
                lastPublish = t
                publish()
            }
        }
    }

    fun stop() {
        unregister?.invoke()
        unregister = null
    }

    private fun ordered(src: DoubleArray) = DoubleArray(n) { src[(head + it) % n] }

    private fun analyse() {
        // Use the axis with the most energy (keeps sign, unlike the magnitude).
        val axes = listOf(ordered(ax), ordered(ay), ordered(az))
        val best = axes.maxBy { a -> a.sumOf { it * it } }
        val spec = magnitudeSpectrum(best)
        val binHz = rate / n
        var bestK = 0
        for (k in 2 until spec.size) if (spec[k] > spec[bestK]) bestK = k
        dominant = if (spec.isNotEmpty() && spec[bestK] > 0.002) bestK * binHz else 0.0
        // 48 display bars up to Nyquist.
        val bars = 48
        val per = max(1, spec.size / bars)
        spectrum = FloatArray(bars) { b ->
            var m = 0.0
            for (k in b * per until minOf(spec.size, (b + 1) * per)) m = max(m, spec[k])
            m.toFloat()
        }
    }

    private fun publish() {
        val count = filled
        if (count == 0) return
        val trace = FloatArray(count) { mag[(head - count + it + n) % n] }
        var sum = 0.0
        var peak = 0.0
        val window = minOf(count, (rate * 2).toInt().coerceAtLeast(1))
        for (i in count - window until count) {
            val v = trace[i].toDouble()
            sum += v * v
            peak = max(peak, abs(v))
        }
        _state.value = VibrationSnapshot(
            trace = trace,
            rms = sqrt(sum / window),
            peak = peak,
            dominantHz = dominant,
            spectrum = spectrum,
            nyquistHz = rate / 2,
            sampleRateHz = rate,
        )
    }
}
