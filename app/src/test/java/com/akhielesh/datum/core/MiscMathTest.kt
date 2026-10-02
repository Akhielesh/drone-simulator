package com.akhielesh.datum.core

import com.akhielesh.datum.ar.ArEngine
import com.akhielesh.datum.core.math.Quat
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.core.math.magnitudeSpectrum
import com.akhielesh.datum.core.sensors.AWeighting48k
import com.akhielesh.datum.core.sensors.LevelCalibration
import com.akhielesh.datum.core.sensors.LevelMath
import com.akhielesh.datum.core.sensors.SlopeUnit
import com.akhielesh.datum.core.sensors.UpVector
import com.akhielesh.datum.core.sensors.mercalli
import com.akhielesh.datum.core.sensors.rms
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class MiscMathTest {

    private fun up(x: Double, y: Double, z: Double): UpVector {
        val n = sqrt(x * x + y * y + z * z)
        return UpVector(x / n, y / n, z / n, 0, 9.81)
    }

    @Test
    fun levelAnglesSurfaceAndEdge() {
        val flat = LevelMath.angles(up(sin(Math.toRadians(2.0)), 0.0, cos(Math.toRadians(2.0))), LevelCalibration())
        assertEquals(2.0, flat.rollDeg, 1e-6)
        assertEquals(0.0, flat.pitchDeg, 1e-6)
        assertEquals(2.0, flat.tiltDeg, 1e-6)
        val upright = LevelMath.angles(up(sin(Math.toRadians(5.0)), cos(Math.toRadians(5.0)), 0.0), LevelCalibration())
        assertEquals(0, upright.quadrant)
        assertEquals(5.0, upright.edgeDeg, 1e-6)
        val landscape = LevelMath.angles(up(1.0, 0.02, 0.0), LevelCalibration())
        assertEquals(1, landscape.quadrant)
        assertEquals(Math.toDegrees(kotlin.math.atan2(1.0, 0.02)) - 90, landscape.edgeDeg, 1e-6)
    }

    @Test
    fun twoPointCalibrationCancelsSurfaceSlope() {
        // Surface slope s and sensor bias b; rotating the phone 180° flips s but not b.
        val sx = 0.012
        val sy = -0.021
        val bx = 0.004
        val by = -0.003
        val r1 = up(sx + bx, sy + by, 1.0)
        val r2 = up(-sx + bx, -sy + by, 1.0)
        val (cx, cy) = LevelMath.surfaceBias(r1, r2)
        assertEquals(bx, cx, 2e-4)
        assertEquals(by, cy, 2e-4)
    }

    @Test
    fun slopeUnits() {
        assertEquals("100.0%", SlopeUnit.PERCENT.format(45.0))
        assertEquals("12.0/12", SlopeUnit.PITCH.format(45.0))
        assertEquals("1:1", SlopeUnit.RATIO.format(45.0))
    }

    @Test
    fun lengthFormatting() {
        assertEquals("23.5 cm", Fmt.length(0.2346, UnitSystem.METRIC).toString())
        assertEquals("8.5 mm", Fmt.length(0.0085, UnitSystem.METRIC).toString())
        assertEquals("1.25 m", Fmt.length(1.2502, UnitSystem.METRIC).toString())
        assertEquals("3⅜ in", Fmt.length(0.0254 * 3.375, UnitSystem.IMPERIAL).toString())
        assertEquals("1′ 4½″", Fmt.length(0.0254 * 16.5, UnitSystem.IMPERIAL).toString())
        assertEquals("5¹⁄₁₆", Fmt.fractionInches(5.0625))
        assertEquals("12", Fmt.fractionInches(11.999))
        assertEquals("1.00 L", Fmt.volume(0.001, UnitSystem.METRIC).toString())
        assertEquals("1.00 ft²", Fmt.area(0.3048 * 0.3048, UnitSystem.IMPERIAL).toString())
    }

    private fun gainDb(freq: Double): Double {
        val f = AWeighting48k()
        val n = 48_000
        val input = DoubleArray(n) { sin(2 * PI * freq * it / 48_000.0) }
        val out = DoubleArray(n) { f.process(input[it]) }
        // Skip the filter's settling time.
        return 20 * log10(rms(out.copyOfRange(n / 2, n)) / rms(input.copyOfRange(n / 2, n)))
    }

    @Test
    fun aWeightingMatchesIecCurve() {
        assertEquals(0.0, gainDb(1000.0), 0.3)
        assertEquals(-19.1, gainDb(100.0), 0.6)
        assertEquals(1.0, gainDb(4000.0), 1.0)
        // The bilinear transform warps the top octave; IEC 61672 Class 1 allows +2.0/−3.0 dB at 10 kHz.
        val g10k = gainDb(10_000.0)
        assertTrue("10 kHz gain $g10k", g10k in (-2.5 - 3.0)..(-2.5 + 2.0))
    }

    @Test
    fun fftFindsDominantFrequency() {
        val rate = 400.0
        val samples = DoubleArray(1024) { sin(2 * PI * 37.0 * it / rate) + 0.2 * sin(2 * PI * 90.0 * it / rate) }
        val spec = magnitudeSpectrum(samples)
        val k = spec.indices.maxBy { spec[it] }
        assertEquals(37.0, k * rate / 1024, 0.5)
    }

    @Test
    fun mercalliScale() {
        assertEquals("I", mercalli(0.001).first)
        assertEquals("V", mercalli(0.05).first)
        assertEquals("X+", mercalli(2.0).first)
    }

    @Test
    fun arVerticalHeightFromCenterRay() {
        // Camera at 1.5 m aiming at the top of a 2.5 m pole two metres away.
        val base = Vec3(0.0, 0.0, -2.0)
        val origin = Vec3(0.0, 1.5, 0.0)
        val dir = Vec3(0.0, 1.0, -2.0).normalized()
        assertEquals(2.5, ArEngine.verticalHeight(base, origin, dir)!!, 1e-9)
        // Looking away from the pole: behind the camera.
        assertNull(ArEngine.verticalHeight(base, origin, Vec3(0.0, 0.0, 1.0)))
    }

    @Test
    fun polygonAreaOnTiltedPlane() {
        val q = Quat.fromAxisAngle(Vec3(1.0, 0.4, 0.0), 0.6)
        val square = listOf(Vec3(0.0, 0.0, 0.0), Vec3(2.0, 0.0, 0.0), Vec3(2.0, 0.0, 1.5), Vec3(0.0, 0.0, 1.5)).map { q.rotate(it) }
        assertEquals(3.0, ArEngine.polygonArea(square), 1e-9)
    }

    @Test
    fun quaternionIntegration() {
        var q = Quat.IDENTITY
        repeat(1000) { q = q.integrate(Vec3(0.0, 0.0, 1.0), 0.001) }
        val x = q.rotate(Vec3.X)
        assertEquals(cos(1.0), x.x, 1e-6)
        assertEquals(sin(1.0), x.y, 1e-6)
        assertEquals(1.0, q.angle, 1e-6)
        val r = Quat.fromTwoVectors(Vec3.X, Vec3.Y).rotate(Vec3.X)
        assertEquals(1.0, r.y, 1e-9)
    }
}
