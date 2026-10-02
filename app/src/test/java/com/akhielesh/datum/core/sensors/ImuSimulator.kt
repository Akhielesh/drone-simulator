package com.akhielesh.datum.core.sensors

import com.akhielesh.datum.core.math.Quat
import com.akhielesh.datum.core.math.Vec3
import java.util.Random

/**
 * Generates realistic accelerometer + gyroscope streams for a rigid phone following a scripted
 * trajectory: sensor bias, white noise, a tilted resting surface and optional rotation.
 */
class ImuSimulator(
    val rateHz: Double = 400.0,
    val accelBias: Vec3 = Vec3(0.03, -0.025, 0.04),
    val gyroBias: Vec3 = Vec3(0.002, -0.0015, 0.0025),
    val accelNoise: Double = 0.012,
    val gyroNoise: Double = 0.0012,
    /** Attitude of the phone (device → world) at rest. */
    var attitude: Quat = Quat.fromAxisAngle(Vec3.X, Math.toRadians(1.5)),
    seed: Long = 7,
) {
    private val rnd = Random(seed)
    private var tNs = 1_000_000_000L
    private val g = 9.80665

    /** World-frame position history isn't needed: callers supply acceleration directly. */
    fun still(tracker: MotionTracker, seconds: Double) = run(tracker, seconds) { Vec3.ZERO to Vec3.ZERO }

    /**
     * Minimum-jerk move of [displacementWorld] metres over [seconds], optionally rotating by
     * [rotationWorld] (rotation vector, rad) with the same smooth profile.
     */
    fun move(
        tracker: MotionTracker,
        displacementWorld: Vec3,
        seconds: Double,
        rotationBody: Vec3 = Vec3.ZERO,
    ) = run(tracker, seconds) { tau ->
        val accScale = (60 * tau - 180 * tau * tau + 120 * tau * tau * tau) / (seconds * seconds)
        val velScale = (30 * tau * tau - 60 * tau * tau * tau + 30 * tau * tau * tau * tau) / seconds
        displacementWorld * accScale to rotationBody * velScale
    }

    private fun run(tracker: MotionTracker, seconds: Double, profile: (Double) -> Pair<Vec3, Vec3>) {
        val n = (seconds * rateHz).toInt()
        val dt = 1.0 / rateHz
        for (i in 0 until n) {
            val tau = (i + 0.5) / n
            val (accWorld, omegaBody) = profile(tau)
            // Integrate attitude with the true body rate.
            attitude = attitude.integrate(omegaBody, dt)
            val specificWorld = accWorld + Vec3(0.0, 0.0, g)
            val fBody = attitude.conjugate().rotate(specificWorld) + accelBias + noise(accelNoise)
            val wBody = omegaBody + gyroBias + noise(gyroNoise)
            tNs += (dt * 1e9).toLong()
            tracker.onGyro(tNs, wBody.x, wBody.y, wBody.z)
            tracker.onAccel(tNs + 300_000, fBody.x, fBody.y, fBody.z)
        }
    }

    private fun noise(sigma: Double) = Vec3(rnd.nextGaussian() * sigma, rnd.nextGaussian() * sigma, rnd.nextGaussian() * sigma)
}
