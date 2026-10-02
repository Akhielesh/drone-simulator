package com.akhielesh.datum.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.math.Vec2
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.core.sensors.MotionSession
import com.akhielesh.datum.core.sensors.MotionTracker
import kotlin.math.atan2

/**
 * A [MotionSession] bound to the screen's lifecycle and the user's distance calibration, with the
 * app's standard feedback for calibration done / motion stopped.
 */
@Composable
fun rememberMotionSession(handheld: Boolean = false): MotionSession {
    val app = LocalApp.current
    val scale = LocalSettings.current.motionScale
    val session = remember(scale, handheld) {
        val base = if (handheld) MotionTracker.Config.HANDHELD else MotionTracker.Config.SURFACE
        MotionSession(app.sensors, base.copy(scale = scale))
    }
    EngineLifecycle(session, start = session::start, stop = session::stop)
    LaunchedEffect(session) {
        session.events.collect { e ->
            when (e) {
                MotionTracker.Event.Calibrated -> app.haptics.lowTick()
                is MotionTracker.Event.MotionStopped -> app.haptics.lowTick()
                MotionTracker.Event.MotionStarted -> Unit
            }
        }
    }
    return session
}

/** In-plane basis for a surface whose normal is [up]: e1 ≈ device X, e2 ≈ device Y at rest. */
class PlaneBasis(up: Vec3) {
    val e1: Vec3 = Vec3.X.rejectFrom(up.normalized()).normalized().let { if (it.length < 0.5) Vec3.Y.rejectFrom(up).normalized() else it }
    val e2: Vec3 = up.normalized() cross e1

    fun project(p: Vec3) = Vec2(p dot e1, p dot e2)

    fun yawOf(deviceX: Vec3): Double = atan2(deviceX dot e2, deviceX dot e1)
}
