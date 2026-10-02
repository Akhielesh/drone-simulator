package com.akhielesh.datum.ui.screens.sound

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.sensors.SoundMeter
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.EngineLifecycle
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.PermissionGate
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.GlassSheet
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.MacSlider
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SectionCard
import com.akhielesh.datum.ui.components.Sparkline
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.components.ZoneMeter
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.theme.Datum
import kotlin.math.pow

private val examples = listOf(
    30.0 to "Whisper", 45.0 to "Quiet office", 60.0 to "Conversation", 70.0 to "Vacuum cleaner",
    85.0 to "City traffic", 100.0 to "Motorbike", 110.0 to "Concert",
)

/** NIOSH: 8 h at 85 dB(A), halving every +3 dB. */
private fun safeExposure(db: Double): String {
    if (db < 85) return "No limit"
    val hours = 8.0 / 2.0.pow((db - 85) / 3)
    return if (hours >= 1) "%.1f h".format(hours) else "%.0f min".format(hours * 60)
}

@Composable
fun SoundScreen() {
    val nav = LocalNavigator.current
    val tool = Tool.Sound
    Column(Modifier.fillMaxSize()) {
        TopBar("Sound", subtitle = "A-weighted level", onBack = { nav.back() })
        PermissionGate(
            Manifest.permission.RECORD_AUDIO, tool.icon, tool.colors,
            title = "Measure noise",
            rationale = "The sound meter reads the microphone level in real time. Audio is analysed on the fly and never recorded or stored.",
        ) {
            SoundContent()
        }
    }
}

@Composable
private fun SoundContent() {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val context = LocalContext.current
    val c = Datum.colors
    val scope = rememberCoroutineScope()
    KeepScreenOn()
    val meter = remember { SoundMeter(context) { app.settings.settings.value.soundOffsetDb } }
    EngineLifecycle(meter, start = { meter.start(scope) }, stop = { meter.stop() })
    val s by meter.state.collectAsStateWithLifecycle()
    var showCal by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Readout("%.0f".format(s.levelDb), "dB(A)", style = Datum.type.readoutXL)
                val loud = s.levelDb >= 85
                StatusChip(
                    if (s.error != null) s.error!! else if (loud) "Loud — limit exposure" else "Safe level",
                    if (s.error != null || loud) c.orange else c.green,
                    pulsing = loud,
                )
            }
            Spacer(Modifier.height(18.dp))
            ZoneMeter(s.levelDb.toFloat(), 20f, 120f, 70f, 85f, Modifier.fillMaxWidth().height(14.dp), peak = s.maxDb.toFloat())
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Last 24 seconds") {
                Sparkline(s.history, Modifier.fillMaxWidth().height(90.dp).padding(14.dp), color = c.accent, fixedMin = 20f, fixedMax = 110f, threshold = 85f)
            }
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "Statistics") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    KeyValueRow("Average (Leq)", "%.1f".format(s.leqDb), unit = "dB")
                    KeyValueRow("Maximum", "%.1f".format(s.maxDb), unit = "dB")
                    KeyValueRow("Instant peak", "%.1f".format(s.peakDb), unit = "dB")
                    KeyValueRow("Safe exposure", safeExposure(s.levelDb), hint = "NIOSH recommendation")
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionCard(title = "For comparison") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    examples.forEach { (db, name) ->
                        val near = kotlin.math.abs(s.levelDb - db) < 6
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            DIcon(if (near) DatumIcons.Sound else DatumIcons.Minus, tint = if (near) c.accent else c.labelQuaternary, size = 16.dp)
                            Spacer(Modifier.width(10.dp))
                            DText(name, Datum.type.callout, if (near) c.label else c.labelSecondary, Modifier.weight(1f))
                            DText("${db.toInt()} dB", Datum.type.callout, c.labelSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Row(Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ToolButton(DatumIcons.Sparkles, "Calibrate", { showCal = true })
            ToolButton(DatumIcons.Download, "Save", {
                app.library.add(
                    Measurement(
                        kind = MeasureKind.SOUND, title = "Noise level", value = s.levelDb, quantity = Quantity.DECIBEL, method = "Microphone · A-weighted",
                        details = listOf(Detail("Leq (dB)", s.leqDb, Quantity.PLAIN), Detail("Max (dB)", s.maxDb, Quantity.PLAIN)),
                    ),
                )
                hud.show(DatumIcons.Check, "Saved")
            })
        }
    }
    var offset by remember(showCal) { mutableStateOf(settings.soundOffsetDb.toFloat()) }
    GlassSheet(showCal, { showCal = false }, title = "Calibrate sound level") {
        DText("Phone microphones vary. If you have a reference meter, match its reading with this offset.", Datum.type.callout, c.labelSecondary)
        Spacer(Modifier.height(12.dp))
        DText("%+.1f dB".format(offset), Datum.type.title2, c.accent)
        MacSlider(offset, { offset = it }, range = -20f..20f, steps = 80, onValueChangeFinished = {
            app.settings.update { it.copy(soundOffsetDb = offset.toDouble()) }
        })
        Box(Modifier.height(6.dp))
    }
}
