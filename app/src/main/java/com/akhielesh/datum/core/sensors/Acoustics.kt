package com.akhielesh.datum.core.sensors

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * IEC 61672 A-weighting as a 6th-order IIR at 48 kHz (bilinear-transformed analogue prototype,
 * the widely used coefficients from Couvreur's `adsgn`). Direct form II transposed.
 */
class AWeighting48k {
    private val b = doubleArrayOf(
        0.234301792299513, -0.468603584599026, -0.234301792299513, 0.937207168598053,
        -0.234301792299513, -0.468603584599026, 0.234301792299513,
    )
    private val a = doubleArrayOf(
        1.0, -4.113043408775871, 6.553121752655047, -4.990849294163381,
        1.785737302937573, -0.246190595319487, 0.011224250033231,
    )
    private val z = DoubleArray(6)

    fun process(x: Double): Double {
        val y = b[0] * x + z[0]
        z[0] = b[1] * x - a[1] * y + z[1]
        z[1] = b[2] * x - a[2] * y + z[2]
        z[2] = b[3] * x - a[3] * y + z[3]
        z[3] = b[4] * x - a[4] * y + z[4]
        z[4] = b[5] * x - a[5] * y + z[5]
        z[5] = b[6] * x - a[6] * y
        return y
    }

    fun reset() = z.fill(0.0)
}

data class SoundSnapshot(
    val levelDb: Double = 0.0,
    val peakDb: Double = 0.0,
    val leqDb: Double = 0.0,
    val maxDb: Double = 0.0,
    val history: List<Float> = emptyList(),
    val running: Boolean = false,
    val error: String? = null,
)

/**
 * Sound level meter. Phone microphones are not lab instruments, so the absolute level depends on a
 * per-model constant (dB SPL at 0 dBFS) that the user can fine-tune against a reference meter.
 */
class SoundMeter(private val context: Context, private val userOffsetDb: () -> Double) {
    private val _state = MutableStateFlow(SoundSnapshot())
    val state: StateFlow<SoundSnapshot> = _state.asStateFlow()
    private var job: Job? = null

    @SuppressLint("MissingPermission") // The UI only starts the meter after RECORD_AUDIO is granted.
    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch(Dispatchers.Default) {
            val rate = 48_000
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val am = context.getSystemService(AudioManager::class.java)
            // Prefer the raw, unprocessed path (no AGC / noise suppression) when the phone offers it.
            val unprocessed = am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
            val rec = runCatching {
                AudioRecord(source, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, 16_384))
            }.getOrNull()
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                _state.value = SoundSnapshot(error = "Microphone unavailable")
                rec?.release()
                return@launch
            }
            val filter = AWeighting48k()
            val buf = ShortArray(2048)
            val blockSeconds = buf.size.toDouble() / rate
            val alpha = 1 - exp(-blockSeconds / 0.125) // "Fast" time weighting
            var energy = -1.0
            var leqEnergySum = 0.0
            var leqCount = 0
            var maxDb = 0.0
            val history = ArrayDeque<Float>()
            var sinceHistory = 0.0
            runCatching { rec.startRecording() }
            try {
                while (isActive) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    var peak = 0.0
                    for (i in 0 until n) {
                        val x = buf[i] / 32768.0
                        val y = filter.process(x)
                        sum += y * y
                        peak = max(peak, abs(x))
                    }
                    val ms = sum / n
                    energy = if (energy < 0) ms else energy + (ms - energy) * alpha
                    val offset = SPL_AT_FULL_SCALE + userOffsetDb()
                    val level = 10 * log10(max(energy, 1e-12)) + offset
                    val peakDb = 20 * log10(max(peak, 1e-9)) + offset
                    leqEnergySum += 10.0.pow(level / 10)
                    leqCount++
                    maxDb = max(maxDb, level)
                    sinceHistory += blockSeconds
                    if (sinceHistory >= 0.1) {
                        sinceHistory = 0.0
                        history.addLast(level.toFloat())
                        while (history.size > 240) history.removeFirst()
                    }
                    _state.value = SoundSnapshot(
                        levelDb = level.coerceAtLeast(0.0),
                        peakDb = peakDb.coerceAtLeast(0.0),
                        leqDb = 10 * log10(leqEnergySum / leqCount),
                        maxDb = maxDb,
                        history = history.toList(),
                        running = true,
                    )
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.value = _state.value.copy(running = false)
    }

    companion object {
        /** Typical MEMS phone microphone: about 120 dB SPL drives the ADC to full scale. */
        const val SPL_AT_FULL_SCALE = 120.0
    }
}

/** RMS-level helper used by tests. */
fun rms(samples: DoubleArray): Double = sqrt(samples.sumOf { it * it } / samples.size)
