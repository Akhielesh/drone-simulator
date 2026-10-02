package com.akhielesh.datum.ui.screens.home

import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.akhielesh.datum.LocalApp
import com.akhielesh.datum.core.data.Measurement
import com.akhielesh.datum.core.data.ThemeMode
import com.akhielesh.datum.core.math.toDegrees
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.ui.Tool
import com.akhielesh.datum.ui.common.LocalSettings
import com.akhielesh.datum.ui.common.isDarkNow
import com.akhielesh.datum.ui.common.rememberSensor
import com.akhielesh.datum.ui.components.CircleButton
import com.akhielesh.datum.ui.components.DText
import com.akhielesh.datum.ui.components.IconTile
import com.akhielesh.datum.ui.components.pressable
import com.akhielesh.datum.ui.icons.DIcon
import com.akhielesh.datum.ui.icons.DatumIcons
import com.akhielesh.datum.ui.nav.LocalNavigator
import com.akhielesh.datum.ui.nav.Route
import com.akhielesh.datum.ui.screens.library.formatMeasurement
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.Shapes
import com.akhielesh.datum.ui.theme.card
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Smoothed gravity direction for live tiles and parallax (x right, y up, z out of screen). */
private data class Grav(val x: Float, val y: Float, val z: Float)

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val nav = LocalNavigator.current
    val c = Datum.colors
    val gravity = rememberSensor(Sensor.TYPE_GRAVITY.takeIf { app.sensors.has(it) } ?: Sensor.TYPE_ACCELEROMETER, SensorManager.SENSOR_DELAY_UI) {
        val n = sqrt(it.x * it.x + it.y * it.y + it.z * it.z).coerceAtLeast(0.1f)
        Grav(it.x / n, it.y / n, it.z / n)
    }
    val heading = rememberSensor(Sensor.TYPE_ROTATION_VECTOR, SensorManager.SENSOR_DELAY_UI) { r ->
        val m = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(m, r.values)
        val o = FloatArray(3)
        SensorManager.getOrientation(m, o)
        ((o[0].toDouble().toDegrees() + 360) % 360).toFloat()
    }
    val pressure = rememberSensor(Sensor.TYPE_PRESSURE, 200_000) { it.x }
    val lux = rememberSensor(Sensor.TYPE_LIGHT, 200_000) { it.x }
    val field = rememberSensor(Sensor.TYPE_MAGNETIC_FIELD, 100_000) { sqrt(it.x * it.x + it.y * it.y + it.z * it.z) }
    val recent by app.library.items.collectAsStateWithLifecycle()
    val device = app.device
    val dark = isDarkNow()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 18.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                DText("Datum", Datum.type.largeTitle)
                DText(
                    device.marketingName + if (device.isTunedGalaxy) " · tuned profile" else "",
                    Datum.type.subhead, c.labelSecondary, maxLines = 1,
                )
            }
            CircleButton(
                if (dark) DatumIcons.Sun else DatumIcons.Moon,
                onClick = { app.settings.update { it.copy(themeMode = if (dark) ThemeMode.LIGHT else ThemeMode.DARK) } },
                size = 40.dp,
            )
            Spacer(Modifier.width(10.dp))
            CircleButton(DatumIcons.Settings, onClick = { nav.push(Route.Settings) }, size = 40.dp)
        }
        Spacer(Modifier.height(18.dp))

        LiveStrip(gravity, heading, pressure, onClick = { nav.switchTo(Route.Level) })

        SectionLabel("Measure")
        ObjectHero(gravity) { nav.switchTo(Route.ObjectHub) }
        Spacer(Modifier.height(12.dp))
        TileRow(gravity, Tool.ArMeasure, Tool.Slide, { nav.push(Route.ArMeasure) }, { nav.switchTo(Route.Slide) })
        TileRow(gravity, Tool.Height, Tool.Ruler, { nav.switchTo(Route.Height) }, { nav.push(Route.Ruler) },
            liveA = { pressure.value?.let { LiveCaption("%.1f hPa".format(it)) } },
            liveB = { MiniRuler() })

        SectionLabel("Align")
        TileRow(gravity, Tool.Level, Tool.Angle, { nav.switchTo(Route.Level) }, { nav.push(Route.Angle) },
            liveA = { MiniBubble(gravity) },
            liveB = { gravity.value?.let { g -> LiveCaption("%.0f°".format(Math.toDegrees(kotlin.math.asin(g.y.coerceIn(-1f, 1f).toDouble())))) } })
        TileRow(gravity, Tool.Compass, Tool.Stud, { nav.push(Route.Compass) }, { nav.push(Route.Stud) },
            liveA = { MiniCompass(heading) },
            liveB = { field.value?.let { LiveCaption("%.0f µT".format(it)) } })

        SectionLabel("Sense")
        TileRow(gravity, Tool.Light, Tool.Sound, { nav.push(Route.Light) }, { nav.push(Route.Sound) },
            liveA = { lux.value?.let { LiveCaption(Fmt.lux(it.toDouble()).toString()) } })
        TileRow(gravity, Tool.Vibration, Tool.Sensors, { nav.push(Route.Vibration) }, { nav.push(Route.Sensors) },
            liveB = { LiveCaption("${app.sensors.allSensors().size} sensors") })

        SectionLabel("Library")
        RecentStrip(recent.take(8), onOpen = { nav.push(Route.LibraryDetail(it.id)) }, onAll = { nav.push(Route.Library) })

        Spacer(Modifier.height(140.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    DText(
        text.uppercase(), Datum.type.sectionLabel, Datum.colors.labelSecondary,
        Modifier.padding(start = 4.dp, top = 26.dp, bottom = 10.dp),
    )
}

@Composable
private fun LiveCaption(text: String) {
    DText(text, Datum.type.mono, Datum.colors.labelSecondary, maxLines = 1)
}

/** "Right now" widget: live tilt, heading and pressure in one glanceable strip. */
@Composable
private fun LiveStrip(gravity: State<Grav?>, heading: State<Float?>, pressure: State<Float?>, onClick: () -> Unit) {
    val c = Datum.colors
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .card(Shapes.xl, elevated = true)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp)) { MiniBubble(gravity, big = true) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            DText("RIGHT NOW", Datum.type.sectionLabel, c.labelSecondary)
            val g = gravity.value
            val tilt = if (g != null) Math.toDegrees(kotlin.math.acos(kotlin.math.abs(g.z).toDouble().coerceIn(0.0, 1.0))) else 0.0
            DText(
                if (g == null) "Reading sensors…" else if (tilt < 0.3) "Perfectly level" else "Tilted %.1f°".format(tilt),
                Datum.type.title3, if (g != null && tilt < 0.3) c.green else c.label,
            )
            val parts = buildList {
                heading.value?.let { add("Heading %.0f° %s".format(it, cardinal(it))) }
                pressure.value?.let { add("%.0f hPa".format(it)) }
            }
            if (parts.isNotEmpty()) DText(parts.joinToString("  ·  "), Datum.type.footnote, c.labelSecondary, maxLines = 1)
        }
        DIcon(DatumIcons.ChevronRight, tint = c.labelTertiary, size = 16.dp)
    }
}

fun cardinal(deg: Float): String {
    val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    return dirs[(((deg + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)]
}

@Composable
private fun TileRow(
    gravity: State<Grav?>,
    a: Tool,
    b: Tool,
    onA: () -> Unit,
    onB: () -> Unit,
    liveA: (@Composable BoxScope.() -> Unit)? = null,
    liveB: (@Composable BoxScope.() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolTile(a, gravity, onA, Modifier.weight(1f), liveA)
        ToolTile(b, gravity, onB, Modifier.weight(1f), liveB)
    }
}

/** A launch tile with subtle tilt parallax and an optional live reading in its corner. */
@Composable
private fun ToolTile(
    tool: Tool,
    gravity: State<Grav?>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    live: (@Composable BoxScope.() -> Unit)? = null,
) {
    val c = Datum.colors
    Box(
        modifier
            .height(132.dp)
            .pressable(onClick = onClick)
            .card(Shapes.xl)
            .padding(14.dp),
    ) {
        IconTile(
            tool.icon, tool.colors, size = 42.dp,
            modifier = Modifier.graphicsLayer {
                val g = gravity.value
                translationX = (g?.x ?: 0f) * -5f * density
                translationY = (g?.y ?: 0f) * 5f * density
            },
        )
        if (live != null) {
            Box(Modifier.align(Alignment.TopEnd).padding(top = 2.dp), contentAlignment = Alignment.TopEnd, content = live)
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            DText(tool.title, Datum.type.headline, maxLines = 1)
            DText(tool.tagline, Datum.type.footnote, c.labelSecondary, maxLines = 2)
        }
    }
}

/** Large Object tile with a spinning wireframe box. */
@Composable
private fun ObjectHero(gravity: State<Grav?>, onClick: () -> Unit) {
    val tool = Tool.Object
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing)), label = "a")
    Row(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .pressable(onClick = onClick)
            .clip(Shapes.xl)
            .background(Brush.linearGradient(listOf(tool.colors[0], tool.colors[1], Color(0xFF3B2BB8))))
            .padding(18.dp),
    ) {
        Column(Modifier.weight(1f).fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            IconTile(tool.icon, listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.14f)), size = 42.dp)
            Column {
                DText("Measure an object", Datum.type.title3, Color.White)
                DText("Walk its corners or edges, add photos — get a 3D model with every metric.", Datum.type.footnote, Color.White.copy(alpha = 0.82f), maxLines = 3)
            }
        }
        Canvas(
            Modifier
                .size(118.dp)
                .align(Alignment.CenterVertically)
                .graphicsLayer {
                    val g = gravity.value
                    rotationY = (g?.x ?: 0f) * 10f
                    rotationX = (g?.y ?: 0f) * -10f
                },
        ) {
            val yaw = Math.toRadians(angle.toDouble())
            val pitch = Math.toRadians(-24.0)
            val dims = floatArrayOf(1.25f, 0.85f, 0.95f)
            val pts = Array(8) { i ->
                val x = (if (i and 1 == 0) -1 else 1) * dims[0] / 2
                val y = (if (i and 2 == 0) -1 else 1) * dims[2] / 2
                val z = (if (i and 4 == 0) -1 else 1) * dims[1] / 2
                val x1 = x * cos(yaw) - z * sin(yaw)
                val z1 = x * sin(yaw) + z * cos(yaw)
                val y2 = y * cos(pitch) - z1 * sin(pitch)
                val z2 = y * sin(pitch) + z1 * cos(pitch)
                val persp = 3.2 / (3.2 + z2)
                Offset(
                    (size.width / 2 + x1 * persp * size.width * 0.36).toFloat(),
                    (size.height / 2 - y2 * persp * size.width * 0.36).toFloat(),
                )
            }
            val edges = listOf(0 to 1, 2 to 3, 4 to 5, 6 to 7, 0 to 2, 1 to 3, 4 to 6, 5 to 7, 0 to 4, 1 to 5, 2 to 6, 3 to 7)
            val top = Path().apply {
                moveTo(pts[2].x, pts[2].y); lineTo(pts[3].x, pts[3].y); lineTo(pts[7].x, pts[7].y); lineTo(pts[6].x, pts[6].y); close()
            }
            drawPath(top, Color.White.copy(alpha = 0.16f))
            for ((a, b) in edges) drawLine(Color.White.copy(alpha = 0.9f), pts[a], pts[b], 2.2f, StrokeCap.Round)
            for (p in pts) drawCircle(Color.White, 3.2f, p)
        }
    }
}

/** Miniature live bubble level. */
@Composable
private fun MiniBubble(gravity: State<Grav?>, big: Boolean = false) {
    val c = Datum.colors
    Canvas(Modifier.size(if (big) 64.dp else 38.dp)) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        val g = gravity.value
        val level = g != null && kotlin.math.abs(g.z) > 0.9998f
        drawCircle(if (level) c.green.copy(alpha = 0.16f) else c.fillTertiary, r, center)
        drawCircle(c.separator, r * 0.42f, center, style = Stroke(1.dp.toPx()))
        drawLine(c.separator, Offset(center.x - r * 0.8f, center.y), Offset(center.x + r * 0.8f, center.y), 1.dp.toPx())
        drawLine(c.separator, Offset(center.x, center.y - r * 0.8f), Offset(center.x, center.y + r * 0.8f), 1.dp.toPx())
        if (g != null) {
            val k = r * 2.4f
            val bx = (g.x * k).coerceIn(-r * 0.62f, r * 0.62f)
            val by = (-g.y * k).coerceIn(-r * 0.62f, r * 0.62f)
            val p = Offset(center.x + bx, center.y + by)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White, if (level) c.green else c.accent),
                    center = Offset(p.x - r * 0.08f, p.y - r * 0.08f), radius = r * 0.36f,
                ),
                r * 0.3f, p,
            )
        }
    }
}

@Composable
private fun MiniCompass(heading: State<Float?>) {
    val c = Datum.colors
    Canvas(Modifier.size(38.dp)) {
        val r = size.minDimension / 2
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(c.fillTertiary, r, center)
        val h = heading.value ?: 0f
        rotate(-h, center) {
            val needle = Path().apply {
                moveTo(center.x, center.y - r * 0.78f)
                lineTo(center.x + r * 0.18f, center.y)
                lineTo(center.x - r * 0.18f, center.y)
                close()
            }
            drawPath(needle, c.red)
            val tail = Path().apply {
                moveTo(center.x, center.y + r * 0.78f)
                lineTo(center.x + r * 0.18f, center.y)
                lineTo(center.x - r * 0.18f, center.y)
                close()
            }
            drawPath(tail, c.labelTertiary)
        }
    }
}

/** A few millimetres of real ruler, drawn at true physical scale. */
@Composable
private fun MiniRuler() {
    val app = LocalApp.current
    val c = Datum.colors
    val ppi = app.physicalPpi
    Canvas(Modifier.size(width = 64.dp, height = 22.dp)) {
        val pxPerMm = (ppi / 25.4).toFloat()
        var mm = 0
        while (mm * pxPerMm <= size.width) {
            val x = mm * pxPerMm
            val h = when {
                mm % 10 == 0 -> size.height
                mm % 5 == 0 -> size.height * 0.62f
                else -> size.height * 0.38f
            }
            drawLine(c.labelSecondary, Offset(x, 0f), Offset(x, h), 1.2f)
            mm++
        }
    }
}

@Composable
private fun RecentStrip(items: List<Measurement>, onOpen: (Measurement) -> Unit, onAll: () -> Unit) {
    val c = Datum.colors
    val settings = LocalSettings.current
    if (items.isEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onClick = onAll)
                .card(Shapes.xl)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Tool.Library.icon, Tool.Library.colors, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                DText("Nothing saved yet", Datum.type.headline)
                DText("Measurements you save appear here.", Datum.type.footnote, c.labelSecondary)
            }
        }
        return
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { m ->
            Column(
                Modifier
                    .width(150.dp)
                    .pressable { onOpen(m) }
                    .card(Shapes.lg)
                    .padding(14.dp),
            ) {
                DText(m.kind.label.uppercase(), Datum.type.sectionLabel, c.labelSecondary, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                DText(formatMeasurement(m, settings.units), Datum.type.title3, maxLines = 1)
                DText(m.title, Datum.type.footnote, c.labelSecondary, maxLines = 1)
            }
        }
        Column(
            Modifier
                .width(110.dp)
                .height(92.dp)
                .pressable(onClick = onAll)
                .card(Shapes.lg)
                .padding(14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DIcon(DatumIcons.Library, tint = c.accent)
            Spacer(Modifier.height(6.dp))
            DText("See all", Datum.type.subhead, c.accent)
        }
    }
}
