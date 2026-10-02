package com.akhielesh.datum.core.math

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One Euro filter (Casiez et al. 2012): heavy smoothing while the signal is steady, low lag while it
 * moves. Ideal for a level that must be rock steady at rest yet respond instantly when tilted.
 */
class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.02,
    private val dCutoff: Double = 1.0,
) {
    private var xPrev = Double.NaN
    private var dxPrev = 0.0
    private var tPrev = 0L

    fun reset() {
        xPrev = Double.NaN
        dxPrev = 0.0
    }

    fun filter(x: Double, timestampNs: Long): Double {
        if (xPrev.isNaN()) {
            xPrev = x
            tPrev = timestampNs
            return x
        }
        val dt = ((timestampNs - tPrev) / 1e9).coerceIn(1e-4, 0.5)
        tPrev = timestampNs
        val dx = (x - xPrev) / dt
        val edx = lerp(dxPrev, dx, alpha(dt, dCutoff))
        dxPrev = edx
        val cutoff = minCutoff + beta * abs(edx)
        val result = lerp(xPrev, x, alpha(dt, cutoff))
        xPrev = result
        return result
    }

    private fun alpha(dt: Double, cutoff: Double): Double {
        val tau = 1.0 / (2 * PI * cutoff)
        return 1.0 / (1.0 + tau / dt)
    }

    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t
}

/** First-order low-pass with a time constant, robust to irregular sensor timestamps. */
class LowPass(private val tauSeconds: Double) {
    var value = Double.NaN
        private set
    private var tPrev = 0L

    fun reset() {
        value = Double.NaN
    }

    fun update(x: Double, timestampNs: Long): Double {
        if (value.isNaN()) {
            value = x
            tPrev = timestampNs
            return x
        }
        val dt = ((timestampNs - tPrev) / 1e9).coerceIn(0.0, 1.0)
        tPrev = timestampNs
        val a = 1.0 - exp(-dt / tauSeconds)
        value += (x - value) * a
        return value
    }
}

/** Welford running mean / variance. */
class RunningStats {
    var count = 0L
        private set
    var mean = 0.0
        private set
    private var m2 = 0.0

    fun add(x: Double) {
        count++
        val d = x - mean
        mean += d / count
        m2 += d * (x - mean)
    }

    val variance: Double get() = if (count > 1) m2 / (count - 1) else 0.0
    val std: Double get() = sqrt(variance)

    fun reset() {
        count = 0; mean = 0.0; m2 = 0.0
    }
}

/** Fixed-capacity ring buffer of doubles with O(1) push and windowed statistics. */
class DoubleRing(val capacity: Int) {
    private val data = DoubleArray(capacity)
    private var start = 0
    var size = 0
        private set

    fun push(v: Double) {
        if (size < capacity) {
            data[(start + size) % capacity] = v
            size++
        } else {
            data[start] = v
            start = (start + 1) % capacity
        }
    }

    operator fun get(i: Int): Double = data[(start + i) % capacity]

    fun clear() {
        start = 0; size = 0
    }

    fun mean(): Double {
        if (size == 0) return 0.0
        var s = 0.0
        for (i in 0 until size) s += get(i)
        return s / size
    }

    fun std(): Double {
        if (size < 2) return 0.0
        val m = mean()
        var s = 0.0
        for (i in 0 until size) {
            val d = get(i) - m
            s += d * d
        }
        return sqrt(s / (size - 1))
    }

    fun max(): Double {
        var m = Double.NEGATIVE_INFINITY
        for (i in 0 until size) if (get(i) > m) m = get(i)
        return m
    }

    fun min(): Double {
        var m = Double.POSITIVE_INFINITY
        for (i in 0 until size) if (get(i) < m) m = get(i)
        return m
    }

    fun toArray(): DoubleArray = DoubleArray(size) { get(it) }
}

/** In-place iterative radix-2 FFT. [re] and [im] must have the same power-of-two length. */
fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    require(n == im.size && n and (n - 1) == 0) { "FFT size must be a power of two" }
    var j = 0
    for (i in 1 until n) {
        var bit = n shr 1
        while (j and bit != 0) {
            j = j xor bit
            bit = bit shr 1
        }
        j = j xor bit
        if (i < j) {
            var t = re[i]; re[i] = re[j]; re[j] = t
            t = im[i]; im[i] = im[j]; im[j] = t
        }
    }
    var len = 2
    while (len <= n) {
        val ang = -2 * PI / len
        val wr = cos(ang)
        val wi = sin(ang)
        var i = 0
        while (i < n) {
            var cr = 1.0
            var ci = 0.0
            for (k in 0 until len / 2) {
                val ur = re[i + k]
                val ui = im[i + k]
                val vr = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                val vi = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                re[i + k] = ur + vr
                im[i + k] = ui + vi
                re[i + k + len / 2] = ur - vr
                im[i + k + len / 2] = ui - vi
                val nr = cr * wr - ci * wi
                ci = cr * wi + ci * wr
                cr = nr
            }
            i += len
        }
        len = len shl 1
    }
}

/** Hann-windowed magnitude spectrum of [samples] (length must be a power of two). */
fun magnitudeSpectrum(samples: DoubleArray): DoubleArray {
    val n = samples.size
    val re = DoubleArray(n)
    val im = DoubleArray(n)
    val mean = samples.average()
    for (i in 0 until n) {
        val w = 0.5 * (1 - cos(2 * PI * i / (n - 1)))
        re[i] = (samples[i] - mean) * w
    }
    fft(re, im)
    return DoubleArray(n / 2) { k -> sqrt(re[k] * re[k] + im[k] * im[k]) * 2.0 / n }
}

fun log10(x: Double) = ln(x) / ln(10.0)

fun Double.toDegrees() = this * 180.0 / PI
fun Double.toRadians() = this * PI / 180.0

/** Wraps an angle in degrees into (-180, 180]. */
fun wrapDegrees(a: Double): Double {
    var x = a % 360.0
    if (x <= -180) x += 360
    if (x > 180) x -= 360
    return x
}
