package com.akhielesh.datum.core.sensors

import com.akhielesh.datum.core.math.Quat
import com.akhielesh.datum.core.math.Vec3
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Inertial odometry for short, deliberate phone movements (sliding along a table, touching the
 * corners of a box, sliding up a wall).
 *
 * Pipeline per IMU sample:
 *  1. **Rest calibration** – while the phone is still, average the gyroscope (its bias) and the
 *     accelerometer (gravity + accelerometer bias = the "rest specific force"). This defines the
 *     reference frame N0 = the device frame at rest.
 *  2. **Attitude** – integrate bias-corrected gyro rates into a quaternion device→N0.
 *  3. **Linear acceleration** – rotate each accelerometer sample into N0 and subtract the rest
 *     specific force. Gravity and the sensor's own bias were measured together at rest, so they
 *     cancel to first order without a factory calibration.
 *  4. **Segmentation + ZUPT** – motion is cut into segments between still periods. At the end of a
 *     segment the true velocity is known to be zero, so any residual velocity is drift. A linear
 *     velocity correction (equivalent to removing a constant bias over the segment) is applied
 *     before the second integration — the "zero-velocity update" used in foot-mounted inertial
 *     navigation, and what makes phone-IMU distance measurement usable at all.
 *
 * Pure Kotlin (no Android types) so it can be exercised with simulated sensor data.
 * Not thread-safe: feed it from a single thread.
 */
class MotionTracker(private val cfg: Config = Config()) {

    data class Config(
        val calibrationSeconds: Double = 0.6,
        /** Quiet time required to end a motion segment. */
        val stillWindowSeconds: Double = 0.20,
        /** Absolute floors for the stillness detector; raised automatically on noisy sensors. */
        val accelStillStd: Double = 0.05,
        val gyroStillRate: Double = 0.03,
        /** Linear acceleration that starts a segment. */
        val accelStartThreshold: Double = 0.12,
        /** A still phone also has ~zero mean linear acceleration (variance alone misses steady braking). */
        val linearStillMean: Double = 0.07,
        val gyroStartRate: Double = 0.12,
        /** Samples kept from just before motion was detected, so no acceleration is lost. */
        val preRollSeconds: Double = 0.15,
        val noiseMultiplier: Double = 4.0,
        val maxSegmentSeconds: Double = 15.0,
        /** Multiplicative distance correction from user calibration (slide a known length). */
        val scale: Double = 1.0,
    ) {
        companion object {
            /** Phone resting on and sliding along a surface: the quietest, most accurate case. */
            val SURFACE = Config()

            /** Phone held in the hand and pressed against corners: tolerate hand tremor. */
            val HANDHELD = Config(
                calibrationSeconds = 0.5,
                stillWindowSeconds = 0.28,
                accelStillStd = 0.13,
                gyroStillRate = 0.09,
                accelStartThreshold = 0.30,
                linearStillMean = 0.15,
                gyroStartRate = 0.35,
                preRollSeconds = 0.25,
                noiseMultiplier = 3.0,
            )
        }
    }

    enum class Phase { CALIBRATING, STILL, MOVING }

    /** One completed movement between two still periods. Displacement is in the N0 frame (m). */
    data class Segment(
        val displacement: Vec3,
        val durationSeconds: Double,
        val peakSpeed: Double,
        /** 1σ estimate of the error of this segment's length, metres. */
        val sigma: Double,
        val startNs: Long,
        val endNs: Long,
    )

    sealed interface Event {
        data object Calibrated : Event
        data object MotionStarted : Event
        data class MotionStopped(val segment: Segment) : Event
    }

    var listener: ((Event) -> Unit)? = null

    var phase = Phase.CALIBRATING
        private set

    /** Committed position (N0, metres) at the end of the last segment. */
    var position: Vec3 = Vec3.ZERO
        private set

    /** Position including the in-progress segment (uncorrected). */
    var livePosition: Vec3 = Vec3.ZERO
        private set

    var liveVelocity: Vec3 = Vec3.ZERO
        private set

    /** Device → N0 rotation. */
    var orientation: Quat = Quat.IDENTITY
        private set

    /** Unit "up" vector in N0. */
    var up: Vec3 = Vec3.Z
        private set

    var calibrationProgress = 0.0
        private set

    /** Seconds the phone has been continuously still (0 while moving). */
    var stillSeconds = 0.0
        private set

    /** Accumulated 1σ uncertainty of [position], metres. */
    var sigma = 0.0
        private set

    var segmentCount = 0
        private set

    var lastSegment: Segment? = null
        private set

    /** Estimated accelerometer sample rate (Hz) – informational. */
    var sampleRateHz = 0.0
        private set

    var hasGyroscope = true

    private var gyroBias = Vec3.ZERO
    private var restForce = Vec3(0.0, 0.0, 9.80665)
    private var lastGyro = Vec3.ZERO
    private var lastGyroNs = 0L
    private var lastAccelNs = 0L

    private var accelThreshold = cfg.accelStillStd
    private var gyroThreshold = cfg.gyroStillRate

    // Calibration accumulators
    private var calStartNs = 0L
    private var calAccel = Vec3.ZERO
    private var calGyro = Vec3.ZERO
    private var calN = 0
    private var calGyroN = 0

    // Stillness windows (time-based)
    private val accWindow = TimedVecWindow()
    private val linWindow = TimedVecWindow()
    private val gyroWindow = TimedScalarWindow()
    private var quietSinceNs = 0L
    private var motionDetectedNs = 0L

    // Pre-roll of linear accelerations while still
    private val preRoll = ArrayDeque<Sample>()

    // Current segment
    private var segT = LongArray(4096)
    private var segA = Array(3) { DoubleArray(4096) }
    private var segN = 0
    private var segVel = Vec3.ZERO
    private var segPos = Vec3.ZERO
    private var lastLin = Vec3.ZERO

    private class Sample(val t: Long, val a: Vec3)

    /**
     * Clears the measured position. With [keepCalibration] the rest calibration is reused and N0 is
     * re-anchored to the phone's current attitude, so measuring can restart instantly.
     */
    fun reset(keepCalibration: Boolean = false) {
        position = Vec3.ZERO
        livePosition = Vec3.ZERO
        liveVelocity = Vec3.ZERO
        sigma = 0.0
        segmentCount = 0
        lastSegment = null
        segN = 0
        preRoll.clear()
        quietSinceNs = 0L
        if (keepCalibration && phase != Phase.CALIBRATING) {
            restForce = orientation.conjugate().rotate(restForce)
            orientation = Quat.IDENTITY
            up = restForce.normalized()
            phase = Phase.STILL
        } else {
            orientation = Quat.IDENTITY
            phase = Phase.CALIBRATING
            calibrationProgress = 0.0
            calStartNs = 0L
        }
    }

    fun onGyro(tNs: Long, wx: Double, wy: Double, wz: Double) {
        val w = Vec3(wx, wy, wz)
        if (lastGyroNs != 0L && phase != Phase.CALIBRATING) {
            val dt = ((tNs - lastGyroNs) / 1e9).coerceIn(0.0, 0.05)
            // Midpoint rule between consecutive gyro samples.
            val mid = (w + lastGyro) * 0.5 - gyroBias
            orientation = orientation.integrate(mid, dt)
        }
        lastGyro = w
        lastGyroNs = tNs
        gyroWindow.add(tNs, (w - gyroBias).length, cfg.stillWindowSeconds)
        if (phase == Phase.CALIBRATING) {
            calGyro += w
            calGyroN++
        }
    }

    fun onAccel(tNs: Long, ax: Double, ay: Double, az: Double) {
        val f = Vec3(ax, ay, az)
        val dtSec = if (lastAccelNs == 0L) 0.0 else ((tNs - lastAccelNs) / 1e9).coerceIn(0.0, 0.05)
        if (dtSec > 0) sampleRateHz = if (sampleRateHz == 0.0) 1 / dtSec else sampleRateHz * 0.98 + (1 / dtSec) * 0.02
        lastAccelNs = tNs
        accWindow.add(tNs, f, cfg.stillWindowSeconds)

        when (phase) {
            Phase.CALIBRATING -> calibrate(tNs, f)
            Phase.STILL -> {
                val lin = orientation.rotate(f) - restForce
                linWindow.add(tNs, lin, cfg.stillWindowSeconds)
                pushPreRoll(tNs, lin)
                if (startsMotion(lin)) {
                    beginSegment()
                } else {
                    stillSeconds += dtSec
                    if (windowIsStill()) refineAtRest(f, dtSec)
                    livePosition = position
                    liveVelocity = Vec3.ZERO
                }
                lastLin = lin
            }
            Phase.MOVING -> {
                val lin = orientation.rotate(f) - restForce
                linWindow.add(tNs, lin, cfg.stillWindowSeconds)
                appendSegment(tNs, lin)
                if (phase == Phase.MOVING) checkForStop(tNs)
                lastLin = lin
            }
        }
    }

    // ---------------------------------------------------------------------------------------------

    private fun startCalibration(tNs: Long) {
        calStartNs = tNs
        calAccel = Vec3.ZERO
        calGyro = Vec3.ZERO
        calN = 0
        calGyroN = 0
    }

    private fun calibrate(tNs: Long, f: Vec3) {
        if (calStartNs == 0L) startCalibration(tNs)
        val moving = accWindow.size() > 4 && (accWindow.std() > max(cfg.accelStillStd, 0.04) * 2.0 ||
            (hasGyroscope && gyroWindow.size() > 4 && gyroWindow.mean() > cfg.gyroStillRate * 2.5))
        if (moving) {
            startCalibration(tNs)
            calibrationProgress = 0.0
            return
        }
        calAccel += f
        calN++
        val elapsed = (tNs - calStartNs) / 1e9
        calibrationProgress = (elapsed / cfg.calibrationSeconds).coerceIn(0.0, 1.0)
        if (elapsed >= cfg.calibrationSeconds && calN >= 10) {
            restForce = calAccel / calN.toDouble()
            gyroBias = if (calGyroN > 0) calGyro / calGyroN.toDouble() else Vec3.ZERO
            up = restForce.normalized()
            // Adapt thresholds to this device's actual noise floor.
            accelThreshold = max(cfg.accelStillStd, accWindow.std() * cfg.noiseMultiplier)
            gyroThreshold = max(cfg.gyroStillRate, (gyroWindow.std() + 0.25 * gyroWindow.mean()) * cfg.noiseMultiplier)
            orientation = Quat.IDENTITY
            phase = Phase.STILL
            stillSeconds = elapsed
            calibrationProgress = 1.0
            listener?.invoke(Event.Calibrated)
        }
    }

    private fun startsMotion(lin: Vec3): Boolean {
        val threshold = max(cfg.accelStartThreshold, accelThreshold * 2.5)
        // Two consecutive strong samples (rejects single spikes), rotation, or a clearly noisy window.
        val strong = lin.length > threshold && lastLin.length > threshold * 0.6
        val rotating = hasGyroscope && gyroWindow.latest() > max(cfg.gyroStartRate, gyroThreshold * 2.5)
        val noisy = accWindow.size() > 4 && accWindow.std() > accelThreshold * 2.5
        return strong || rotating || noisy
    }

    private fun windowIsStill(): Boolean = accWindow.size() > 4 && accWindow.std() < accelThreshold

    private fun gyroWindowQuiet(): Boolean =
        !hasGyroscope || gyroWindow.size() < 3 || gyroWindow.mean() < gyroThreshold

    private fun checkForStop(tNs: Long) {
        val segSeconds = (segT[segN - 1] - segT[0]) / 1e9
        if (segSeconds < 0.08) return
        val linQuiet = linWindow.mean().length < max(cfg.linearStillMean, accelThreshold)
        if (windowIsStill() && linQuiet && gyroWindowQuiet() && accWindow.spanSeconds() >= cfg.stillWindowSeconds * 0.9) {
            // The quiet stretch must lie after the moment motion was detected, otherwise the
            // pre-motion samples still in the window would end the segment immediately.
            if (quietSinceNs == 0L) {
                quietSinceNs = max(tNs - (accWindow.spanSeconds() * 1e9).toLong(), motionDetectedNs)
            }
            // A fast-looking but quiet window may be smooth constant-velocity sliding: wait longer.
            val required = if (liveVelocity.length > 0.06) cfg.stillWindowSeconds * 1.6 else cfg.stillWindowSeconds
            if ((tNs - quietSinceNs) / 1e9 >= required) endSegment()
        } else {
            quietSinceNs = 0L
        }
    }

    private fun refineAtRest(f: Vec3, dt: Double) {
        // Track slow changes (thermal bias drift, tiny attitude error) while resting.
        val alpha = (dt / 1.5).coerceIn(0.0, 0.05)
        restForce = restForce.lerp(orientation.rotate(f), alpha)
        if (hasGyroscope && gyroWindow.mean() < gyroThreshold * 0.7) {
            gyroBias = gyroBias.lerp(lastGyro, alpha * 0.5)
        }
        up = restForce.normalized()
    }

    private fun pushPreRoll(tNs: Long, lin: Vec3) {
        preRoll.addLast(Sample(tNs, lin))
        val cutoff = tNs - (cfg.preRollSeconds * 1e9).toLong()
        while (preRoll.isNotEmpty() && preRoll.first().t < cutoff) preRoll.removeFirst()
    }

    private fun beginSegment() {
        phase = Phase.MOVING
        segN = 0
        segVel = Vec3.ZERO
        segPos = Vec3.ZERO
        quietSinceNs = 0L
        motionDetectedNs = preRoll.lastOrNull()?.t ?: 0L
        // Seed with the pre-roll; its first sample is the still anchor with zero velocity.
        for (s in preRoll) appendSegment(s.t, s.a)
        preRoll.clear()
        stillSeconds = 0.0
        listener?.invoke(Event.MotionStarted)
    }

    private fun appendSegment(t: Long, a: Vec3) {
        if (segN == segT.size) {
            val n = segT.size * 2
            segT = segT.copyOf(n)
            segA = Array(3) { segA[it].copyOf(n) }
        }
        if (segN > 0) {
            val dt = (t - segT[segN - 1]) / 1e9
            if (dt <= 0) return
            val prev = Vec3(segA[0][segN - 1], segA[1][segN - 1], segA[2][segN - 1])
            val newVel = segVel + (prev + a) * (0.5 * dt)
            segPos += (segVel + newVel) * (0.5 * dt)
            segVel = newVel
        }
        segT[segN] = t
        segA[0][segN] = a.x
        segA[1][segN] = a.y
        segA[2][segN] = a.z
        segN++
        liveVelocity = segVel
        livePosition = position + segPos * cfg.scale
        if (segN > 1 && (segT[segN - 1] - segT[0]) / 1e9 > cfg.maxSegmentSeconds) {
            // Force a cut; accuracy will be poor but state stays bounded.
            endSegment()
        }
    }

    private fun endSegment() {
        val n = segN
        if (n < 3) {
            phase = Phase.STILL
            segN = 0
            return
        }
        val t0 = segT[0]
        val total = (segT[n - 1] - t0) / 1e9
        // Pass 1: raw velocity.
        val vx = DoubleArray(n)
        val vy = DoubleArray(n)
        val vz = DoubleArray(n)
        for (i in 1 until n) {
            val dt = (segT[i] - segT[i - 1]) / 1e9
            vx[i] = vx[i - 1] + 0.5 * (segA[0][i - 1] + segA[0][i]) * dt
            vy[i] = vy[i - 1] + 0.5 * (segA[1][i - 1] + segA[1][i]) * dt
            vz[i] = vz[i - 1] + 0.5 * (segA[2][i - 1] + segA[2][i]) * dt
        }
        // Pass 2: remove linear velocity drift so that v(end) = 0, then integrate position.
        val ex = vx[n - 1]
        val ey = vy[n - 1]
        val ez = vz[n - 1]
        var px = 0.0
        var py = 0.0
        var pz = 0.0
        var peak = 0.0
        var prevCx = 0.0
        var prevCy = 0.0
        var prevCz = 0.0
        for (i in 1 until n) {
            val f = ((segT[i] - t0) / 1e9) / total
            val cx = vx[i] - ex * f
            val cy = vy[i] - ey * f
            val cz = vz[i] - ez * f
            val dt = (segT[i] - segT[i - 1]) / 1e9
            px += 0.5 * (prevCx + cx) * dt
            py += 0.5 * (prevCy + cy) * dt
            pz += 0.5 * (prevCz + cz) * dt
            prevCx = cx; prevCy = cy; prevCz = cz
            peak = max(peak, sqrt(cx * cx + cy * cy + cz * cz))
        }
        val d = Vec3(px, py, pz) * cfg.scale
        val len = d.length
        val segSigma = 0.002 + 0.02 * len + 0.002 * total * total
        val seg = Segment(d, total, peak, segSigma, t0, segT[n - 1])
        position += d
        livePosition = position
        liveVelocity = Vec3.ZERO
        sigma = sqrt(sigma * sigma + segSigma * segSigma)
        segmentCount++
        lastSegment = seg
        segN = 0
        quietSinceNs = 0L
        phase = Phase.STILL
        stillSeconds = cfg.stillWindowSeconds
        listener?.invoke(Event.MotionStopped(seg))
    }
}

/** Sliding time window over 3-axis samples; reports the combined per-axis standard deviation. */
internal class TimedVecWindow(private val cap: Int = 2048) {
    private val t = LongArray(cap)
    private val x = DoubleArray(cap)
    private val y = DoubleArray(cap)
    private val z = DoubleArray(cap)
    private var head = 0
    private var count = 0

    fun add(ts: Long, v: Vec3, windowSeconds: Double) {
        if (count == cap) {
            head = (head + 1) % cap
            count--
        }
        val idx = (head + count) % cap
        t[idx] = ts
        x[idx] = v.x
        y[idx] = v.y
        z[idx] = v.z
        count++
        val cutoff = ts - (windowSeconds * 1e9).toLong()
        while (count > 0 && t[head] < cutoff) {
            head = (head + 1) % cap
            count--
        }
    }

    fun size() = count

    fun spanSeconds(): Double = if (count < 2) 0.0 else (t[(head + count - 1) % cap] - t[head]) / 1e9

    fun mean(): Vec3 {
        if (count == 0) return Vec3.ZERO
        var mx = 0.0
        var my = 0.0
        var mz = 0.0
        for (i in 0 until count) {
            val k = (head + i) % cap
            mx += x[k]; my += y[k]; mz += z[k]
        }
        return Vec3(mx / count, my / count, mz / count)
    }

    fun std(): Double {
        if (count < 2) return 0.0
        var mx = 0.0
        var my = 0.0
        var mz = 0.0
        for (i in 0 until count) {
            val k = (head + i) % cap
            mx += x[k]; my += y[k]; mz += z[k]
        }
        mx /= count; my /= count; mz /= count
        var s = 0.0
        for (i in 0 until count) {
            val k = (head + i) % cap
            val dx = x[k] - mx
            val dy = y[k] - my
            val dz = z[k] - mz
            s += dx * dx + dy * dy + dz * dz
        }
        return sqrt(s / (count - 1))
    }
}

internal class TimedScalarWindow(private val cap: Int = 2048) {
    private val t = LongArray(cap)
    private val v = DoubleArray(cap)
    private var head = 0
    private var count = 0

    fun add(ts: Long, value: Double, windowSeconds: Double) {
        if (count == cap) {
            head = (head + 1) % cap
            count--
        }
        val idx = (head + count) % cap
        t[idx] = ts
        v[idx] = value
        count++
        val cutoff = ts - (windowSeconds * 1e9).toLong()
        while (count > 0 && t[head] < cutoff) {
            head = (head + 1) % cap
            count--
        }
    }

    fun size() = count

    fun latest(): Double = if (count == 0) 0.0 else v[(head + count - 1) % cap]

    fun mean(): Double {
        if (count == 0) return 0.0
        var s = 0.0
        for (i in 0 until count) s += v[(head + i) % cap]
        return s / count
    }

    fun std(): Double {
        if (count < 2) return 0.0
        val m = mean()
        var s = 0.0
        for (i in 0 until count) {
            val d = v[(head + i) % cap] - m
            s += d * d
        }
        return sqrt(s / (count - 1))
    }
}
