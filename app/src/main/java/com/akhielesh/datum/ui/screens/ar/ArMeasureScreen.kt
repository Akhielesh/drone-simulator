package com.akhielesh.datum.ui.screens.ar

import android.Manifest
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.ar.ArEngine
import com.akhielesh.datum.ar.ArFrameState
import com.akhielesh.datum.ar.ArTool
import com.akhielesh.datum.ar.TrackingStatus
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.DarkChrome
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.PermissionGate
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.VolumeShutter
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.components.CoachBubble
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.ShutterButton
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.LocalHazeState
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass

private val overlayTint = Color(0xB3151517)

@Composable
fun ArMeasureScreen() {
    val nav = LocalNavigator.current
    val tool = Tool.ArMeasure
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PermissionGate(
            Manifest.permission.CAMERA, tool.icon, tool.colors,
            title = "Measure in AR",
            rationale = "AR tracks the room through the camera so you can measure between any two points you aim at. Camera frames never leave your phone.",
        ) {
            ArGate("Use Slide, Height or Object → Motion instead — they work with the motion sensors alone.") {
                ArMeasureContent()
            }
        }
        TopBar("AR Measure", onBack = { nav.back() })
    }
}

@Composable
private fun ArMeasureContent() {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val context = LocalContext.current
    DarkChrome()
    KeepScreenOn()
    val engine = remember { ArEngine(context) }
    val state by engine.state.collectAsStateWithLifecycle()
    var tool by remember { mutableStateOf(ArTool.LINE) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tool) { engine.setTool(tool) }
    VolumeShutter {
        engine.addPoint()
        app.haptics.capture()
    }
    Box(Modifier.fillMaxSize()) {
        ArSessionView(engine, Modifier.fillMaxSize(), onError = { error = it })
        ArOverlay(state, settings.units, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalHazeState provides null) {
            Column(Modifier.fillMaxSize().padding(top = 100.dp)) {
                SegmentedControl(
                    listOf("Line", "Path", "Area", "Height"),
                    listOf(ArTool.LINE, ArTool.PATH, ArTool.AREA, ArTool.HEIGHT).indexOf(tool),
                    onSelect = { tool = listOf(ArTool.LINE, ArTool.PATH, ArTool.AREA, ArTool.HEIGHT)[it] },
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                CoachBubble(
                    error ?: state.message,
                    Modifier.align(Alignment.CenterHorizontally),
                    icon = if (state.status == TrackingStatus.TRACKING) DatumIcons.Target else DatumIcons.Info,
                    tint = if (error != null || state.status == TrackingStatus.LIMITED) Datum.colors.orange else Datum.colors.accent,
                )
                Spacer(Modifier.weight(1f))
                ReadoutPanel(state)
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolButton(DatumIcons.Undo, "Undo", { engine.undo() }, enabled = state.canUndo, onDark = true)
                    ShutterButton(
                        onClick = {
                            engine.addPoint()
                            app.haptics.capture()
                        },
                        icon = DatumIcons.Plus,
                        enabled = state.reticleValid || tool == ArTool.HEIGHT,
                    )
                    ToolButton(DatumIcons.Download, "Save", {
                        val r = state.readout ?: return@ToolButton
                        app.library.add(
                            Measurement(
                                kind = MeasureKind.AR,
                                title = "AR · ${tool.label}",
                                value = r.value,
                                quantity = if (r.isArea) Quantity.AREA else Quantity.LENGTH,
                                method = "ARCore" + if (state.depthEnabled) " + depth" else "",
                                details = r.secondary.map { (k, v) -> Detail(k, v, if (k == "Segments") Quantity.PLAIN else Quantity.LENGTH) },
                            ),
                        )
                        app.haptics.success()
                        hud.show(DatumIcons.Check, "Saved")
                    }, enabled = state.readout != null, onDark = true)
                }
            }
        }
    }
}

@Composable
private fun ReadoutPanel(state: ArFrameState) {
    val settings = LocalSettings.current
    val r = state.readout
    AnimatedVisibility(r != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxWidth()) {
        if (r == null) return@AnimatedVisibility
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .glass(Shapes.xl, tint = overlayTint, elevation = 10.dp)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DText(r.title.uppercase(), Datum.type.sectionLabel, Color.White.copy(alpha = 0.6f))
            val f = if (r.isArea) Fmt.area(r.value, settings.units) else Fmt.length(r.value, settings.units, settings.inchFractions)
            Readout(f.value, f.unit, style = Datum.type.readoutM, color = Color.White, unitColor = Color.White.copy(alpha = 0.6f))
            if (r.secondary.isNotEmpty()) {
                DText(
                    r.secondary.joinToString("  ·  ") { (k, v) ->
                        if (k == "Segments") "$k ${v.toInt()}" else "$k ${Fmt.length(v, settings.units)}"
                    },
                    Datum.type.footnote, Color.White.copy(alpha = 0.7f), maxLines = 2,
                )
            }
        }
    }
}
