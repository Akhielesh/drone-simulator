package com.akhielesh.datum.ui.screens.ar

import android.content.Context
import android.opengl.GLSurfaceView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.akhielesh.datum.ar.ArEngine
import com.akhielesh.datum.ar.ArFrameState
import com.akhielesh.datum.ar.LineRole
import com.akhielesh.datum.ar.ScreenPt
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.findActivity
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.EmptyState
import com.akhielesh.datum.ui.components.PillButton
import com.akhielesh.datum.ui.components.ProgressRing
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.screens.slide.pill
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.InterText
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Session
import kotlinx.coroutines.delay

/** How ARCore availability is checked; replaceable so UI tests don't bind to Play Services. */
val LocalArChecker = staticCompositionLocalOf<(Context) -> ArCoreApk.Availability?> {
    { ctx -> runCatching { ArCoreApk.getInstance().checkAvailability(ctx) }.getOrNull() }
}

/** ARCore availability for this device, re-checked while it's still being determined. */
@Composable
fun rememberArAvailability(): ArCoreApk.Availability? {
    val checker = LocalArChecker.current
    val context = LocalContext.current
    var availability by remember { mutableStateOf<ArCoreApk.Availability?>(null) }
    var resumes by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumes++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(resumes) {
        repeat(20) {
            val a = checker(context)
            availability = a
            if (a == null || !a.isTransient) return@LaunchedEffect
            delay(250)
        }
    }
    return availability
}

/**
 * Shows [content] once ARCore is installed and up to date; otherwise explains and offers the
 * one-tap install of Google Play Services for AR (free, from Google).
 */
@Composable
fun ArGate(fallbackHint: String, content: @Composable () -> Unit) {
    val availability = rememberArAvailability()
    val context = LocalContext.current
    var installError by remember { mutableStateOf<String?>(null) }
    val tool = Tool.ArMeasure
    when {
        availability == null || availability.isTransient -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(0.3f, Modifier.size(36.dp))
                Spacer(Modifier.height(10.dp))
                DText("Checking AR support…", Datum.type.callout, Datum.colors.labelSecondary)
            }
        }
        availability == ArCoreApk.Availability.SUPPORTED_INSTALLED -> content()
        availability.isSupported -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                tool.icon,
                "Install AR support",
                "Your phone supports AR. Google Play Services for AR is needed (or needs an update) to track the room.",
                action = {
                    PillButton("Install from Play Store", onClick = {
                        val activity = context.findActivity() ?: return@PillButton
                        runCatching { ArCoreApk.getInstance().requestInstall(activity, true) }
                            .onFailure { installError = it.message ?: "Installation was declined" }
                    })
                },
            )
            installError?.let { DText(it, Datum.type.footnote, Datum.colors.red, Modifier.align(Alignment.BottomCenter).padding(32.dp)) }
        }
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(DatumIcons.Info, "AR isn't available on this phone", fallbackHint)
        }
    }
}

/**
 * Hosts the ARCore session in a GLSurfaceView with the lifecycle ordering ARCore requires:
 * resume session → view; pause view → session (so the GL thread never touches a paused session).
 */
@Composable
fun ArSessionView(engine: ArEngine, modifier: Modifier = Modifier, onError: (String) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val glView = remember {
        GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(engine)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setWillNotDraw(false)
        }
    }
    DisposableEffect(owner) {
        var session: Session? = null
        fun resume() {
            if (session == null) {
                session = runCatching { Session(context) }.onFailure { onError("Couldn't start AR: ${it.javaClass.simpleName}") }.getOrNull()
                session?.let { engine.attach(it) }
            }
            val s = session ?: return
            runCatching { s.resume() }.onFailure {
                onError("Camera is busy — close other camera apps and try again")
                return
            }
            glView.onResume()
            engine.rotation.onResume()
        }
        fun pause() {
            engine.rotation.onPause()
            glView.onPause()
            session?.pause()
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) pause()
            engine.detach()
            session?.close()
            session = null
        }
    }
    AndroidView(factory = { glView }, modifier = modifier)
}

/** Measurement overlay: reticle, points, labelled lines and translucent faces. */
@Composable
fun ArOverlay(state: ArFrameState, units: UnitSystem, modifier: Modifier = Modifier) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val label = TextStyle(fontFamily = InterText, fontSize = 13.sp, color = Color.Black, fontFeatureSettings = "tnum")
    val pulse = rememberInfiniteTransition(label = "arPulse")
    val p by pulse.animateFloat(0.6f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "p")
    Canvas(modifier) {
        fun o(s: ScreenPt) = Offset(s.x, s.y)
        for (f in state.fills) {
            val path = Path().apply {
                moveTo(f[0].x, f[0].y)
                for (i in 1 until f.size) lineTo(f[i].x, f[i].y)
                close()
            }
            drawPath(path, c.accent.copy(alpha = 0.22f))
        }
        for (l in state.lines) {
            val a = o(l.a)
            val b = o(l.b)
            when (l.role) {
                LineRole.MEASURED -> {
                    drawLine(Color.Black.copy(alpha = 0.35f), a, b, 6.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, a, b, 3.dp.toPx(), StrokeCap.Round)
                }
                LineRole.LIVE -> {
                    drawLine(Color.Black.copy(alpha = 0.3f), a, b, 5.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, a, b, 2.5.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(18f, 12f)))
                }
                LineRole.EDGE -> drawLine(Color.White.copy(alpha = 0.85f), a, b, 2.dp.toPx(), StrokeCap.Round)
                LineRole.GUIDE -> drawLine(Color.White.copy(alpha = 0.6f), a, b, 1.5.dp.toPx(), StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
        }
        for (l in state.lines) {
            if (!l.label || l.lengthM < 0.005) continue
            val text = (if (l.tag != ' ') "${l.tag}  " else "") + Fmt.length(l.lengthM, units)
            val mid = Offset((l.a.x + l.b.x) / 2, (l.a.y + l.b.y) / 2)
            pill(measurer, text, label, Color.White, mid)
        }
        for (d in state.dots) {
            val r = if (d.live) 7.dp.toPx() * p else 7.dp.toPx()
            drawCircle(Color.Black.copy(alpha = 0.3f), r + 2.dp.toPx(), o(d.p))
            drawCircle(Color.White, r, o(d.p))
            drawCircle(c.accent, r * 0.55f, o(d.p))
        }
        val center = Offset(size.width / 2, size.height / 2)
        if (state.reticleValid && state.reticleRing.size > 2) {
            val ring = Path().apply {
                moveTo(state.reticleRing[0].x, state.reticleRing[0].y)
                for (i in 1 until state.reticleRing.size) lineTo(state.reticleRing[i].x, state.reticleRing[i].y)
                close()
            }
            drawPath(ring, Color.Black.copy(alpha = 0.3f), style = Stroke(5.dp.toPx()))
            drawPath(ring, Color.White, style = Stroke(2.5.dp.toPx()))
            drawCircle(Color.White, 3.5.dp.toPx(), center)
        } else {
            drawCircle(Color.White.copy(alpha = 0.5f * p), 26.dp.toPx(), center, style = Stroke(2.dp.toPx()))
        }
        val arm = 9.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.85f), Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), 1.5.dp.toPx())
        drawLine(Color.White.copy(alpha = 0.85f), Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), 1.5.dp.toPx())
    }
}
