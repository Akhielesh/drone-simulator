package com.akhielesh.datum.core.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** A copied sensor event (Android recycles [SensorEvent] instances, so never keep those). */
class Reading(
    val type: Int,
    val values: FloatArray,
    val timestampNs: Long,
    val accuracy: Int,
) {
    val x: Float get() = values[0]
    val y: Float get() = if (values.size > 1) values[1] else 0f
    val z: Float get() = if (values.size > 2) values[2] else 0f
}

/** Which measuring hardware this phone actually has. Tools adapt or hide accordingly. */
data class SensorAvailability(
    val accelerometer: Boolean,
    val gyroscope: Boolean,
    val magnetometer: Boolean,
    val barometer: Boolean,
    val light: Boolean,
    val proximity: Boolean,
    val gravity: Boolean,
    val rotationVector: Boolean,
    val gameRotationVector: Boolean,
    val stepCounter: Boolean,
    val hingeAngle: Boolean,
    val camera: Boolean,
    val microphone: Boolean,
    val uwb: Boolean,
    val nfc: Boolean,
)

data class SensorInfo(
    val type: Int,
    val typeName: String,
    val name: String,
    val vendor: String,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val maxRateHz: Float,
    val isWakeUp: Boolean,
    val unit: String,
)

/**
 * Central access point to the phone's sensors. High-rate streams are delivered on a dedicated
 * urgent-priority thread so the UI thread never stalls integration.
 */
class SensorHub(private val context: Context) {
    val manager: SensorManager = context.getSystemService(SensorManager::class.java)

    private val sensorThread: HandlerThread by lazy {
        HandlerThread("datum-sensors", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
    }

    /** Handler on the sensor thread; post work here to touch engine state safely. */
    val handler: Handler by lazy { Handler(sensorThread.looper) }

    fun sensor(type: Int): Sensor? = manager.getDefaultSensor(type)

    fun has(type: Int): Boolean = manager.getDefaultSensor(type) != null

    val availability: SensorAvailability by lazy {
        val pm = context.packageManager
        SensorAvailability(
            accelerometer = has(Sensor.TYPE_ACCELEROMETER),
            gyroscope = has(Sensor.TYPE_GYROSCOPE),
            magnetometer = has(Sensor.TYPE_MAGNETIC_FIELD),
            barometer = has(Sensor.TYPE_PRESSURE),
            light = has(Sensor.TYPE_LIGHT),
            proximity = has(Sensor.TYPE_PROXIMITY),
            gravity = has(Sensor.TYPE_GRAVITY),
            rotationVector = has(Sensor.TYPE_ROTATION_VECTOR),
            gameRotationVector = has(Sensor.TYPE_GAME_ROTATION_VECTOR),
            stepCounter = has(Sensor.TYPE_STEP_COUNTER),
            hingeAngle = android.os.Build.VERSION.SDK_INT >= 30 && has(Sensor.TYPE_HINGE_ANGLE),
            camera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            microphone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE),
            uwb = android.os.Build.VERSION.SDK_INT >= 31 && pm.hasSystemFeature(PackageManager.FEATURE_UWB),
            nfc = pm.hasSystemFeature(PackageManager.FEATURE_NFC),
        )
    }

    /**
     * Cold, conflated stream of one sensor at roughly [periodUs]. Registration lives exactly as long
     * as the collector, so `collectAsStateWithLifecycle` automatically pauses sensors in background.
     */
    fun readings(type: Int, periodUs: Int = SensorManager.SENSOR_DELAY_UI): Flow<Reading> = callbackFlow {
        val sensor = manager.getDefaultSensor(type)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(Reading(event.sensor.type, event.values.copyOf(), event.timestamp, event.accuracy))
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, periodUs, handler)
        awaitClose { manager.unregisterListener(listener) }
    }.conflate()

    /**
     * Registers a raw listener on the sensor thread. Use for streams where every sample matters
     * (integration, FFT). Returns an unregister function.
     */
    fun listen(types: IntArray, periodUs: Int, onEvent: (SensorEvent) -> Unit): () -> Unit {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) = onEvent(event)
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        var any = false
        for (t in types) {
            val s = manager.getDefaultSensor(t) ?: continue
            manager.registerListener(listener, s, periodUs, 0, handler)
            any = true
        }
        return if (any) ({ manager.unregisterListener(listener) }) else ({})
    }

    fun allSensors(): List<SensorInfo> = manager.getSensorList(Sensor.TYPE_ALL)
        .sortedWith(compareBy({ typeOrder(it.type) }, { it.name }))
        .map { s ->
            SensorInfo(
                type = s.type,
                typeName = prettyType(s),
                name = s.name,
                vendor = s.vendor,
                version = s.version,
                maxRange = s.maximumRange,
                resolution = s.resolution,
                powerMa = s.power,
                maxRateHz = if (s.minDelay > 0) 1_000_000f / s.minDelay else 0f,
                isWakeUp = s.isWakeUpSensor,
                unit = unitFor(s.type),
            )
        }

    companion object {
        fun prettyType(s: Sensor): String = when (s.type) {
            Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
            Sensor.TYPE_ACCELEROMETER_UNCALIBRATED -> "Accelerometer (raw)"
            Sensor.TYPE_GYROSCOPE -> "Gyroscope"
            Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "Gyroscope (raw)"
            Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
            Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "Magnetometer (raw)"
            Sensor.TYPE_PRESSURE -> "Barometer"
            Sensor.TYPE_LIGHT -> "Ambient light"
            Sensor.TYPE_PROXIMITY -> "Proximity"
            Sensor.TYPE_GRAVITY -> "Gravity (fused)"
            Sensor.TYPE_LINEAR_ACCELERATION -> "Linear acceleration (fused)"
            Sensor.TYPE_ROTATION_VECTOR -> "Rotation vector (fused)"
            Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game rotation vector"
            Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic rotation"
            Sensor.TYPE_STEP_COUNTER -> "Step counter"
            Sensor.TYPE_STEP_DETECTOR -> "Step detector"
            Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant motion"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "Humidity"
            Sensor.TYPE_HEART_RATE -> "Heart rate"
            else -> if (android.os.Build.VERSION.SDK_INT >= 30 && s.type == Sensor.TYPE_HINGE_ANGLE) {
                "Hinge angle"
            } else {
                s.stringType.substringAfterLast('.').replace('_', ' ').replaceFirstChar { it.uppercase() }
            }
        }

        private fun typeOrder(type: Int) = when (type) {
            Sensor.TYPE_ACCELEROMETER -> 0
            Sensor.TYPE_GYROSCOPE -> 1
            Sensor.TYPE_MAGNETIC_FIELD -> 2
            Sensor.TYPE_PRESSURE -> 3
            Sensor.TYPE_LIGHT -> 4
            Sensor.TYPE_PROXIMITY -> 5
            Sensor.TYPE_GRAVITY -> 6
            Sensor.TYPE_ROTATION_VECTOR -> 7
            else -> 100 + type
        }

        fun unitFor(type: Int): String = when (type) {
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_ACCELEROMETER_UNCALIBRATED,
            Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION -> "m/s²"
            Sensor.TYPE_GYROSCOPE, Sensor.TYPE_GYROSCOPE_UNCALIBRATED -> "rad/s"
            Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED -> "µT"
            Sensor.TYPE_PRESSURE -> "hPa"
            Sensor.TYPE_LIGHT -> "lx"
            Sensor.TYPE_PROXIMITY -> "cm"
            Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
            Sensor.TYPE_RELATIVE_HUMIDITY -> "%"
            Sensor.TYPE_STEP_COUNTER -> "steps"
            else -> ""
        }
    }
}
