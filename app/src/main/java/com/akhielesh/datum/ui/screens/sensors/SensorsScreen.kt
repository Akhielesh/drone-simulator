package com.akhielesh.datum.ui.screens.sensors

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.sensors.SensorInfo
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.rememberSensor
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.Sparkline
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.components.pressable
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.screens.ar.rememberArAvailability
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlin.math.sqrt

@Composable
fun SensorsScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val c = Datum.colors
    val device = app.device
    val av = app.sensors.availability
    val ar = rememberArAvailability()
    val sensors = remember { app.sensors.allSensors() }
    var expanded by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize()) {
        TopBar("Sensors", subtitle = "${sensors.size} sensors on this phone", onBack = { nav.back() })
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Column(Modifier.fillMaxWidth().card(Shapes.xl).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(DatumIcons.Phone, Tool.Sensors.colors, size = 46.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            DText(device.marketingName, Datum.type.title3)
                            DText("${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", Datum.type.footnote, c.labelSecondary)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    KeyValueRow("Profile", if (device.isTunedGalaxy) "Tuned" + if (device.approximate) " (approx.)" else "" else "Generic estimate", accent = if (device.isTunedGalaxy) c.green else c.orange)
                    KeyValueRow("Body", "%.1f × %.1f × %.1f mm".format(device.heightMm, device.widthMm, device.depthMm))
                    KeyValueRow("Display", "%.1f″ · %.0f ppi".format(device.screenDiagonalIn, app.physicalPpi))
                    Spacer(Modifier.height(10.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Capability("Gyroscope", av.gyroscope)
                        Capability("Barometer", av.barometer)
                        Capability("Magnetometer", av.magnetometer)
                        Capability("Light", av.light)
                        Capability("Proximity", av.proximity)
                        Capability("Step counter", av.stepCounter)
                        Capability("Hinge", av.hingeAngle)
                        Capability("UWB", av.uwb)
                        Capability("NFC", av.nfc)
                        Capability("ARCore", ar?.isSupported == true)
                    }
                }
                Spacer(Modifier.height(18.dp))
                DText("HARDWARE SENSORS", Datum.type.sectionLabel, c.labelSecondary, Modifier.padding(start = 6.dp, bottom = 8.dp))
            }
            items(sensors.indices.toList(), key = { it }) { i ->
                SensorRow(sensors[i], expanded == i) { expanded = if (expanded == i) null else i }
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun Capability(name: String, ok: Boolean) {
    val c = Datum.colors
    Row(Modifier.card(Shapes.pill).padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        DIcon(if (ok) DatumIcons.Check else DatumIcons.Close, tint = if (ok) c.green else c.labelTertiary, size = 13.dp)
        Spacer(Modifier.width(5.dp))
        DText(name, Datum.type.caption, if (ok) c.label else c.labelTertiary)
    }
}

@Composable
private fun SensorRow(s: SensorInfo, expanded: Boolean, onClick: () -> Unit) {
    val c = Datum.colors
    Column(Modifier.fillMaxWidth().pressable(onClick = onClick).card(Shapes.lg).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DText(s.typeName, Datum.type.headline, maxLines = 1)
                DText(s.name, Datum.type.footnote, c.labelSecondary, maxLines = 1)
            }
            DText(if (s.maxRateHz > 0) "%.0f Hz".format(s.maxRateHz) else "on change", Datum.type.subhead, c.labelSecondary)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(top = 8.dp)) {
                KeyValueRow("Vendor", s.vendor)
                KeyValueRow("Range", "%.4g %s".format(s.maxRange, s.unit))
                KeyValueRow("Resolution", "%.3g %s".format(s.resolution, s.unit))
                KeyValueRow("Power", "%.2f mA".format(s.powerMa))
                if (s.isWakeUp) KeyValueRow("Wake-up", "Yes")
                LiveValues(s.type, s.unit)
            }
        }
    }
}

@Composable
private fun LiveValues(type: Int, unit: String) {
    val c = Datum.colors
    val reading = rememberSensor(type, 50_000) { it.values.copyOf() }
    val history = remember { mutableStateListOf<Float>() }
    val v = reading.value
    LaunchedEffect(v) {
        if (v != null) {
            val m = if (v.size >= 3) sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]) else v[0]
            history += m
            while (history.size > 80) history.removeAt(0)
        }
    }
    if (v == null) {
        DText("Waiting for data… (some sensors only report on change)", Datum.type.footnote, c.labelTertiary)
        return
    }
    DText(v.take(4).joinToString("   ") { "%.3f".format(it) } + "  $unit", Datum.type.mono, c.accent)
    Spacer(Modifier.height(6.dp))
    Sparkline(history.toList(), Modifier.fillMaxWidth().height(46.dp))
}
