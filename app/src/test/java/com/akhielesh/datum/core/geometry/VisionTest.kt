package com.akhielesh.datum.core.geometry

import com.akhielesh.datum.core.math.Quat
import com.akhielesh.datum.core.math.Vec2
import com.akhielesh.datum.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Synthetic pinhole camera looking at a tilted plane (a box face) with a bank card on it.
 * Validates the photo-calibration geometry against ground truth.
 */
class VisionTest {
    private val f = 3000.0
    private val cx = 2000.0
    private val cy = 1500.0

    /** Plane in camera coordinates (x right, y down, z forward). */
    private class Plane(val origin: Vec3, yawDeg: Double, pitchDeg: Double) {
        private val q = Quat.fromAxisAngle(Vec3.Y, Math.toRadians(yawDeg)) * Quat.fromAxisAngle(Vec3.X, Math.toRadians(pitchDeg))
        val ex: Vec3 = q.rotate(Vec3.X)
        val ey: Vec3 = q.rotate(Vec3.Y)
        fun at(s: Double, t: Double) = origin + ex * s + ey * t
    }

    private fun project(p: Vec3) = Vec2(cx + f * p.x / p.z, cy + f * p.y / p.z)

    private fun quadOn(plane: Plane, s: Double, t: Double, w: Double, h: Double) = Quad(
        project(plane.at(s, t)), project(plane.at(s + w, t)),
        project(plane.at(s + w, t + h)), project(plane.at(s, t + h)),
    )

    @Test
    fun referenceCardRecoversFaceSizeUnderStrongPerspective() {
        val plane = Plane(Vec3(-0.2, -0.12, 0.9), yawDeg = 32.0, pitchDeg = -18.0)
        val face = quadOn(plane, 0.0, 0.0, 0.42, 0.25)
        val card = quadOn(plane, 0.06, 0.15, 0.0856, 0.05398)
        val m = measureWithReference(card, 0.0856, 0.05398, face)
        assertNotNull(m)
        assertEquals(0.42, m!!.width, 0.42 * 0.003)
        assertEquals(0.25, m.height, 0.25 * 0.003)
        assertTrue(m.relativeSigma in 0.005..0.08)
    }

    @Test
    fun aspectRatioWithoutReferenceUsesPerspectiveToRecoverFocalLength() {
        val plane = Plane(Vec3(-0.25, -0.15, 1.0), yawDeg = 35.0, pitchDeg = -20.0)
        val face = quadOn(plane, 0.0, 0.0, 0.50, 0.30)
        // Deliberately wrong fallback focal length: the quad itself should reveal the true one.
        val a = rectangleAspect(face, Vec2(cx, cy), fallbackFocalPx = 2200.0)
        assertNotNull(a)
        assertTrue("focal should come from the image", a!!.focalFromImage)
        assertEquals(3000.0, a.focalPx, 30.0)
        assertEquals(0.50 / 0.30, a.ratio, (0.50 / 0.30) * 0.005)
    }

    @Test
    fun aspectRatioFrontoParallelFallsBackGracefully() {
        val plane = Plane(Vec3(-0.2, -0.1, 1.0), yawDeg = 0.0, pitchDeg = 0.0)
        val face = quadOn(plane, 0.0, 0.0, 0.40, 0.20)
        val a = rectangleAspect(face, Vec2(cx, cy), fallbackFocalPx = 3000.0)
        assertNotNull(a)
        assertEquals(2.0, a!!.ratio, 0.01)
    }

    @Test
    fun homographyMapsReferenceCornersExactly() {
        val src = listOf(Vec2(10.0, 20.0), Vec2(400.0, 35.0), Vec2(380.0, 300.0), Vec2(5.0, 260.0))
        val dst = listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(1.0, 1.0), Vec2(0.0, 1.0))
        val h = Homography.fromQuad(src, dst)!!
        src.zip(dst).forEach { (s, d) ->
            val m = h.map(s)
            assertEquals(d.x, m.x, 1e-9)
            assertEquals(d.y, m.y, 1e-9)
        }
        val inv = h.inverse()!!
        assertEquals(10.0, inv.map(Vec2(0.0, 0.0)).x, 1e-6)
    }

    @Test
    fun cornerSnapperFindsNearbyCorner() {
        // A bright square on a dark background; its top-left corner is at (40, 30).
        val w = 120
        val h = 100
        val gray = FloatArray(w * h) { i ->
            val x = i % w
            val y = i / w
            if (x >= 40 && y >= 30 && x < 90 && y < 80) 1f else 0f
        }
        val snapped = CornerSnapper(gray, w, h).snap(Vec2(46.0, 35.0), 10)
        assertNotNull(snapped)
        assertEquals(40.0, snapped!!.x, 2.0)
        assertEquals(30.0, snapped.y, 2.0)
    }
}
