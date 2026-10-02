package com.akhielesh.datum

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import com.akhielesh.datum.core.data.LibraryRepository
import com.akhielesh.datum.core.data.ObjectDraftStore
import com.akhielesh.datum.core.data.SettingsRepository
import com.akhielesh.datum.core.device.DeviceCatalog
import com.akhielesh.datum.core.device.DeviceProfile
import com.akhielesh.datum.core.feedback.Haptics
import com.akhielesh.datum.core.feedback.Tones
import com.akhielesh.datum.core.sensors.SensorHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.CopyOnWriteArrayList

/** Hand-rolled dependency container: one instance per process, created by [DatumApp]. */
class AppContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsRepository(app, scope)
    val library = LibraryRepository(app, scope)
    val sensors = SensorHub(app)
    val haptics = Haptics(app) { settings.settings.value.haptics }
    val tones = Tones { settings.settings.value.sounds }
    val objectDraft = ObjectDraftStore()
    val keys = HardwareKeyBus()

    private val detectedDevice: DeviceProfile by lazy { DeviceCatalog.detect(app) }

    /** Detected profile with any user overrides of the body dimensions applied. */
    val device: DeviceProfile
        get() {
            val s = settings.settings.value
            val d = detectedDevice
            return d.copy(
                heightMm = if (s.bodyLengthOverrideMm > 0) s.bodyLengthOverrideMm else d.heightMm,
                widthMm = if (s.bodyWidthOverrideMm > 0) s.bodyWidthOverrideMm else d.widthMm,
            )
        }

    val physicalPpi: Double get() = DeviceCatalog.physicalPpi(app, detectedDevice) * settings.settings.value.screenScale
}

/** Volume keys double as a shutter while a capture screen is showing (no screen tap = no shake). */
class HardwareKeyBus {
    private val handlers = CopyOnWriteArrayList<() -> Boolean>()

    fun register(handler: () -> Boolean): () -> Unit {
        handlers += handler
        return { handlers -= handler }
    }

    fun dispatchShutter(): Boolean = handlers.lastOrNull()?.invoke() ?: false
}

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }
