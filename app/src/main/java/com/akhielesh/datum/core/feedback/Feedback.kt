package com.akhielesh.datum.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Tactile vocabulary of the app. On Galaxy S22+ (Android 12+) these use the haptic composition
 * primitives, which the S-series linear motor renders as crisp, distinct clicks and ticks.
 */
class Haptics(context: Context, private val enabled: () -> Boolean) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()?.takeIf { it.hasVibrator() }

    private val primitives: Boolean = Build.VERSION.SDK_INT >= 31 && vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK,
        VibrationEffect.Composition.PRIMITIVE_CLICK,
        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
    ) == true

    private var lastTickMs = 0L

    /** A ruler detent. Rate-limited so fast slides don't turn into a buzz. */
    fun tick(strength: Float = 0.5f) {
        val now = SystemClock.uptimeMillis()
        if (now - lastTickMs < 22) return
        lastTickMs = now
        play(
            primitive = { if (Build.VERSION.SDK_INT >= 31) it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, strength) },
            predefined = 2, // EFFECT_TICK
            oneShotMs = 8,
        )
    }

    /** A softer, lower detent for minor graduations. */
    fun lowTick() = play(
        primitive = { if (Build.VERSION.SDK_INT >= 31) it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.6f) },
        predefined = 2,
        oneShotMs = 6,
    )

    fun click() = play(
        primitive = { if (Build.VERSION.SDK_INT >= 31) it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.7f) },
        predefined = 0, // EFFECT_CLICK
        oneShotMs = 14,
    )

    /** A point was captured. */
    fun capture() = play(
        primitive = {
            if (Build.VERSION.SDK_INT >= 31) {
                it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
                it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 60)
            }
        },
        predefined = 5, // EFFECT_HEAVY_CLICK
        oneShotMs = 22,
    )

    /** Rising double tap: success / done. */
    fun success() = play(
        primitive = {
            if (Build.VERSION.SDK_INT >= 31) {
                it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.5f)
                it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f, 70)
            }
        },
        predefined = 1, // EFFECT_DOUBLE_CLICK
        oneShotMs = 30,
    )

    fun warning() = play(
        primitive = {
            if (Build.VERSION.SDK_INT >= 31) {
                it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f)
                it.addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 1f, 80)
            }
        },
        predefined = 1,
        oneShotMs = 40,
    )

    private fun play(primitive: (VibrationEffect.Composition) -> Unit, predefined: Int, oneShotMs: Long) {
        if (!enabled()) return
        val v = vibrator ?: return
        runCatching {
            when {
                primitives && Build.VERSION.SDK_INT >= 31 -> {
                    val c = VibrationEffect.startComposition()
                    primitive(c)
                    v.vibrate(c.compose())
                }
                Build.VERSION.SDK_INT >= 29 -> v.vibrate(VibrationEffect.createPredefined(predefined))
                else -> v.vibrate(VibrationEffect.createOneShot(oneShotMs, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    }
}

/**
 * Tiny synthesizer for UI sounds (no audio assets): soft "tink" on capture, a two-note chime on
 * success, and parking-sensor style beeps for the eyes-free level.
 */
class Tones(private val enabled: () -> Boolean) {
    private val rate = 44_100
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "datum-tones").apply { isDaemon = true } }
    private val cache = HashMap<String, AudioTrack>()

    fun tink() = play("tink") { tone(listOf(2637.0 to 0.045), volume = 0.32) }
    fun pop() = play("pop") { tone(listOf(880.0 to 0.05), volume = 0.30) }
    fun success() = play("success") { tone(listOf(1318.5 to 0.07, 1760.0 to 0.12), volume = 0.28) }
    fun error() = play("error") { tone(listOf(330.0 to 0.09, 262.0 to 0.12), volume = 0.30) }

    /** Short guidance beep; [force] ignores the global sound toggle (used when explicitly enabled). */
    fun beep(freq: Double, seconds: Double = 0.06, force: Boolean = false) {
        val key = "beep_${freq.toInt()}_${(seconds * 1000).toInt()}"
        play(key, force) { tone(listOf(freq to seconds), volume = 0.35) }
    }

    private fun play(key: String, force: Boolean = false, build: () -> ShortArray) {
        if (!force && !enabled()) return
        executor.execute {
            runCatching {
                val track = cache.getOrPut(key) { createTrack(build()) }
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
                track.reloadStaticData()
                track.play()
            }
        }
    }

    private fun createTrack(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    /** Sequence of sine notes, each with a fast attack and exponential decay (bell-like). */
    private fun tone(notes: List<Pair<Double, Double>>, volume: Double): ShortArray {
        val total = notes.sumOf { (it.second * rate).toInt() }
        val out = ShortArray(total)
        var offset = 0
        for ((freq, dur) in notes) {
            val n = (dur * rate).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val attack = min(1.0, t / 0.003)
                val env = attack * exp(-t / (dur * 0.35))
                val s = sin(2 * PI * freq * t) * 0.85 + sin(4 * PI * freq * t) * 0.15
                out[offset + i] = (s * env * volume * Short.MAX_VALUE).toInt().toShort()
            }
            offset += n
        }
        return out
    }
}
