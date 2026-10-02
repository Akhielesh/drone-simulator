package com.akhielesh.datum.core.sensors

import com.akhielesh.datum.core.math.Quat
import com.akhielesh.datum.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MotionTrackerTest {

    @Test
    fun calibratesThenMeasuresTwoSlidesOnTiltedTable() {
        val sim = ImuSimulator()
        val tracker = MotionTracker(MotionTracker.Config.SURFACE)
        val stops = mutableListOf<MotionTracker.Segment>()
        tracker.listener = { if (it is MotionTracker.Event.MotionStopped) stops += it.segment }

        sim.still(tracker, 1.2)
        assertEquals(MotionTracker.Phase.STILL, tracker.phase)

        // Slide 30 cm along the phone's long axis (world direction of device +Y).
        val dir = sim.attitude.rotate(Vec3.Y)
        sim.move(tracker, dir * 0.30, 1.2)
        sim.still(tracker, 0.7)
        sim.move(tracker, dir * 0.15, 0.8)
        sim.still(tracker, 0.7)

        assertEquals(2, stops.size)
        val first = stops[0].displacement.length
        val total = tracker.position.length
        println("first=%.4f total=%.4f sigma=%.4f".format(first, total, tracker.sigma))
        assertEquals(0.30, first, 0.012)
        assertEquals(0.45, total, 0.018)
        // Motion is along device Y in N0.
        assertTrue(abs(tracker.position.y) > 0.43)
    }

    @Test
    fun handheldCornerWalkWithRotationRecoversEdges() {
        // Phone held upright (screen facing user), walking three box edges with some wrist rotation.
        val sim = ImuSimulator(
            accelNoise = 0.03,
            gyroNoise = 0.003,
            attitude = Quat.fromAxisAngle(Vec3.X, Math.toRadians(80.0)),
            seed = 11,
        )
        val tracker = MotionTracker(MotionTracker.Config.HANDHELD)
        val stops = mutableListOf<MotionTracker.Segment>()
        tracker.listener = { if (it is MotionTracker.Event.MotionStopped) stops += it.segment }
        sim.still(tracker, 1.0)
        assertEquals(MotionTracker.Phase.STILL, tracker.phase)

        val edges = listOf(Vec3(0.42, 0.0, 0.0), Vec3(0.0, 0.31, 0.0), Vec3(0.0, 0.0, 0.25))
        val wrist = listOf(Vec3(0.0, 0.0, 0.25), Vec3(0.1, 0.0, -0.2), Vec3(-0.1, 0.15, 0.0))
        for (i in edges.indices) {
            sim.move(tracker, edges[i], 1.3, rotationBody = wrist[i])
            sim.still(tracker, 0.9)
        }
        assertEquals(3, stops.size)
        for (i in edges.indices) {
            val got = stops[i].displacement.length
            println("edge $i expected=%.3f got=%.4f".format(edges[i].length, got))
            assertEquals(edges[i].length, got, edges[i].length * 0.05 + 0.006)
        }
    }

    @Test
    fun ignoresTapsWhileResting() {
        val sim = ImuSimulator()
        val tracker = MotionTracker(MotionTracker.Config.SURFACE)
        sim.still(tracker, 1.0)
        // A tap: a 10 ms 1.5 m/s² jolt back and forth.
        sim.move(tracker, Vec3(0.0, 0.0, 0.00002), 0.02)
        sim.still(tracker, 0.6)
        assertTrue("tap moved the phone ${tracker.position.length}", tracker.position.length < 0.003)
    }
}
