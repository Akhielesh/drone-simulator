package com.akhielesh.datum.ui.common

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.GlassWeight
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass

@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    val state = remember(permission) {
        PermissionState(permission, ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.granted = granted
        if (!granted) {
            val activity = context.findActivity()
            state.permanentlyDenied = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        }
    }
    state.launch = { launcher.launch(permission) }
    // The user may grant it from system settings and come back.
    LifecycleResumeEffect(permission) {
        state.granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }
    return state
}

class PermissionState(val permission: String, initiallyGranted: Boolean) {
    var granted by mutableStateOf(initiallyGranted)
    var permanentlyDenied by mutableStateOf(false)
    internal var launch: () -> Unit = {}
    fun request() = launch()
}

/**
 * Just-in-time permission primer: explains *why* before the system dialog appears, and offers a
 * route to Settings if the user previously chose "Don't allow".
 */
@Composable
fun PermissionGate(
    permission: String,
    icon: ImageVector,
    colors: List<Color>,
    title: String,
    rationale: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPermissionState(permission)
    if (state.granted) {
        content()
        return
    }
    val context = LocalContext.current
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .glass(Shapes.xxl, GlassWeight.Thick, elevation = 16.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(icon, colors, size = 68.dp)
            Spacer(Modifier.height(4.dp))
            DText(title, Datum.type.title2, align = TextAlign.Center)
            DText(rationale, Datum.type.callout, Datum.colors.labelSecondary, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            if (state.permanentlyDenied) {
                PillButton(
                    "Open Settings",
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                DText("Permission was declined. Enable it in Settings › Permissions.", Datum.type.footnote, Datum.colors.labelTertiary, align = TextAlign.Center)
            } else {
                PillButton("Continue", onClick = { state.request() }, modifier = Modifier.fillMaxWidth())
                DText("You can change this anytime in Settings.", Datum.type.footnote, Datum.colors.labelTertiary, align = TextAlign.Center)
            }
        }
    }
}
