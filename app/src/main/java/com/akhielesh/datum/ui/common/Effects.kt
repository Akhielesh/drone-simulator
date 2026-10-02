package com.akhielesh.datum.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.AppSettings
import com.akhielesh.datum.core.sensors.Reading
import com.akhielesh.datum.ui.nav.LocalChrome
import kotlinx.coroutines.flow.map

val LocalSettings = staticCompositionLocalOf { AppSettings() }

/** Whether the resolved appearance (after the user's Light/Dark/Auto choice) is dark. */
@Composable
fun isDarkNow(): Boolean = com.akhielesh.datum.ui.theme.Datum.colors.isDark

/** Keeps the display awake while a measuring tool is on screen. */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

/** Hides the dock while [hidden] (immersive capture). */
@Composable
fun HideDock(hidden: Boolean = true) {
    val chrome = LocalChrome.current
    DisposableEffect(hidden) {
        chrome.dockHidden = hidden
        onDispose { chrome.dockHidden = false }
    }
}

/** Light status-bar icons over camera / AR content. */
@Composable
fun DarkChrome() {
    val chrome = LocalChrome.current
    DisposableEffect(Unit) {
        chrome.darkContent = true
        onDispose { chrome.darkContent = false }
    }
}

/** Volume keys act as a shutter while this is composed (pressing them doesn't shake the phone). */
@Composable
fun VolumeShutter(enabled: Boolean = true, onShutter: () -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val cb by rememberUpdatedState(onShutter)
    DisposableEffect(enabled, settings.volumeKeyCapture) {
        if (!enabled || !settings.volumeKeyCapture) return@DisposableEffect onDispose { }
        val unregister = app.keys.register {
            cb()
            true
        }
        onDispose { unregister() }
    }
}

/** Starts an engine when the screen resumes and stops it on pause/dispose. */
@Composable
fun EngineLifecycle(key: Any?, start: () -> Unit, stop: () -> Unit) {
    LifecycleResumeEffect(key) {
        start()
        onPauseOrDispose { stop() }
    }
}

/** Lifecycle-aware sensor value: registration lives only while the screen is visible. */
@Composable
fun <T> rememberSensor(type: Int, periodUs: Int, transform: (Reading) -> T): State<T?> {
    val hub = LocalApp.current.sensors
    val flow = remember(type, periodUs) { hub.readings(type, periodUs).map { transform(it) } }
    return flow.collectAsStateWithLifecycle(initialValue = null)
}

@Composable
fun displayRotation(): Int {
    val context = LocalContext.current
    LocalConfiguration.current // recompose on rotation
    @Suppress("DEPRECATION")
    return runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 30) context.display.rotation
        else context.getSystemService(WindowManager::class.java).defaultDisplay.rotation
    }.getOrDefault(Surface.ROTATION_0)
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
