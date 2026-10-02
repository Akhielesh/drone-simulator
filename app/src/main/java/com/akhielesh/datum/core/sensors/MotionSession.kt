package com.akhielesh.datum.core.sensors

import android.hardware.Sensor
import android.os.SystemClock
import com.akhielesh.datum.core.math.Quat
import com.akhielesh.datum.core.math.Vec3
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** Immutable snapshot of a [MotionTracker], published to the UI at display rate. */
data class MotionSnapshot(
    val phase: MotionTracker.Phase = MotionTracker.Phase.CALIBRATING,
    val position: Vec3 = Vec3.ZERO,
    val livePosition: Vec3 = Vec3.ZERO,
    val liveVelocity: Vec3 = Vec3.ZERO,
    val up: Vec3 = Vec3.Z,
    val orientation: Quat = Quat.IDENTITY,
    val calibrationProgress: Double = 0.0,
    val stillSeconds: Double = 0.0,
    val sigma: Double = 0.0,
    val segmentCount: Int = 0,
    val lastSegment: MotionTracker.Segment? = null,
    val sampleRateHz: Double = 0.0,
    val running: Boolean = false,
)

/**
 * Runs a [MotionTracker] on the sensor thread at the IMU's full rate and publishes snapshots.
 * Start it when a measuring screen becomes visible and stop it when it leaves.
 */
class MotionSession(
    private val hub: SensorHub,
    config: MotionTracker.Config,
) {
    private val tracker = MotionTracker(config)
    private val _state = MutableStateFlow(MotionSnapshot())
    val state: StateFlow<MotionSnapshot> = _state.asStateFlow()
    private val _events = MutableSharedFlow<MotionTracker.Event>(extraBufferCapacity = 64)
    val events: SharedFlow<MotionTracker.Event> = _events.asSharedFlow()

    private var unregister: (() -> Unit)? = null
    private var lastPublishNs = 0L

    val hasGyroscope: Boolean = hub.has(Sensor.TYPE_GYROSCOPE)

    init {
        tracker.hasGyroscope = hasGyroscope
        tracker.listener = { e ->
            _events.tryEmit(e)
            publish()
        }
    }

    fun start() {
        if (unregister != null) return
        // ~400 Hz request; Android caps to the hardware maximum (and to 200 Hz without the
        // HIGH_SAMPLING_RATE_SENSORS permission, which the manifest declares).
        unregister = hub.listen(intArrayOf(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE), 2_500) { e ->
            when (e.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    tracker.onAccel(e.timestamp, e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
                    if (e.timestamp - lastPublishNs > 15_000_000L) {
                        lastPublishNs = e.timestamp
                        publish()
                    }
                }
                Sensor.TYPE_GYROSCOPE ->
                    tracker.onGyro(e.timestamp, e.values[0].toDouble(), e.values[1].toDouble(), e.values[2].toDouble())
            }
        }
        _state.value = _state.value.copy(running = true)
    }

    fun stop() {
        unregister?.invoke()
        unregister = null
        hub.handler.post {
            tracker.reset(keepCalibration = false)
            _state.value = snapshot().copy(running = false)
        }
    }

    /** Zero the distance; keeps calibration when the phone hasn't been disturbed. */
    fun reset(keepCalibration: Boolean = true) = hub.handler.post {
        tracker.reset(keepCalibration)
        publish()
    }

    fun recalibrate() = reset(keepCalibration = false)

    private fun publish() {
        _state.value = snapshot()
    }

    private fun snapshot() = MotionSnapshot(
        phase = tracker.phase,
        position = tracker.position,
        livePosition = tracker.livePosition,
        liveVelocity = tracker.liveVelocity,
        up = tracker.up,
        orientation = tracker.orientation,
        calibrationProgress = tracker.calibrationProgress,
        stillSeconds = tracker.stillSeconds,
        sigma = tracker.sigma,
        segmentCount = tracker.segmentCount,
        lastSegment = tracker.lastSegment,
        sampleRateHz = tracker.sampleRateHz,
        running = unregister != null,
    )

    companion object {
        fun nowNs() = SystemClock.elapsedRealtimeNanos()
    }
}
