package com.akhielesh.datum.ui.screens.objects

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
import com.akhielesh.datum.ar.ArTool
import com.akhielesh.datum.ar.TrackingStatus
import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.DimEstimate
import com.akhielesh.datum.core.data.EstimateSource
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.units.Fmt
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
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.ShutterButton
import com.akhielesh.datum.ui.components.StepDots
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.screens.ar.ArGate
import com.akhielesh.datum.ui.screens.ar.ArOverlay
import com.akhielesh.datum.ui.screens.ar.ArSessionView
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.LocalHazeState
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass

@Composable
fun ObjectArScreen() {
    val nav = LocalNavigator.current
    val tool = Tool.Object
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PermissionGate(
            Manifest.permission.CAMERA, tool.icon, tool.colors,
            title = "Scan with the camera",
            rationale = "AR follows the room through the camera so you can aim at an object's corners. Nothing is recorded or uploaded.",
        ) {
            ArGate("Use Touch corners, Slide edges or Photos — they don't need AR.") { ObjectArContent() }
        }
        TopBar("AR scan", onBack = { nav.back() })
    }
}

@Composable
private fun ObjectArContent() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val hud = LocalHud.current
    val settings = LocalSettings.current
    val context = LocalContext.current
    DarkChrome()
    KeepScreenOn()
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val arTool = when (draft.shape) {
        ShapeKind.BOX -> ArTool.BOX
        ShapeKind.CYLINDER -> ArTool.CYLINDER
        ShapeKind.SPHERE -> ArTool.SPHERE
    }
    val engine = remember { ArEngine(context) }
    val state by engine.state.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(arTool) { engine.setTool(arTool) }
    VolumeShutter {
        engine.addPoint()
        app.haptics.capture()
    }
    val shape = state.shape
    Box(Modifier.fillMaxSize()) {
        ArSessionView(engine, Modifier.fillMaxSize(), onError = { error = it })
        ArOverlay(state, settings.units, Modifier.fillMaxSize())
        CompositionLocalProvider(LocalHazeState provides null) {
            Column(Modifier.fillMaxSize().padding(top = 104.dp)) {
                StepDots(arTool.stepsToComplete, state.step.coerceAtMost(arTool.stepsToComplete), Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(10.dp))
                CoachBubble(
                    error ?: state.message,
                    Modifier.align(Alignment.CenterHorizontally),
                    icon = if (shape != null) DatumIcons.Check else if (state.status == TrackingStatus.TRACKING) DatumIcons.Target else DatumIcons.Info,
                    tint = if (shape != null) Datum.colors.green else if (error != null || state.status == TrackingStatus.LIMITED) Datum.colors.orange else Datum.colors.accent,
                )
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(shape != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxWidth()) {
                    val s = shape ?: return@AnimatedVisibility
                    Column(
                        Modifier
                            .padding(horizontal = 18.dp)
                            .fillMaxWidth()
                            .glass(Shapes.xl, tint = Color(0xB3151517), elevation = 10.dp)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val f = { v: Double -> Fmt.length(v, settings.units).toString() }
                        DText(
                            when (draft.shape) {
                                ShapeKind.BOX -> "${f(s.length)} × ${f(s.width)} × ${f(s.height)}"
                                ShapeKind.CYLINDER -> "Ø ${f(s.length)} × ${f(s.height)}"
                                ShapeKind.SPHERE -> "Ø ${f(s.length)}"
                            },
                            Datum.type.title2, Color.White,
                        )
                        DText("Measured with ARCore" + if (state.depthEnabled) " + depth" else "", Datum.type.footnote, Color.White.copy(alpha = 0.65f))
                        Spacer(Modifier.height(12.dp))
                        PillButton("Use in model", icon = DatumIcons.Sparkles, onClick = {
                            fun sigma(v: Double) = 0.004 + 0.012 * v
                            val list = when (draft.shape) {
                                ShapeKind.BOX -> listOf(
                                    DimEstimate(Dim.L, s.length, sigma(s.length), EstimateSource.AR),
                                    DimEstimate(Dim.W, s.width, sigma(s.width), EstimateSource.AR),
                                    DimEstimate(Dim.H, s.height, sigma(s.height), EstimateSource.AR),
                                )
                                ShapeKind.CYLINDER -> listOf(
                                    DimEstimate(Dim.L, s.length, sigma(s.length), EstimateSource.AR),
                                    DimEstimate(Dim.H, s.height, sigma(s.height), EstimateSource.AR),
                                )
                                ShapeKind.SPHERE -> listOf(DimEstimate(Dim.L, s.length, sigma(s.length), EstimateSource.AR))
                            }
                            app.objectDraft.replaceEstimates(EstimateSource.AR, list)
                            app.haptics.success()
                            hud.show(DatumIcons.Sparkles, "Model updated")
                            nav.replace(Route.ObjectDesign)
                        }, modifier = Modifier.fillMaxWidth())
                    }
                }
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
                    )
                    ToolButton(DatumIcons.Reset, "Clear", { engine.clear() }, enabled = state.canUndo, onDark = true)
                }
            }
        }
    }
}
