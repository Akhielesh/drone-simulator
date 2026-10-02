package com.akhielesh.datum.ui.screens.objects

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.DimEstimate
import com.akhielesh.datum.core.data.EstimateSource
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.geometry.BoxFitter
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.core.sensors.MotionSnapshot
import com.akhielesh.datum.core.sensors.MotionTracker
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.ui.common.KeepScreenOn
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.ToolButton
import com.akhielesh.datum.ui.common.VolumeShutter
import com.akhielesh.datum.ui.common.aboveNavBar
import com.akhielesh.datum.ui.common.rememberMotionSession
import com.akhielesh.datum.ui.components.Chip
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.KeyValueRow
import com.akhielesh.datum.ui.components.LocalHud
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.ProgressRing
import com.akhielesh.datum.ui.components.Readout
import com.akhielesh.datum.ui.components.ShutterButton
import com.akhielesh.datum.ui.components.StatusChip
import com.akhielesh.datum.ui.components.StepDots
import com.akhielesh.datum.ui.components.TopBar
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

private data class Step(val title: String, val hint: String, val dim: Dim?)

/** Which part of the phone touches the object. Offsets are from the phone's centre (≈ IMU). */
private enum class Contact(val label: String) { TopEdge("Top edge"), BottomEdge("Bottom edge"), SideEdge("Long side") }

private const val HOLD_SECONDS = 0.5

private fun cornerSteps(shape: ShapeKind) = when (shape) {
    ShapeKind.BOX -> listOf(
        Step("Corner A", "Rest the phone's top edge on a bottom corner, then tap Start.", null),
        Step("Corner B", "Move along the length to the next bottom corner and hold still.", Dim.L),
        Step("Corner C", "Now along the width to the next corner. Hold still.", Dim.W),
        Step("Corner D", "Straight up that edge to the top corner. Hold still.", Dim.H),
    )
    ShapeKind.CYLINDER -> listOf(
        Step("Rim", "Touch one side of the top rim, then tap Start.", null),
        Step("Across", "Move straight across to the opposite side of the rim.", Dim.L),
        Step("Base", "Move straight down the side to the base.", Dim.H),
    )
    ShapeKind.SPHERE -> listOf(
        Step("One side", "Touch the widest point on one side, then tap Start.", null),
        Step("Other side", "Move straight across to the opposite side.", Dim.L),
    )
}

private fun edgeSteps(shape: ShapeKind) = when (shape) {
    ShapeKind.BOX -> listOf(
        Step("Length", "Lay the phone against the length edge at one end. Tap Ready, wait for the tick, then glide to the other end.", Dim.L),
        Step("Width", "Move to the width edge. Tap Ready, then glide along it.", Dim.W),
        Step("Height", "Hold the phone against a vertical edge at the bottom. Tap Ready, then glide up.", Dim.H),
    )
    ShapeKind.CYLINDER -> listOf(
        Step("Diameter", "Lay the phone across the top at one side. Tap Ready, then glide across.", Dim.L),
        Step("Height", "Hold the phone against the side at the bottom. Tap Ready, then glide up.", Dim.H),
    )
    ShapeKind.SPHERE -> listOf(
        Step("Diameter", "Tap Ready at one side, then glide across the widest part.", Dim.L),
    )
}

@Composable
fun ObjectMotionScreen(edges: Boolean) {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val settings = LocalSettings.current
    val hud = LocalHud.current
    val c = Datum.colors
    KeepScreenOn()
    val draft by app.objectDraft.draft.collectAsStateWithLifecycle()
    val shape = draft.shape
    val steps = remember(shape, edges) { if (edges) edgeSteps(shape) else cornerSteps(shape) }
    val session = rememberMotionSession(handheld = true)
    val snap by session.state.collectAsStateWithLifecycle()

    var step by remember(shape, edges) { mutableIntStateOf(0) }
    var armed by remember { mutableStateOf(false) }
    // Set once the tracker has re-entered calibration after arming, so a stale pre-reset snapshot
    // can never be mistaken for the first capture.
    var calibrationSeen by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(Contact.TopEdge) }
    var addBody by remember { mutableStateOf(false) }
    val captures = remember(shape, edges) { mutableStateListOf<Pair<Vec3, Double>>() }
    val sweeps = remember(shape, edges) { mutableStateListOf<DimEstimate>() }
    var segmentsAtLast by remember { mutableIntStateOf(0) }
    val device = app.device
    val done = step >= steps.size

    fun contactOffset(): Vec3 {
        val h = device.heightMm / 2000
        val w = device.widthMm / 2000
        return when (contact) {
            Contact.TopEdge -> Vec3(0.0, h, 0.0)
            Contact.BottomEdge -> Vec3(0.0, -h, 0.0)
            Contact.SideEdge -> Vec3(w, 0.0, 0.0)
        }
    }
    fun contactPoint(s: MotionSnapshot, live: Boolean) =
        (if (live) s.livePosition else s.position) + s.orientation.rotate(contactOffset())

    fun reset() {
        step = 0
        armed = false
        calibrationSeen = false
        captures.clear()
        sweeps.clear()
        session.reset(keepCalibration = false)
    }

    fun captureCorner() {
        val p = contactPoint(snap, live = false)
        val sigma = if (captures.isEmpty()) 0.0 else (snap.lastSegment?.sigma ?: 0.01)
        captures += p to sigma
        segmentsAtLast = snap.segmentCount
        step++
        app.haptics.capture()
        app.tones.tink()
    }

    // Corners: A is taken when the rest calibration at the first corner completes; later corners
    // when the phone has moved and then held still.
    LaunchedEffect(snap.phase) {
        if (armed && snap.phase == MotionTracker.Phase.CALIBRATING) calibrationSeen = true
    }
    LaunchedEffect(snap.phase, snap.stillSeconds, snap.segmentCount) {
        if (done || edges || !armed || !calibrationSeen) return@LaunchedEffect
        if (snap.phase != MotionTracker.Phase.STILL) return@LaunchedEffect
        if (captures.isEmpty()) {
            captureCorner()
        } else if (settings.autoCapture && snap.segmentCount > segmentsAtLast && snap.stillSeconds >= HOLD_SECONDS) {
            val moved = (contactPoint(snap, false) - captures.last().first).length
            if (moved > 0.02) captureCorner()
        }
    }
    // Edges: one completed slide after Ready = one dimension.
    LaunchedEffect(snap.segmentCount) {
        if (done || !edges || !armed || !calibrationSeen) return@LaunchedEffect
        val seg = snap.lastSegment ?: return@LaunchedEffect
        if (snap.segmentCount <= segmentsAtLast) return@LaunchedEffect
        val len = seg.displacement.length
        if (len < 0.02) return@LaunchedEffect
        // The phone lies along the edge, so its long side is what gets added.
        val body = if (addBody) device.heightMm / 1000 else 0.0
        val dim = steps[step].dim ?: return@LaunchedEffect
        sweeps += DimEstimate(dim, len + body, seg.sigma, EstimateSource.MOTION, "edges")
        segmentsAtLast = snap.segmentCount
        armed = false
        calibrationSeen = false
        step++
        app.haptics.capture()
        app.tones.tink()
    }

    fun primaryAction() {
        if (done) return
        if (edges) {
            if (!armed) {
                session.reset(keepCalibration = false)
                segmentsAtLast = 0
                calibrationSeen = false
                armed = true
                app.haptics.click()
            }
        } else {
            if (!armed) {
                session.reset(keepCalibration = false)
                segmentsAtLast = 0
                calibrationSeen = false
                armed = true
                app.haptics.click()
            } else if (snap.phase == MotionTracker.Phase.STILL && captures.isNotEmpty()) {
                captureCorner()
            }
        }
    }
    VolumeShutter(enabled = !done) { primaryAction() }

    // Results
    val results: List<DimEstimate> = remember(done, captures.size, sweeps.size) {
        if (!done) emptyList() else if (edges) sweeps.toList() else computeCorners(shape, captures.toList(), snap.up)
    }
    val quality: Double? = remember(done, captures.size) {
        if (done && !edges && shape == ShapeKind.BOX && captures.size == 4) {
            BoxFitter.fromWalk(captures[0].first, captures[1].first, captures[2].first, captures[3].first).maxAngleErrorDeg
        } else {
            null
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            if (edges) "Slide edges" else "Touch corners",
            subtitle = if (done) "Complete" else "Step ${step + 1} of ${steps.size}",
            onBack = { nav.back() },
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StepDots(steps.size, step.coerceAtMost(steps.size))
            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = if (done) -1 else step,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(180)) },
                label = "stepCard",
            ) { s ->
                Row(
                    Modifier.fillMaxWidth().card(Shapes.xl).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhoneContactIllustration(if (edges) Contact.SideEdge else contact, Modifier.size(54.dp, 78.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        if (s < 0) {
                            DText("All done", Datum.type.title2)
                            DText("Review the result below, then add it to your object.", Datum.type.callout, c.labelSecondary)
                        } else {
                            DText(steps[s].title, Datum.type.title2)
                            DText(steps[s].hint, Datum.type.callout, c.labelSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            if (!done) {
                LiveReadout(snap, armed, edges, captures.lastOrNull()?.first?.let { last -> (contactPoint(snap, live = true) - last).length }, segmentsAtLast)
                Spacer(Modifier.height(14.dp))
            }
            if (!edges) {
                WalkPreview(captures.map { it.first }, if (armed && !done && captures.isNotEmpty()) contactPoint(snap, true) else null, snap.up, Modifier.fillMaxWidth().height(200.dp).card(Shapes.xl))
                Spacer(Modifier.height(12.dp))
                if (step == 0 && !armed) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Contact.entries.forEach { ct -> Chip(ct.label, ct == contact, onClick = { contact = ct }) }
                    }
                }
            } else {
                EdgeGuide(
                    shape = shape,
                    dims = steps.mapNotNull { it.dim },
                    current = step,
                    measured = sweeps.associate { it.dim to it.value },
                    live = if (armed && snap.phase == MotionTracker.Phase.MOVING) {
                        snap.livePosition.length + if (addBody) device.heightMm / 1000 else 0.0
                    } else {
                        null
                    },
                    units = settings.units,
                    modifier = Modifier.fillMaxWidth().height(210.dp).card(Shapes.xl),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip(if (addBody) "Adds phone length ✓" else "Add phone length", addBody, onClick = { addBody = !addBody }, icon = DatumIcons.Phone)
                }
            }
            val captured = if (edges) sweeps.toList() else partialCorners(shape, captures.map { it.first })
            if (captured.isNotEmpty() || done) {
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().card(Shapes.lg).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    (if (done) results else captured).forEach { e ->
                        KeyValueRow(dimName(e.dim, shape), Fmt.length(e.value, settings.units).toString(), hint = if (e.sigma > 0) "± " + Fmt.length(e.sigma, settings.units) else null)
                    }
                    quality?.let {
                        KeyValueRow("Squareness", if (it < 4) "Excellent" else if (it < 9) "Good" else "Check corners", hint = "worst angle off by %.1f°".format(it), accent = if (it < 9) c.green else c.orange)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        Row(
            Modifier.fillMaxWidth().aboveNavBar().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolButton(DatumIcons.Reset, "Restart", { reset() })
            if (done) {
                PillButton("Use in model", icon = DatumIcons.Sparkles, onClick = {
                    if (results.isNotEmpty()) {
                        app.objectDraft.replaceEstimates(EstimateSource.MOTION, results)
                        app.haptics.success()
                        hud.show(DatumIcons.Sparkles, "Model updated")
                        nav.replace(Route.ObjectDesign)
                    }
                })
            } else {
                ShutterButton(
                    onClick = { primaryAction() },
                    icon = if (!armed) DatumIcons.Play else DatumIcons.Target,
                    color = if (!armed) c.green else c.accent,
                )
            }
            ToolButton(DatumIcons.Undo, "Back step", {
                if (edges) {
                    if (sweeps.isNotEmpty()) {
                        sweeps.removeAt(sweeps.lastIndex)
                        step = sweeps.size
                        armed = false
                    }
                } else if (captures.isNotEmpty()) {
                    reset()
                }
            }, enabled = step > 0)
        }
    }
}

@Composable
private fun LiveReadout(snap: MotionSnapshot, armed: Boolean, edges: Boolean, fromLast: Double?, segmentsAtLast: Int) {
    val settings = LocalSettings.current
    val c = Datum.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        val (label, color) = when {
            !armed -> (if (edges) "Tap Ready at the start" else "Tap Start on the first corner") to c.labelSecondary
            snap.phase == MotionTracker.Phase.CALIBRATING -> "Hold still…" to c.orange
            snap.phase == MotionTracker.Phase.MOVING -> "Moving" to c.accent
            else -> "Hold still to capture" to c.green
        }
        StatusChip(label, color, pulsing = armed && snap.phase != MotionTracker.Phase.STILL)
        Spacer(Modifier.height(8.dp))
        val value = if (edges) snap.livePosition.length else fromLast ?: 0.0
        val f = Fmt.length(value, settings.units, settings.inchFractions)
        Box(contentAlignment = Alignment.Center) {
            Readout(f.value, f.unit, style = Datum.type.readoutL)
        }
        val holding = armed && snap.phase == MotionTracker.Phase.STILL && snap.segmentCount > segmentsAtLast
        if (snap.phase == MotionTracker.Phase.CALIBRATING || holding) {
            Spacer(Modifier.height(6.dp))
            ProgressRing(
                if (snap.phase == MotionTracker.Phase.CALIBRATING) snap.calibrationProgress.toFloat() else (snap.stillSeconds / HOLD_SECONDS).toFloat(),
                Modifier.size(30.dp), color = if (holding) c.green else c.orange, stroke = 3.dp,
            )
        }
    }
}

/** Phone outline with a glowing dot where it should touch the object. */
@Composable
private fun PhoneContactIllustration(contact: Contact, modifier: Modifier) {
    val c = Datum.colors
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "glow")
    val glow by t.animateFloat(0.4f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse), label = "g")
    Canvas(modifier) {
        val w = size.width * 0.62f
        val h = size.height * 0.92f
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        drawRoundRect(c.label.copy(alpha = 0.8f), Offset(left, top), androidx.compose.ui.geometry.Size(w, h), androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()), style = Stroke(2.dp.toPx()))
        drawLine(c.label.copy(alpha = 0.5f), Offset(left + w * 0.35f, top + h - 6.dp.toPx()), Offset(left + w * 0.65f, top + h - 6.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        val p = when (contact) {
            Contact.TopEdge -> Offset(left + w / 2, top)
            Contact.BottomEdge -> Offset(left + w / 2, top + h)
            Contact.SideEdge -> Offset(left + w, top + h / 2)
        }
        drawCircle(c.accent.copy(alpha = 0.25f * glow), 12.dp.toPx(), p)
        drawCircle(c.accent, 5.dp.toPx(), p)
    }
}

/** Little isometric view of the corners touched so far (gravity-up), with the live segment. */
@Composable
private fun WalkPreview(points: List<Vec3>, live: Vec3?, up: Vec3, modifier: Modifier) {
    val c = Datum.colors
    Box(modifier) {
        Canvas(Modifier.fillMaxSize().padding(18.dp)) {
            val all = points + listOfNotNull(live)
            if (all.isEmpty()) {
                // Hint: an empty box outline.
                val s = size.minDimension * 0.28f
                val cx = size.width / 2
                val cy = size.height / 2
                val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                drawRect(c.labelTertiary, Offset(cx - s, cy - s * 0.6f), androidx.compose.ui.geometry.Size(s * 2, s * 1.2f), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
                return@Canvas
            }
            val u = up.normalized()
            val e1 = Vec3.X.rejectFrom(u).normalized().let { if (it.length < 0.5) Vec3.Y.rejectFrom(u).normalized() else it }
            val e2 = u cross e1
            val yaw = Math.toRadians(35.0)
            val tilt = Math.toRadians(28.0)
            fun proj(p: Vec3): Offset {
                val x = p dot e1
                val y = p dot u
                val z = p dot e2
                val xr = x * cos(yaw) - z * sin(yaw)
                val zr = x * sin(yaw) + z * cos(yaw)
                val yr = y * cos(tilt) - zr * sin(tilt)
                return Offset(xr.toFloat(), -yr.toFloat())
            }
            val pp = all.map { proj(it) }
            val minX = pp.minOf { it.x }
            val maxX = pp.maxOf { it.x }
            val minY = pp.minOf { it.y }
            val maxY = pp.maxOf { it.y }
            val span = max(max(maxX - minX, maxY - minY), 0.05f)
            val scale = size.minDimension / span * 0.85f
            val cx = (minX + maxX) / 2
            val cy = (minY + maxY) / 2
            fun s(o: Offset) = Offset(size.width / 2 + (o.x - cx) * scale, size.height / 2 + (o.y - cy) * scale)
            val screen = pp.map { s(it) }
            for (i in 1 until points.size) drawLine(c.accent, screen[i - 1], screen[i], 3.dp.toPx(), StrokeCap.Round)
            if (live != null && points.isNotEmpty()) {
                drawLine(c.accent.copy(alpha = 0.7f), screen[points.size - 1], screen.last(), 2.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                drawCircle(c.accent.copy(alpha = 0.3f), 10.dp.toPx(), screen.last())
            }
            points.indices.forEach { i ->
                drawCircle(Color.White, 6.dp.toPx(), screen[i])
                drawCircle(if (i == 0) c.green else c.accent, 4.dp.toPx(), screen[i])
            }
        }
        if (points.isEmpty()) {
            DText("Your path appears here", Datum.type.footnote, c.labelSecondary, Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
    }
}

/** Dimensions from the corners captured so far (for the live list). */
private fun partialCorners(shape: ShapeKind, pts: List<Vec3>): List<DimEstimate> {
    val dims = when (shape) {
        ShapeKind.BOX -> listOf(Dim.L, Dim.W, Dim.H)
        ShapeKind.CYLINDER -> listOf(Dim.L, Dim.H)
        ShapeKind.SPHERE -> listOf(Dim.L)
    }
    return (1 until pts.size).mapNotNull { i -> dims.getOrNull(i - 1)?.let { DimEstimate(it, (pts[i] - pts[i - 1]).length, 0.0, EstimateSource.MOTION) } }
}

private fun computeCorners(shape: ShapeKind, caps: List<Pair<Vec3, Double>>, up: Vec3): List<DimEstimate> {
    val p = caps.map { it.first }
    val sig = caps.map { it.second }
    return when (shape) {
        ShapeKind.BOX -> {
            if (p.size < 4) return emptyList()
            val fit = BoxFitter.fromWalk(p[0], p[1], p[2], p[3])
            val skew = sin(Math.toRadians(fit.maxAngleErrorDeg))
            listOf(
                DimEstimate(Dim.L, fit.length, sqrt(sig[1] * sig[1] + (fit.length * skew).let { it * it }), EstimateSource.MOTION, "corners"),
                DimEstimate(Dim.W, fit.width, sqrt(sig[2] * sig[2] + (fit.width * skew).let { it * it }), EstimateSource.MOTION, "corners"),
                DimEstimate(Dim.H, fit.height, sqrt(sig[3] * sig[3] + (fit.height * skew).let { it * it }), EstimateSource.MOTION, "corners"),
            )
        }
        ShapeKind.CYLINDER -> {
            if (p.size < 3) return emptyList()
            val d = (p[1] - p[0]).length
            val h = abs((p[2] - p[1]) dot up.normalized())
            listOf(
                DimEstimate(Dim.L, d, max(sig[1], 0.003), EstimateSource.MOTION, "corners"),
                DimEstimate(Dim.H, h, max(sig[2], 0.003), EstimateSource.MOTION, "corners"),
            )
        }
        ShapeKind.SPHERE -> {
            if (p.size < 2) return emptyList()
            listOf(DimEstimate(Dim.L, (p[1] - p[0]).length, max(sig[1], 0.003), EstimateSource.MOTION, "corners"))
        }
    }
}
