package com.akhielesh.datum.ui.screens.objects

import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akhielesh.datum.core.data.Face
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.core.units.Fmt
import com.akhielesh.datum.core.units.UnitSystem
import com.akhielesh.datum.ui.screens.slide.pill
import com.akhielesh.datum.ui.theme.Datum
import com.akhielesh.datum.ui.theme.DatumColors
import com.akhielesh.datum.ui.theme.InterText
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class RenderStyle { Solid, Blueprint, XRay }

enum class ViewPreset(val label: String, val yaw: Float, val pitch: Float, val ortho: Boolean) {
    Iso("3D", -36f, -24f, false),
    Front("Front", 0f, 0f, true),
    Side("Side", -90f, 0f, true),
    Top("Top", 0f, -89.5f, true),
}

/** Orbit camera with spring-animated presets, fling inertia and a perspective↔orthographic blend. */
@Stable
class OrbitCamera {
    val yaw = Animatable(ViewPreset.Iso.yaw)
    val pitch = Animatable(ViewPreset.Iso.pitch)
    val zoom = Animatable(1f)

    /** True while a finger is on the viewport (pauses the turntable). */
    var interacting by mutableStateOf(false)

    /** Camera distance in model units; large = near-orthographic. */
    val distance = Animatable(3.4f)

    suspend fun goTo(p: ViewPreset) = coroutineScope {
        val targetYaw = nearestAngle(yaw.value, p.yaw)
        launch { yaw.animateTo(targetYaw, spring(dampingRatio = 0.85f, stiffness = 140f)) }
        launch { pitch.animateTo(p.pitch, spring(dampingRatio = 0.85f, stiffness = 140f)) }
        launch { distance.animateTo(if (p.ortho) 40f else 3.4f, spring(dampingRatio = 1f, stiffness = 90f)) }
        launch { zoom.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 160f)) }
    }

    private fun nearestAngle(from: Float, to: Float): Float {
        var t = to
        while (t - from > 180f) t -= 360f
        while (t - from < -180f) t += 360f
        return t
    }
}

/** Material appearance for the solid render. */
fun materialColor(material: String, c: DatumColors): Color = when {
    material.startsWith("Cardboard") -> Color(0xFFC9A26E)
    material in setOf("Pine", "Oak", "Plywood", "MDF") -> Color(0xFFC79A66)
    material in setOf("Aluminium", "Steel") -> Color(0xFFAFB6BE)
    material == "Glass" || material == "Ice" || material == "Water" -> Color(0xFF8FD3E8)
    material == "Concrete" || material == "Marble" -> Color(0xFFBDB8B0)
    material == "Gold" -> Color(0xFFE5B84B)
    material == "EPS foam" -> Color(0xFFF2F2EE)
    else -> lerp(c.accent, Color.White, 0.55f)
}

private class Projected(val p: Offset, val depth: Float)

/** Projection for one frame: world (model units, y up) → screen. */
private class Projector(
    yawDeg: Float, pitchDeg: Float,
    private val dist: Float,
    private val f: Float,
    private val cx: Float,
    private val cy: Float,
    private val center: Vec3,
) {
    private val cyw = cos(Math.toRadians(yawDeg.toDouble()))
    private val syw = sin(Math.toRadians(yawDeg.toDouble()))
    private val cp = cos(Math.toRadians(pitchDeg.toDouble()))
    private val sp = sin(Math.toRadians(pitchDeg.toDouble()))

    /** Rotate into view space (x right, y up, z toward the viewer). */
    fun view(v: Vec3): Vec3 {
        val x = v.x - center.x
        val y = v.y - center.y
        val z = v.z - center.z
        val x1 = x * cyw + z * syw
        val z1 = -x * syw + z * cyw
        val y2 = y * cp + z1 * sp
        val z2 = -y * sp + z1 * cp
        return Vec3(x1, y2, z2)
    }

    fun project(v: Vec3): Projected {
        val q = view(v)
        val depth = (dist - q.z).coerceAtLeast(0.05)
        return Projected(Offset((cx + f * q.x / depth).toFloat(), (cy - f * q.y / depth).toFloat()), depth.toFloat())
    }

    fun facing(faceCenter: Vec3, normal: Vec3): Boolean {
        val c = view(faceCenter)
        val n = view(normal + center) // rotate direction only
        val eye = Vec3(0.0, 0.0, dist.toDouble())
        return (n dot (eye - c)) > 1e-6
    }
}

private data class FaceDef(val face: Face, val corners: List<Vec3>, val normal: Vec3)

/**
 * Interactive 3D view of a measured object. Dimensions are in metres; the model is normalised so
 * its longest side spans the viewport regardless of absolute size.
 */
@Composable
fun ObjectViewport(
    shape: ShapeKind,
    length: Double,
    width: Double,
    height: Double,
    camera: OrbitCamera,
    modifier: Modifier = Modifier,
    style: RenderStyle = RenderStyle.Solid,
    material: String = "",
    textures: Map<Face, ImageBitmap> = emptyMap(),
    units: UnitSystem = UnitSystem.METRIC,
    showDimensions: Boolean = true,
    labels: Triple<String, String, String>? = null,
    interactive: Boolean = true,
) {
    val c = Datum.colors
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = InterText, fontSize = 12.sp, color = Color.White, fontFeatureSettings = "tnum")
    val scope = rememberCoroutineScope()
    val base = materialColor(material, c)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val matrix = remember { Matrix() }

    var gestureMod = modifier
    if (interactive) {
        gestureMod = gestureMod
            .pointerInput(camera) {
                detectTapGestures(onDoubleTap = { scope.launch { camera.goTo(ViewPreset.Iso) } })
            }
            .pointerInput(camera) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    camera.interacting = true
                    val tracker = VelocityTracker()
                    do {
                        val event = awaitPointerEvent()
                        val zoomChange = event.calculateZoom()
                        val pan = event.calculatePan()
                        val pointers = event.changes.count { it.pressed }
                        if (pointers >= 2 && event.calculateCentroidSize() > 0f) {
                            scope.launch { camera.zoom.snapTo((camera.zoom.value * zoomChange).coerceIn(0.45f, 3f)) }
                        } else if (pan != Offset.Zero) {
                            val ch: PointerInputChange = event.changes.first()
                            tracker.addPosition(ch.uptimeMillis, ch.position)
                            scope.launch {
                                camera.yaw.snapTo(camera.yaw.value + pan.x * 0.35f)
                                camera.pitch.snapTo((camera.pitch.value - pan.y * 0.3f).coerceIn(-89.5f, 89.5f))
                            }
                        }
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    } while (event.changes.any { it.pressed })
                    camera.interacting = false
                    val v = tracker.calculateVelocity()
                    scope.launch { camera.yaw.animateDecay(v.x * 0.35f, exponentialDecay(frictionMultiplier = 2.2f)) }
                }
            }
    }

    Canvas(gestureMod) {
        val l = length
        val w = if (shape == ShapeKind.BOX) width else length
        val h = if (shape == ShapeKind.SPHERE) length else height
        if (l <= 0 || w <= 0 || h <= 0) return@Canvas
        val norm = 1.0 / max(l, max(w, h))
        val nl = l * norm
        val nw = w * norm
        val nh = h * norm
        val radius = 0.5 * sqrt(nl * nl + nw * nw + nh * nh)
        val dist = camera.distance.value
        val fit = min(size.width, size.height) * 0.36f * camera.zoom.value
        val f = (fit * (dist - radius) / radius).toFloat()
        val proj = Projector(camera.yaw.value, camera.pitch.value, dist, f, size.width / 2, size.height * 0.52f, Vec3(0.0, nh / 2, 0.0))

        drawGroundShadow(proj, nl, nw, shape, style, c)
        when (shape) {
            ShapeKind.BOX -> drawBox(proj, nl, nw, nh, style, base, c, textures, paint, matrix)
            ShapeKind.CYLINDER -> drawCylinder(proj, nl, nh, style, base, c)
            ShapeKind.SPHERE -> drawSphere(proj, nl, style, base, c)
        }
        if (showDimensions) {
            val lab = labels ?: Triple(
                Fmt.lengthShort(l, units), Fmt.lengthShort(w, units), Fmt.lengthShort(h, units),
            )
            when (shape) {
                ShapeKind.BOX -> drawBoxDimensions(proj, nl, nw, nh, lab, measurer, labelStyle, c)
                ShapeKind.CYLINDER -> drawCylinderDimensions(proj, nl, nh, lab, measurer, labelStyle, c)
                ShapeKind.SPHERE -> {
                    val ctr = proj.project(Vec3(0.0, nl / 2, 0.0)).p
                    val a = proj.project(Vec3(-nl / 2, nl / 2, 0.0)).p
                    val b = proj.project(Vec3(nl / 2, nl / 2, 0.0)).p
                    val r = abs(b.x - a.x) / 2
                    // Diameter callout just below the silhouette.
                    dimensionLine(
                        Offset(ctr.x - r, ctr.y), Offset(ctr.x + r, ctr.y), Offset(0f, 1f),
                        "Ø " + lab.first, c.accent, measurer, labelStyle, outward = r / density + 18f,
                    )
                }
            }
        }
    }
}

private fun boxFaces(l: Double, w: Double, h: Double): List<FaceDef> {
    val x0 = -l / 2
    val x1 = l / 2
    val z0 = -w / 2
    val z1 = w / 2
    fun v(x: Double, y: Double, z: Double) = Vec3(x, y, z)
    return listOf(
        FaceDef(Face.FRONT, listOf(v(x0, h, z1), v(x1, h, z1), v(x1, 0.0, z1), v(x0, 0.0, z1)), Vec3.Z),
        FaceDef(Face.BACK, listOf(v(x1, h, z0), v(x0, h, z0), v(x0, 0.0, z0), v(x1, 0.0, z0)), -Vec3.Z),
        FaceDef(Face.RIGHT, listOf(v(x1, h, z1), v(x1, h, z0), v(x1, 0.0, z0), v(x1, 0.0, z1)), Vec3.X),
        FaceDef(Face.LEFT, listOf(v(x0, h, z0), v(x0, h, z1), v(x0, 0.0, z1), v(x0, 0.0, z0)), -Vec3.X),
        FaceDef(Face.TOP, listOf(v(x0, h, z0), v(x1, h, z0), v(x1, h, z1), v(x0, h, z1)), Vec3.Y),
        FaceDef(Face.BOTTOM, listOf(v(x0, 0.0, z1), v(x1, 0.0, z1), v(x1, 0.0, z0), v(x0, 0.0, z0)), -Vec3.Y),
    )
}

private val LIGHT = Vec3(-0.45, 0.8, 0.55).normalized()

private fun shade(base: Color, n: Vec3): Color {
    val k = (0.6 + 0.4 * max(0.0, n dot LIGHT)).toFloat()
    return Color(base.red * k, base.green * k, base.blue * k, base.alpha)
}

private fun DrawScope.drawGroundShadow(proj: Projector, l: Double, w: Double, shape: ShapeKind, style: RenderStyle, c: DatumColors) {
    if (style == RenderStyle.Blueprint) {
        // Faint floor grid.
        val step = 0.25
        for (i in -6..6) {
            val a = proj.project(Vec3(i * step, 0.0, -1.5)).p
            val b = proj.project(Vec3(i * step, 0.0, 1.5)).p
            drawLine(c.accent.copy(alpha = 0.10f), a, b, 1f)
            val d = proj.project(Vec3(-1.5, 0.0, i * step)).p
            val e = proj.project(Vec3(1.5, 0.0, i * step)).p
            drawLine(c.accent.copy(alpha = 0.10f), d, e, 1f)
        }
        return
    }
    val r = if (shape == ShapeKind.BOX) max(l, w) * 0.75 else l * 0.62
    val pts = (0 until 24).map { i ->
        val a = 2 * PI * i / 24
        proj.project(Vec3(cos(a) * r * (if (shape == ShapeKind.BOX) l / max(l, w) else 1.0), 0.0, sin(a) * r * (if (shape == ShapeKind.BOX) w / max(l, w) else 1.0))).p
    }
    val minX = pts.minOf { it.x }
    val maxX = pts.maxOf { it.x }
    val minY = pts.minOf { it.y }
    val maxY = pts.maxOf { it.y }
    val center = Offset((minX + maxX) / 2, (minY + maxY) / 2 + 6.dp.toPx())
    val rx = (maxX - minX) / 2 * 1.1f
    val ry = max((maxY - minY) / 2 * 1.1f, 10.dp.toPx())
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = if (c.isDark) 0.55f else 0.22f), Color.Transparent), center, max(rx, ry)),
        topLeft = Offset(center.x - rx, center.y - ry),
        size = androidx.compose.ui.geometry.Size(rx * 2, ry * 2),
    )
}

private fun DrawScope.drawBox(
    proj: Projector,
    l: Double, w: Double, h: Double,
    style: RenderStyle,
    base: Color,
    c: DatumColors,
    textures: Map<Face, ImageBitmap>,
    paint: Paint,
    matrix: Matrix,
) {
    val faces = boxFaces(l, w, h)
    val stroke = 1.6.dp.toPx()
    val edge = if (style == RenderStyle.Solid) c.label.copy(alpha = 0.55f) else c.accent
    // Back faces first (dashed hidden edges for blueprint / translucent for x-ray).
    for (f in faces) {
        val center = f.corners.reduce { a, b -> a + b } * 0.25
        if (proj.facing(center, f.normal)) continue
        val path = facePath(f.corners.map { proj.project(it).p })
        when (style) {
            RenderStyle.XRay -> {
                drawPath(path, c.accent.copy(alpha = 0.10f))
                drawPath(path, c.accent.copy(alpha = 0.55f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))))
            }
            RenderStyle.Blueprint -> drawPath(path, c.accent.copy(alpha = 0.45f), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f))))
            RenderStyle.Solid -> Unit
        }
    }
    for (f in faces) {
        val center = f.corners.reduce { a, b -> a + b } * 0.25
        if (!proj.facing(center, f.normal)) continue
        val screen = f.corners.map { proj.project(it).p }
        val path = facePath(screen)
        when (style) {
            RenderStyle.Solid -> {
                val tex = textures[f.face]
                if (tex != null) {
                    drawTexture(tex, screen, path, paint, matrix)
                    val k = (0.6 + 0.4 * max(0.0, f.normal dot LIGHT)).toFloat()
                    drawPath(path, Color.Black.copy(alpha = (1 - k) * 0.6f))
                } else {
                    val col = shade(base, f.normal)
                    drawPath(path, Brush.linearGradient(listOf(col, lerp(col, Color.White, 0.12f)), screen[3], screen[1]))
                }
            }
            RenderStyle.XRay -> drawPath(path, c.accent.copy(alpha = 0.16f))
            RenderStyle.Blueprint -> drawPath(path, c.accent.copy(alpha = 0.06f))
        }
        drawPath(path, edge, style = Stroke(stroke, join = StrokeJoin.Round))
    }
}

private fun facePath(p: List<Offset>) = Path().apply {
    moveTo(p[0].x, p[0].y)
    for (i in 1 until p.size) lineTo(p[i].x, p[i].y)
    close()
}

/** Perspective-correct texture: the homography of a projected planar quad is exactly a 4-point poly-to-poly map. */
private fun DrawScope.drawTexture(tex: ImageBitmap, quad: List<Offset>, clip: Path, paint: Paint, matrix: Matrix) {
    val bmp = tex.asAndroidBitmap()
    val src = floatArrayOf(0f, 0f, bmp.width.toFloat(), 0f, bmp.width.toFloat(), bmp.height.toFloat(), 0f, bmp.height.toFloat())
    val dst = floatArrayOf(quad[0].x, quad[0].y, quad[1].x, quad[1].y, quad[2].x, quad[2].y, quad[3].x, quad[3].y)
    if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) return
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        nc.save()
        nc.clipPath(clip.asAndroidPath())
        nc.drawBitmap(bmp, matrix, paint)
        nc.restore()
    }
}

private fun DrawScope.drawCylinder(proj: Projector, d: Double, h: Double, style: RenderStyle, base: Color, c: DatumColors) {
    val n = 72
    val r = d / 2
    val bottom = (0 until n).map { i -> val a = 2 * PI * i / n; Vec3(cos(a) * r, 0.0, sin(a) * r) }
    val top = bottom.map { Vec3(it.x, h, it.z) }
    val pb = bottom.map { proj.project(it).p }
    val pt = top.map { proj.project(it).p }
    val edge = if (style == RenderStyle.Solid) c.label.copy(alpha = 0.55f) else c.accent
    for (i in 0 until n) {
        val j = (i + 1) % n
        val mid = (bottom[i] + bottom[j]) * 0.5 + Vec3(0.0, h / 2, 0.0)
        val normal = Vec3(mid.x, 0.0, mid.z).normalized()
        val visible = proj.facing(mid, normal)
        val quad = facePath(listOf(pt[i], pt[j], pb[j], pb[i]))
        when (style) {
            RenderStyle.Solid -> if (visible) {
                val col = shade(base, normal)
                drawPath(quad, col)
                drawPath(quad, col, style = Stroke(1f))
            }
            RenderStyle.XRay -> drawPath(quad, c.accent.copy(alpha = if (visible) 0.05f else 0.03f))
            RenderStyle.Blueprint -> Unit
        }
    }
    val capVisible = proj.facing(Vec3(0.0, h, 0.0), Vec3.Y)
    val capPath = facePath(if (capVisible) pt else pb)
    if (style == RenderStyle.Solid) drawPath(capPath, shade(base, if (capVisible) Vec3.Y else -Vec3.Y))
    else drawPath(capPath, c.accent.copy(alpha = 0.08f))
    // Outlines: rims and the two silhouette lines.
    drawPath(facePath(pt), edge, style = Stroke(1.6.dp.toPx()))
    val bottomPath = Path()
    for (i in 0 until n) {
        val j = (i + 1) % n
        val mid = (bottom[i] + bottom[j]) * 0.5
        val vis = proj.facing(mid + Vec3(0.0, 0.0001, 0.0), Vec3(mid.x, 0.0, mid.z).normalized())
        if (vis) {
            bottomPath.moveTo(pb[i].x, pb[i].y)
            bottomPath.lineTo(pb[j].x, pb[j].y)
        }
    }
    drawPath(bottomPath, edge, style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
    val leftmost = (0 until n).minBy { pb[it].x + pt[it].x }
    val rightmost = (0 until n).maxBy { pb[it].x + pt[it].x }
    drawLine(edge, pt[leftmost], pb[leftmost], 1.6.dp.toPx())
    drawLine(edge, pt[rightmost], pb[rightmost], 1.6.dp.toPx())
}

private fun DrawScope.drawSphere(proj: Projector, d: Double, style: RenderStyle, base: Color, c: DatumColors) {
    val center = proj.project(Vec3(0.0, d / 2, 0.0))
    val edgeP = proj.project(Vec3(d / 2, d / 2, 0.0))
    val r = abs(edgeP.p.x - center.p.x).coerceAtLeast(4f)
    if (style == RenderStyle.Solid) {
        drawCircle(
            Brush.radialGradient(
                listOf(lerp(base, Color.White, 0.55f), base, Color(base.red * 0.55f, base.green * 0.55f, base.blue * 0.55f)),
                Offset(center.p.x - r * 0.35f, center.p.y - r * 0.4f), r * 1.5f,
            ),
            r, center.p,
        )
    } else {
        drawCircle(c.accent.copy(alpha = 0.10f), r, center.p)
    }
    val n = 64
    val equator = Path()
    for (i in 0..n) {
        val a = 2 * PI * i / n
        val p = proj.project(Vec3(cos(a) * d / 2, d / 2, sin(a) * d / 2)).p
        if (i == 0) equator.moveTo(p.x, p.y) else equator.lineTo(p.x, p.y)
    }
    val line = if (style == RenderStyle.Solid) c.label.copy(alpha = 0.35f) else c.accent
    drawPath(equator, line, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
    drawCircle(line, r, center.p, style = Stroke(1.6.dp.toPx()))
}

/** Dimension line offset outward from the object, with extension lines, end ticks and a label pill. */
private fun DrawScope.dimensionLine(
    a: Offset,
    b: Offset,
    outwardDir: Offset,
    text: String,
    color: Color,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    outward: Float = 22f,
) {
    val off = Offset(outwardDir.x * outward.dp.toPx(), outwardDir.y * outward.dp.toPx())
    val a2 = a + off
    val b2 = b + off
    if (outward > 0f) {
        drawLine(color.copy(alpha = 0.45f), a, a2 + off * 0.25f, 1.dp.toPx())
        drawLine(color.copy(alpha = 0.45f), b, b2 + off * 0.25f, 1.dp.toPx())
    }
    drawLine(color, a2, b2, 1.6.dp.toPx(), StrokeCap.Round)
    val dir = (b2 - a2).let { val len = it.getDistance().coerceAtLeast(1f); Offset(it.x / len, it.y / len) }
    val perp = Offset(-dir.y, dir.x) * 5.dp.toPx()
    drawLine(color, a2 - perp, a2 + perp, 1.6.dp.toPx(), StrokeCap.Round)
    drawLine(color, b2 - perp, b2 + perp, 1.6.dp.toPx(), StrokeCap.Round)
    pill(measurer, text, style, color, (a2 + b2) / 2f)
}

private fun DrawScope.drawBoxDimensions(
    proj: Projector,
    l: Double, w: Double, h: Double,
    labels: Triple<String, String, String>,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    c: DatumColors,
) {
    val x0 = -l / 2
    val x1 = l / 2
    val z0 = -w / 2
    val z1 = w / 2
    val center = proj.project(Vec3(0.0, h / 2, 0.0)).p
    fun outward(a: Offset, b: Offset): Offset {
        val mid = (a + b) / 2f
        val d = mid - center
        val len = d.getDistance().coerceAtLeast(1f)
        return Offset(d.x / len, d.y / len)
    }
    // Length: the X-parallel edge lowest on screen.
    val lengthEdges = listOf(
        Vec3(x0, 0.0, z1) to Vec3(x1, 0.0, z1), Vec3(x0, 0.0, z0) to Vec3(x1, 0.0, z0),
        Vec3(x0, h, z1) to Vec3(x1, h, z1), Vec3(x0, h, z0) to Vec3(x1, h, z0),
    ).map { (a, b) -> proj.project(a).p to proj.project(b).p }
    val (la, lb) = lengthEdges.maxBy { (it.first.y + it.second.y) }
    dimensionLine(la, lb, outward(la, lb), labels.first, c.accent, measurer, style)
    // Width: the Z-parallel edge lowest on screen.
    val widthEdges = listOf(
        Vec3(x1, 0.0, z0) to Vec3(x1, 0.0, z1), Vec3(x0, 0.0, z0) to Vec3(x0, 0.0, z1),
        Vec3(x1, h, z0) to Vec3(x1, h, z1), Vec3(x0, h, z0) to Vec3(x0, h, z1),
    ).map { (a, b) -> proj.project(a).p to proj.project(b).p }
    val (wa, wb) = widthEdges.maxBy { (it.first.y + it.second.y) }
    if ((wb - wa).getDistance() > 8.dp.toPx()) dimensionLine(wa, wb, outward(wa, wb), labels.second, c.green, measurer, style)
    // Height: the vertical edge furthest left on screen.
    val heightEdges = listOf(
        Vec3(x0, 0.0, z0) to Vec3(x0, h, z0), Vec3(x1, 0.0, z0) to Vec3(x1, h, z0),
        Vec3(x1, 0.0, z1) to Vec3(x1, h, z1), Vec3(x0, 0.0, z1) to Vec3(x0, h, z1),
    ).map { (a, b) -> proj.project(a).p to proj.project(b).p }
    val (ha, hb) = heightEdges.minBy { (it.first.x + it.second.x) }
    if ((hb - ha).getDistance() > 8.dp.toPx()) dimensionLine(ha, hb, outward(ha, hb), labels.third, c.orange, measurer, style)
}

private fun DrawScope.drawCylinderDimensions(
    proj: Projector,
    d: Double,
    h: Double,
    labels: Triple<String, String, String>,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    c: DatumColors,
) {
    // Diameter across the top along the screen-horizontal direction.
    var best = 0.0
    var bestA = Offset.Zero
    var bestB = Offset.Zero
    for (i in 0 until 36) {
        val a = PI * i / 36
        val p1 = proj.project(Vec3(cos(a) * d / 2, h, sin(a) * d / 2)).p
        val p2 = proj.project(Vec3(-cos(a) * d / 2, h, -sin(a) * d / 2)).p
        val span = abs(p2.x - p1.x).toDouble()
        if (span > best) {
            best = span
            bestA = p1
            bestB = p2
        }
    }
    dimensionLine(bestA, bestB, Offset(0f, -1f), "Ø " + labels.first, c.accent, measurer, style, outward = 26f)
    var minX = Float.MAX_VALUE
    var a = Offset.Zero
    var b = Offset.Zero
    for (i in 0 until 72) {
        val ang = 2 * PI * i / 72
        val p1 = proj.project(Vec3(cos(ang) * d / 2, 0.0, sin(ang) * d / 2)).p
        val p2 = proj.project(Vec3(cos(ang) * d / 2, h, sin(ang) * d / 2)).p
        if (p1.x + p2.x < minX) {
            minX = p1.x + p2.x
            a = p1
            b = p2
        }
    }
    dimensionLine(a, b, Offset(-1f, 0f), labels.third, c.orange, measurer, style)
}

/** Slowly spins the camera while [enabled] (turntable), pausing whenever the user touches it. */
@Composable
fun Turntable(camera: OrbitCamera, enabled: Boolean) {
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        var last = 0L
        while (true) {
            val t = withFrameMillis { it }
            if (last != 0L && !camera.interacting && !camera.yaw.isRunning && !camera.pitch.isRunning) {
                camera.yaw.snapTo(camera.yaw.value + 12f * (t - last) / 1000f)
            }
            last = t
        }
    }
}
