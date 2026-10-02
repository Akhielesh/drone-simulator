package com.akhielesh.datum

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.akhielesh.datum.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as DatumApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
        setContent { AppRoot(container) }
    }

    /** Volume keys become a shutter while a capture screen has registered for them. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && container.settings.settings.value.volumeKeyCapture) {
            if (event.repeatCount == 0 && container.keys.dispatchShutter()) {
                pendingShutterUp = true
                return true
            }
            // Swallow auto-repeats of a press that already fired the shutter.
            if (pendingShutterUp) return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** The matching release is swallowed too, so the system volume never changes mid-capture. */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (isVolumeKey(keyCode) && pendingShutterUp) {
            pendingShutterUp = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun isVolumeKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP

    private var pendingShutterUp = false
}
