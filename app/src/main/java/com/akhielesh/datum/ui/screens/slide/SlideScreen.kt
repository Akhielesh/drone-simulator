package com.akhielesh.datum.ui.screens.slide

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Detail
import com.akhielesh.datum.core.data.MeasureKind
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.math.Vec2
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.core.sensors.MotionSnapshot
import com.akhielesh.datum.core.sensors.MotionTracker
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.Quantity
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.PlaneBasis
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.VolumeShutter
import com.akhielesh.datum.ui.common.aboveDock
import com.akhielesh.datum.ui.common.rememberMotionSession
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.Chip
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.ProgressRing
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.SegmentedControl
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.GlassWeight
import com.akhielesh.datum.ui.theme.LocalHazeState
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.glass
import androidx.compose.runtime.CompositionLocalProvider
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs
import kotlin.math.floor

enum class SlideAxis(val label: String) { Long("Long edge"), Short("Short edge"), Free("Free") }

private data class SlideMark(val label: String, val along: Double, val at: Vec2)

/** Distance along the chosen axis (signed for the edges, magnitude for free sliding). */
fun alongAxis(p: Vec3, axis: SlideAxis, basis: PlaneBasis): Double = when (axis) {
    SlideAxis.Long -> p dot basis.e2
    SlideAxis.Short -> p dot basis.e1
    SlideAxis.Free -> basis.project(p).length
}

@Composable
fun SlideScreen(preview: MotionSnapshot? = null) {
    val app = LocalApp.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    val session = rememberMotionSession()
    val live by session.state.collectAsStateWithLifecycle()
    val snap = preview ?: live
    KeepScreenOn()

    var axis by remember { mutableStateOf(SlideAxis.Long) }
    var addBody by remember { mutableStateOf(false) }
    var showCal by remember { mutableStateOf(false) }
    val marks = remember { mutableStateListOf<SlideMark>() }
    val path = remember { mutableStateListOf(Vec2(0.0, 0.0)) }

    val basis = remember(snap.up) { PlaneBasis(snap.up) }
    val device = app.device
    val bodyM = (if (axis == SlideAxis.Short) device.widthMm else device.heightMm) / 1000.0
    val along = alongAxis(snap.livePosition, axis, basis)
    val distance = abs(along) + if (addBody) bodyM else 0.0
    val pos2 = basis.project(snap.livePosition)
    val yaw = basis.yawOf(snap.orientation.rotate(Vec3.X))
    val pxPerMeter = (app.physicalPpi / 0.0254).toFloat()

    // The big number glides to the drift-corrected value whenever a slide ends.
    val shown = remember { Animatable(0f) }
    LaunchedEffect(distance, snap.phase) {
        if (snap.phase == MotionTracker.Phase.MOVING) shown.snapTo(distance.toFloat())
        else shown.animateTo(distance.toFloat(), spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    // Ruler detents: a tick every cm (or ¼″), a stronger one every 10 cm (or inch).
    val step = if (settings.units == UnitSystem.METRIC) 0.01 else 0.0254 / 4
    var lastTick by remember { mutableStateOf(0L) }
    LaunchedEffect(along) {
        val idx = floor(abs(along) / step).toLong()
        if (idx != lastTick) {
            if (snap.phase == MotionTracker.Phase.MOVING) {
                val major = if (settings.units == UnitSystem.METRIC) idx % 10 == 0L else idx % 4 == 0L
                app.haptics.tick(if (major) 0.9f else 0.45f)
            }
            lastTick = idx
        }
    }
    LaunchedEffect(pos2) {
        val last = path.lastOrNull()
        if (last == null || (pos2 - last).length > 0.003) path += pos2
    }

    fun reset() {
        session.reset(keepCalibration = true)
        marks.clear()
        path.clear()
        path += Vec2(0.0, 0.0)
        app.haptics.click()
    }

    fun mark() {
        if (snap.phase == MotionTracker.Phase.CALIBRATING) return
        val label = ('A' + marks.size).toString()
        marks += SlideMark(label, along, pos2)
        app.haptics.capture()
        app.tones.tink()
    }
    VolumeShutter { mark() }

    val haze = rememberHazeState()
    Box(Modifier.fillMaxSize()) {
        // Background: the table-fixed tape or graph paper (blurred behind the glass panels).
        Box(Modifier.fillMaxSize().hazeSource(haze)) {
            when (axis) {
                SlideAxis.Free -> GraphPaper(pos2, yaw, path.toList(), marks.map { it.label to it.at }, pxPerMeter, Modifier.fillMaxSize())
                else -> TableTape(
                    offset = along,
                    pxPerMeter = pxPerMeter,
                    units = settings.units,
                    marks = marks.map { TapeMark(it.label, it.along) },
                    modifier = Modifier.fillMaxSize(),
                    horizontal = axis == SlideAxis.Short,
                    needleFraction = if (axis == SlideAxis.Short) 0.5f else 0.56f,
                )
            }
        }
        CompositionLocalProvider(LocalHazeState provides haze) {
            Column(Modifier.fillMaxSize()) {
                TopBar("Slide", subtitle = axis.label) {
                    CircleButton(DatumIcons.Sparkles, onClick = { showCal = true }, size = 38.dp)
                }
                SegmentedControl(
                    SlideAxis.entries.map { it.label }, axis.ordinal,
                    onSelect = {
                        axis = SlideAxis.entries[it]
                        reset()
                    },
                    modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                // Readout card
                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .glass(Shapes.xl, GlassWeight.Regular, elevation = 10.dp)
                        .padding(vertical = 14.dp, horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val (label, color) = when (snap.phase) {
                        MotionTracker.Phase.CALIBRATING -> "Hold still…" to c.orange
                        MotionTracker.Phase.MOVING -> "Measuring" to c.accent
                        MotionTracker.Phase.STILL -> (if (snap.segmentCount == 0) "Ready — slide the phone" else "Locked") to c.green
                    }
                    StatusChip(label, color, pulsing = snap.phase != MotionTracker.Phase.STILL)
                    Spacer(Modifier.height(6.dp))
                    val f = Fmt.length(shown.value.toDouble(), settings.units, settings.inchFractions)
                    Readout(f.value, f.unit, style = Datum.type.readoutL)
                    val extra = buildList {
                        if (snap.sigma > 0) add("± " + Fmt.length(snap.sigma, settings.units))
                        if (axis == SlideAxis.Free && snap.segmentCount > 0) add("path " + Fmt.length(pathLength(path), settings.units))
                        if (addBody) add("incl. phone " + Fmt.length(bodyM, settings.units))
                        if (snap.sampleRateHz > 0) add("%.0f Hz".format(snap.sampleRateHz))
                    }
                    DText(extra.joinToString("  ·  ").ifEmpty { "Glide along a straight edge, pause, read." }, Datum.type.footnote, c.labelSecondary, maxLines = 1)
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    androidx.compose.animation.AnimatedVisibility(
                        snap.phase == MotionTracker.Phase.CALIBRATING,
                        enter = fadeIn() + scaleIn(initialScale = 0.9f),
                        exit = fadeOut(tween(300)) + scaleOut(targetScale = 1.05f),
                    ) {
                        Column(
                            Modifier.glass(Shapes.xl, GlassWeight.Thick, elevation = 14.dp).padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ProgressRing(snap.calibrationProgress.toFloat(), Modifier.size(54.dp), color = c.orange)
                            Spacer(Modifier.height(10.dp))
                            DText("Lay the phone flat and keep it still", Datum.type.headline)
                            DText("Measuring sensor bias at rest", Datum.type.footnote, c.labelSecondary)
                        }
                    }
                }
                if (marks.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        marks.forEachIndexed { i, m ->
                            val total = (if (axis == SlideAxis.Free) m.at.length else abs(m.along)) + if (addBody) bodyM else 0.0
                            val prev = if (i == 0) 0.0 else (if (axis == SlideAxis.Free) (m.at - marks[i - 1].at).length else abs(m.along - marks[i - 1].along))
                            val text = if (i == 0) "${m.label}  ${Fmt.length(total, settings.units)}"
                            else "${m.label}  ${Fmt.length(total, settings.units)}  (+${Fmt.length(prev, settings.units)})"
                            Chip(text, selected = false, onClick = {
                                marks.removeAt(i)
                                app.haptics.click()
                            }, icon = DatumIcons.Pin)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().aboveDock().padding(horizontal = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    ToolButton(DatumIcons.Reset, "Reset", { reset() })
                    ToolButton(DatumIcons.Pin, "Mark", { mark() }, enabled = snap.phase != MotionTracker.Phase.CALIBRATING)
                    ToolButton(DatumIcons.Phone, if (addBody) "+Phone ✓" else "+Phone", { addBody = !addBody }, active = addBody)
                    ToolButton(DatumIcons.Download, "Save", {
                        if (distance <= 0.0) return@ToolButton
                        app.library.add(
                            Measurement(
                                kind = MeasureKind.SLIDE,
                                title = "Slide · ${axis.label}",
                                value = distance,
                                quantity = Quantity.LENGTH,
                                sigma = snap.sigma,
                                method = "IMU slide · ${axis.label.lowercase()}",
                                marks = marks.map { (if (axis == SlideAxis.Free) it.at.length else abs(it.along)) + if (addBody) bodyM else 0.0 },
                                details = buildList {
                                    add(Detail("Distance", distance, Quantity.LENGTH))
                                    if (addBody) add(Detail("Phone length added", bodyM, Quantity.LENGTH))
                                    if (axis == SlideAxis.Free) add(Detail("Path length", pathLength(path), Quantity.LENGTH))
                                    add(Detail("Pauses (drift resets)", snap.segmentCount.toDouble(), Quantity.PLAIN))
                                },
                            ),
                        )
                        app.haptics.success()
                        hud.show(DatumIcons.Check, "Saved")
                    })
                }
            }
        }
        MotionCalibrationSheet(showCal, lastMeasured = abs(alongAxis(snap.position, axis, basis)), onDismiss = { showCal = false })
    }
}

private fun pathLength(path: List<Vec2>): Double {
    var s = 0.0
    for (i in 1 until path.size) s += (path[i] - path[i - 1]).length
    return s
}
