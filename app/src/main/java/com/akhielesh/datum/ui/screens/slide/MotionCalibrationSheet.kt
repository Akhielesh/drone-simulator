package com.akhielesh.datum.ui.screens.slide

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.components.ButtonStyle
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.GlassSheet
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.NumberField
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.theme.Datum

/**
 * "Slide a known length" calibration for every IMU distance tool. Accelerometer scale error and
 * detection latency are systematic per device; one reference slide removes most of both.
 */
@Composable
fun MotionCalibrationSheet(visible: Boolean, lastMeasured: Double, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    val metric = settings.units == UnitSystem.METRIC
    var known by remember { mutableStateOf(if (metric) "30" else "12") }
    GlassSheet(visible, onDismiss, title = "Calibrate distance") {
        DText(
            "Slide the phone along a ruler or tape measure for a known distance (e.g. 30 cm), pause, then enter that distance. " +
                "Datum learns your phone's scale factor and applies it to Slide, Height and Object.",
            Datum.type.callout, c.labelSecondary,
        )
        Spacer(Modifier.height(16.dp))
        KeyValueRow("Last measured", if (lastMeasured > 0) Fmt.length(lastMeasured, settings.units).toString() else "—")
        KeyValueRow("Current scale", "×%.3f".format(settings.motionScale))
        Spacer(Modifier.height(10.dp))
        DText("TRUE DISTANCE", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(bottom = 6.dp))
        NumberField(known, { known = it }, Modifier.fillMaxWidth(), suffix = if (metric) "cm" else "in")
        Spacer(Modifier.height(14.dp))
        PillButton(
            "Apply calibration",
            enabled = lastMeasured > 0.02 && (known.toDoubleOrNull() ?: 0.0) > 0,
            onClick = {
                val k = known.toDoubleOrNull() ?: return@PillButton
                val trueM = if (metric) k / 100 else k * 0.0254
                // lastMeasured already includes the current scale.
                val raw = lastMeasured / settings.motionScale
                val newScale = (trueM / raw).coerceIn(0.6, 1.6)
                app.settings.update { it.copy(motionScale = newScale) }
                app.haptics.success()
                hud.show(DatumIcons.Check, "Scale ×%.3f".format(newScale))
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DText("Tip: pause every 30–50 cm. Each pause lets Datum cancel drift.", Datum.type.footnote, c.labelSecondary, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            PillButton("Reset", onClick = {
                app.settings.update { it.copy(motionScale = 1.0) }
                hud.show(DatumIcons.Reset, "Scale reset")
            }, style = ButtonStyle.Destructive, compact = true)
        }
    }
}
