package com.akhielesh.datum.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.akhielesh.datum.core.sensors.LevelCalibration
import com.akhielesh.datum.core.units.UnitSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** macOS's accent palette, in System Settings order. */
enum class Accent { MULTICOLOR, BLUE, PURPLE, PINK, RED, ORANGE, YELLOW, GREEN, GRAPHITE }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.MULTICOLOR,
    val units: UnitSystem = UnitSystem.METRIC,
    val inchFractions: Boolean = true,
    val haptics: Boolean = true,
    val sounds: Boolean = true,
    val onboardingDone: Boolean = false,
    /** Person's height, used to estimate where the phone is held for sight measurements. */
    val userHeightCm: Double = 172.0,
    /** Phone height above the ground when aiming; 0 = derive from [userHeightCm]. */
    val eyeHeightOverrideCm: Double = 0.0,
    val levelCalibration: LevelCalibration = LevelCalibration(),
    /** IMU distance correction from "slide a known length" calibration. */
    val motionScale: Double = 1.0,
    /** Screen ruler correction from credit-card calibration (1 = trust the panel spec). */
    val screenScale: Double = 1.0,
    val seaLevelPressureHpa: Double = 1013.25,
    val soundOffsetDb: Double = 0.0,
    val autoCapture: Boolean = true,
    val volumeKeyCapture: Boolean = true,
    val levelSoundGuide: Boolean = false,
    val bodyLengthOverrideMm: Double = 0.0,
    val bodyWidthOverrideMm: Double = 0.0,
) {
    /** Camera height when the phone is raised to eye level (eye height ≈ 93.6 % of stature). */
    val eyeHeightM: Double get() = if (eyeHeightOverrideCm > 0) eyeHeightOverrideCm / 100 else userHeightCm * 0.936 / 100
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context, scope: CoroutineScope) {

    private object K {
        val theme = stringPreferencesKey("theme")
        val accent = stringPreferencesKey("accent")
        val units = stringPreferencesKey("units")
        val fractions = booleanPreferencesKey("fractions")
        val haptics = booleanPreferencesKey("haptics")
        val sounds = booleanPreferencesKey("sounds")
        val onboarding = booleanPreferencesKey("onboarding")
        val userHeight = doublePreferencesKey("user_height_cm")
        val eyeOverride = doublePreferencesKey("eye_height_cm")
        val levelBx = doublePreferencesKey("level_bx")
        val levelBy = doublePreferencesKey("level_by")
        val levelEdge = doublePreferencesKey("level_edge")
        val motionScale = doublePreferencesKey("motion_scale")
        val screenScale = doublePreferencesKey("screen_scale")
        val seaLevel = doublePreferencesKey("sea_level_hpa")
        val soundOffset = doublePreferencesKey("sound_offset")
        val autoCapture = booleanPreferencesKey("auto_capture")
        val volumeKeys = booleanPreferencesKey("volume_keys")
        val levelSound = booleanPreferencesKey("level_sound")
        val bodyLength = doublePreferencesKey("body_length")
        val bodyWidth = doublePreferencesKey("body_width")
    }

    private val defaults = AppSettings(
        units = if (Locale.getDefault().country in setOf("US", "LR", "MM")) UnitSystem.IMPERIAL else UnitSystem.METRIC,
    )

    val settings: StateFlow<AppSettings> = context.dataStore.data
        .map { p -> read(p) }
        .stateIn(scope, SharingStarted.Eagerly, defaults)

    private val writeScope = scope

    private fun read(p: Preferences): AppSettings = AppSettings(
        themeMode = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: defaults.themeMode,
        accent = p[K.accent]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: defaults.accent,
        units = p[K.units]?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() } ?: defaults.units,
        inchFractions = p[K.fractions] ?: defaults.inchFractions,
        haptics = p[K.haptics] ?: defaults.haptics,
        sounds = p[K.sounds] ?: defaults.sounds,
        onboardingDone = p[K.onboarding] ?: false,
        userHeightCm = p[K.userHeight] ?: defaults.userHeightCm,
        eyeHeightOverrideCm = p[K.eyeOverride] ?: 0.0,
        levelCalibration = LevelCalibration(p[K.levelBx] ?: 0.0, p[K.levelBy] ?: 0.0, p[K.levelEdge] ?: 0.0),
        motionScale = p[K.motionScale] ?: 1.0,
        screenScale = p[K.screenScale] ?: 1.0,
        seaLevelPressureHpa = p[K.seaLevel] ?: 1013.25,
        soundOffsetDb = p[K.soundOffset] ?: 0.0,
        autoCapture = p[K.autoCapture] ?: true,
        volumeKeyCapture = p[K.volumeKeys] ?: true,
        levelSoundGuide = p[K.levelSound] ?: false,
        bodyLengthOverrideMm = p[K.bodyLength] ?: 0.0,
        bodyWidthOverrideMm = p[K.bodyWidth] ?: 0.0,
    )

    /** Applies [transform] to the current settings and persists the result. */
    fun update(transform: (AppSettings) -> AppSettings) {
        writeScope.launch {
            context.dataStore.edit { p ->
                val s = transform(read(p))
                p[K.theme] = s.themeMode.name
                p[K.accent] = s.accent.name
                p[K.units] = s.units.name
                p[K.fractions] = s.inchFractions
                p[K.haptics] = s.haptics
                p[K.sounds] = s.sounds
                p[K.onboarding] = s.onboardingDone
                p[K.userHeight] = s.userHeightCm
                p[K.eyeOverride] = s.eyeHeightOverrideCm
                p[K.levelBx] = s.levelCalibration.biasX
                p[K.levelBy] = s.levelCalibration.biasY
                p[K.levelEdge] = s.levelCalibration.edgeOffsetDeg
                p[K.motionScale] = s.motionScale
                p[K.screenScale] = s.screenScale
                p[K.seaLevel] = s.seaLevelPressureHpa
                p[K.soundOffset] = s.soundOffsetDb
                p[K.autoCapture] = s.autoCapture
                p[K.volumeKeys] = s.volumeKeyCapture
                p[K.levelSound] = s.levelSoundGuide
                p[K.bodyLength] = s.bodyLengthOverrideMm
                p[K.bodyWidth] = s.bodyWidthOverrideMm
            }
        }
    }
}
