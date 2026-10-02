package com.akhielesh.datum.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.akhielesh.datum.core.math.Vec3
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class ArTool(val label: String) {
    LINE("Line"), PATH("Path"), AREA("Area"), HEIGHT("Height"), BOX("Box"), CYLINDER("Cylinder"), SPHERE("Sphere");

    val stepsToComplete: Int
        get() = when (this) {
            BOX -> 4
            CYLINDER -> 3
            SPHERE -> 2
            HEIGHT -> 2
            else -> Int.MAX_VALUE
        }
}

enum class TrackingStatus { STARTING, SEARCHING, TRACKING, LIMITED, ERROR }

data class ScreenPt(val x: Float, val y: Float)

enum class LineRole { MEASURED, LIVE, EDGE, GUIDE }

data class ArLine(val a: ScreenPt, val b: ScreenPt, val role: LineRole, val lengthM: Double = 0.0, val label: Boolean = false, val tag: Char = ' ')

data class ArDot(val p: ScreenPt, val live: Boolean = false)

data class ArReadout(
    /** Primary value in SI (m or m²). */
    val value: Double,
    val isArea: Boolean = false,
    val title: String,
    val secondary: List<Pair<String, Double>> = emptyList(),
)

/** Final dimensions of a 3D shape measured in AR (metres). */
data class ArShape(val length: Double, val width: Double, val height: Double)

data class ArFrameState(
    val status: TrackingStatus = TrackingStatus.STARTING,
    val message: String = "Starting camera…",
    val reticleValid: Boolean = false,
    val reticleRing: List<ScreenPt> = emptyList(),
    val reticleDistance: Double = 0.0,
    val dots: List<ArDot> = emptyList(),
    val lines: List<ArLine> = emptyList(),
    val fills: List<List<ScreenPt>> = emptyList(),
    val readout: ArReadout? = null,
    val shape: ArShape? = null,
    val step: Int = 0,
    val canUndo: Boolean = false,
    val depthEnabled: Boolean = false,
    val planesFound: Boolean = false,
    val tool: ArTool = ArTool.LINE,
)

/**
 * ARCore measuring engine. Rendering is only the camera background; all measurement geometry is
 * projected to screen space each frame and published as [ArFrameState] for a Compose overlay to
 * draw (crisp, anti-aliased and themeable). All ARCore objects are touched only on the GL thread;
 * UI requests are queued as commands and executed at the start of the next frame.
 */
class ArEngine(context: Context) : GLSurfaceView.Renderer {
    private val background = BackgroundRenderer()
    val rotation = DisplayRotationHelper(context)

    @Volatile
    var session: Session? = null
        private set
    @Volatile
    private var textureBound = false
    private val commands = ConcurrentLinkedQueue<(Frame?) -> Unit>()
    private val _state = MutableStateFlow(ArFrameState())
    val state: StateFlow<ArFrameState> = _state.asStateFlow()

    // ----- GL-thread state -----
    private var tool = ArTool.LINE
    private val anchors = ArrayList<Anchor>()
    private var fixedHeight: Double? = null
    private var reticle: Vec3? = null
    private var reticleTrackable: HitResult? = null
    private var depthEnabled = false
    private var viewW = 1
    private var viewH = 1
    private val viewM = FloatArray(16)
    private val projM = FloatArray(16)
    private val vpM = FloatArray(16)
    private val invVp = FloatArray(16)
    private val tmp4 = FloatArray(4)
    private val out4 = FloatArray(4)

    fun attach(session: Session) {
        val config = Config(session).apply {
            focusMode = Config.FocusMode.AUTO
            planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            instantPlacementMode = Config.InstantPlacementMode.DISABLED
        }
        depthEnabled = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
        if (depthEnabled) config.depthMode = Config.DepthMode.AUTOMATIC
        session.configure(config)
        this.session = session
        textureBound = false
    }

    fun detach() {
        session = null
    }

    // ----- UI commands -----
    fun setTool(t: ArTool) = commands.add {
        clearInternal()
        tool = t
    }

    fun addPoint() = commands.add { addPointInternal() }
    fun undo() = commands.add { undoInternal() }
    fun clear() = commands.add { clearInternal() }

    // ----- Renderer -----
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
        textureBound = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewW = max(1, width)
        viewH = max(1, height)
        rotation.onSurfaceChanged(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (!textureBound) {
            s.setCameraTextureName(background.textureId)
            textureBound = true
        }
        rotation.updateSessionIfNeeded(s)
        val frame = try {
            s.update()
        } catch (t: Throwable) {
            _state.value = _state.value.copy(status = TrackingStatus.ERROR, message = "Camera unavailable — reopen this screen")
            return
        }
        background.draw(frame)
        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            reticle = null
            reticleTrackable = null
            drainCommands(null)
            publish(frame, tracking = false)
            return
        }
        camera.getViewMatrix(viewM, 0)
        camera.getProjectionMatrix(projM, 0, NEAR, 100f)
        Matrix.multiplyMM(vpM, 0, projM, 0, viewM, 0)
        Matrix.invertM(invVp, 0, vpM, 0)
        updateReticle(frame)
        drainCommands(frame)
        publish(frame, tracking = true)
    }

    private fun drainCommands(frame: Frame?) {
        while (true) {
            val cmd = commands.poll() ?: break
            runCatching { cmd(frame) }
        }
    }

    // ----- Hit testing -----
    private fun updateReticle(frame: Frame) {
        val hits = runCatching { frame.hitTest(viewW / 2f, viewH / 2f) }.getOrElse { emptyList() }
        val camPose = frame.camera.pose
        val hit = hits.firstOrNull { h ->
            when (val t = h.trackable) {
                is Plane -> t.isPoseInPolygon(h.hitPose) && distanceToPlane(h.hitPose, camPose) > 0
                is DepthPoint -> true
                is Point -> t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
                else -> false
            }
        }
        if (hit == null) {
            reticle = null
            reticleTrackable = null
            return
        }
        val p = Vec3(hit.hitPose.tx().toDouble(), hit.hitPose.ty().toDouble(), hit.hitPose.tz().toDouble())
        val prev = reticle
        reticle = if (prev == null || (p - prev).length > 0.06) p else prev.lerp(p, 0.35)
        reticleTrackable = hit
    }

    private fun distanceToPlane(planePose: Pose, cameraPose: Pose): Float {
        val normal = FloatArray(3)
        planePose.getTransformedAxis(1, 1.0f, normal, 0)
        return (cameraPose.tx() - planePose.tx()) * normal[0] +
            (cameraPose.ty() - planePose.ty()) * normal[1] +
            (cameraPose.tz() - planePose.tz()) * normal[2]
    }

    /** World ray through the screen centre (unprojected, so it's exact even with image cropping). */
    private fun centerRay(): Pair<Vec3, Vec3>? {
        fun unproject(z: Float): Vec3? {
            tmp4[0] = 0f; tmp4[1] = 0f; tmp4[2] = z; tmp4[3] = 1f
            Matrix.multiplyMV(out4, 0, invVp, 0, tmp4, 0)
            if (abs(out4[3]) < 1e-9) return null
            return Vec3((out4[0] / out4[3]).toDouble(), (out4[1] / out4[3]).toDouble(), (out4[2] / out4[3]).toDouble())
        }
        val near = unproject(-1f) ?: return null
        val far = unproject(1f) ?: return null
        return near to (far - near).normalized()
    }

    /** Height above [base] of the point on the vertical through [base] closest to the centre ray. */
    private fun heightAt(base: Vec3): Double? {
        val (o, d) = centerRay() ?: return null
        return verticalHeight(base, o, d)
    }

    private fun pos(a: Anchor): Vec3 {
        val p = a.pose
        return Vec3(p.tx().toDouble(), p.ty().toDouble(), p.tz().toDouble())
    }

    // ----- Measurement state machine -----
    private fun addPointInternal() {
        val session = session ?: return
        when (tool) {
            ArTool.HEIGHT -> when (anchors.size) {
                0 -> placeAtReticle(session)
                else -> if (fixedHeight == null) {
                    fixedHeight = heightAt(pos(anchors[0]))
                } else {
                    clearInternal()
                    placeAtReticle(session)
                }
            }
            ArTool.BOX -> when {
                anchors.size < 2 -> placeAtReticle(session)
                anchors.size == 2 -> {
                    val c = boxCorner() ?: return
                    anchors += session.createAnchor(Pose.makeTranslation(c.x.toFloat(), c.y.toFloat(), c.z.toFloat()))
                }
                fixedHeight == null -> fixedHeight = heightAt(pos(anchors[2]))?.coerceAtLeast(0.0)
                else -> {
                    clearInternal()
                    placeAtReticle(session)
                }
            }
            ArTool.CYLINDER -> when {
                anchors.size < 2 -> placeAtReticle(session)
                fixedHeight == null -> fixedHeight = heightAt(pos(anchors[1]))?.coerceAtLeast(0.0)
                else -> {
                    clearInternal()
                    placeAtReticle(session)
                }
            }
            ArTool.SPHERE -> if (anchors.size < 2) placeAtReticle(session) else {
                clearInternal()
                placeAtReticle(session)
            }
            else -> placeAtReticle(session)
        }
    }

    private fun placeAtReticle(session: Session) {
        val r = reticle ?: return
        val hit = reticleTrackable
        val pose = Pose.makeTranslation(r.x.toFloat(), r.y.toFloat(), r.z.toFloat())
        val anchor = runCatching { hit?.trackable?.createAnchor(pose) }.getOrNull() ?: session.createAnchor(pose)
        anchors += anchor
    }

    private fun undoInternal() {
        if (fixedHeight != null) {
            fixedHeight = null
            return
        }
        if (anchors.isNotEmpty()) anchors.removeAt(anchors.lastIndex).detach()
    }

    private fun clearInternal() {
        anchors.forEach { it.detach() }
        anchors.clear()
        fixedHeight = null
    }

    /** Width corner: from B, perpendicular to AB in the horizontal plane, as far as the reticle. */
    private fun boxCorner(): Vec3? {
        val a = pos(anchors[0])
        val b = pos(anchors[1])
        val r = reticle ?: return null
        val u = Vec3(b.x - a.x, 0.0, b.z - a.z).normalized()
        if (u.length < 0.5) return null
        val v = Vec3(-u.z, 0.0, u.x)
        val w = (r - b) dot v
        return Vec3(b.x + v.x * w, b.y, b.z + v.z * w)
    }

    // ----- Projection -----
    private fun toView(p: Vec3): FloatArray {
        tmp4[0] = p.x.toFloat(); tmp4[1] = p.y.toFloat(); tmp4[2] = p.z.toFloat(); tmp4[3] = 1f
        val o = FloatArray(4)
        Matrix.multiplyMV(o, 0, viewM, 0, tmp4, 0)
        return o
    }

    private fun projectView(v: FloatArray): ScreenPt? {
        Matrix.multiplyMV(out4, 0, projM, 0, v, 0)
        val w = out4[3]
        if (w <= 1e-6f) return null
        val nx = out4[0] / w
        val ny = out4[1] / w
        return ScreenPt((nx + 1f) / 2f * viewW, (1f - ny) / 2f * viewH)
    }

    private fun project(p: Vec3): ScreenPt? {
        val v = toView(p)
        if (v[2] > -NEAR) return null
        return projectView(v)
    }

    /** Projects a world segment, clipping it against the near plane. */
    private fun segment(a: Vec3, b: Vec3, role: LineRole, label: Boolean = false, tag: Char = ' '): ArLine? {
        val va = toView(a)
        val vb = toView(b)
        val za = va[2]
        val zb = vb[2]
        val limit = -NEAR
        if (za > limit && zb > limit) return null
        fun lerp(p: FloatArray, q: FloatArray, t: Float) = floatArrayOf(
            p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t, p[2] + (q[2] - p[2]) * t, 1f,
        )
        val ca = if (za > limit) lerp(va, vb, (limit - za) / (zb - za)) else va
        val cb = if (zb > limit) lerp(vb, va, (limit - zb) / (za - zb)) else vb
        val sa = projectView(ca) ?: return null
        val sb = projectView(cb) ?: return null
        return ArLine(sa, sb, role, (b - a).length, label, tag)
    }

    // ----- Publishing -----
    private fun publish(frame: Frame, tracking: Boolean) {
        val camera = frame.camera
        val planes = session?.getAllTrackables(Plane::class.java)?.any { it.trackingState == TrackingState.TRACKING && it.subsumedBy == null } == true
        if (!tracking) {
            val msg = when (camera.trackingFailureReason) {
                TrackingFailureReason.INSUFFICIENT_LIGHT -> "Too dark — add some light"
                TrackingFailureReason.EXCESSIVE_MOTION -> "Moving too fast — slow down"
                TrackingFailureReason.INSUFFICIENT_FEATURES -> "Aim at a surface with some texture"
                TrackingFailureReason.CAMERA_UNAVAILABLE -> "Camera unavailable"
                TrackingFailureReason.BAD_STATE -> "Tracking problem — move slowly"
                else -> "Move your phone slowly to start"
            }
            _state.value = ArFrameState(
                status = if (camera.trackingState == TrackingState.PAUSED) TrackingStatus.LIMITED else TrackingStatus.STARTING,
                message = msg, tool = tool, depthEnabled = depthEnabled, planesFound = planes,
                canUndo = anchors.isNotEmpty(), step = anchors.size,
            )
            return
        }
        val dots = ArrayList<ArDot>()
        val lines = ArrayList<ArLine>()
        val fills = ArrayList<List<ScreenPt>>()
        val pts = anchors.map { pos(it) }
        val r = reticle
        var readout: ArReadout? = null
        var shape: ArShape? = null
        var message: String

        // Reticle ring lying on the surface.
        val ring = ArrayList<ScreenPt>()
        reticleTrackable?.hitPose?.let { pose ->
            if (r != null) {
                val xa = pose.xAxis
                val za = pose.zAxis
                val rad = 0.03
                for (i in 0 until 40) {
                    val ang = 2 * Math.PI * i / 40
                    val p = Vec3(
                        r.x + rad * (cos(ang) * xa[0] + sin(ang) * za[0]),
                        r.y + rad * (cos(ang) * xa[1] + sin(ang) * za[1]),
                        r.z + rad * (cos(ang) * xa[2] + sin(ang) * za[2]),
                    )
                    project(p)?.let { ring += it }
                }
            }
        }
        val camPos = Vec3(camera.pose.tx().toDouble(), camera.pose.ty().toDouble(), camera.pose.tz().toDouble())
        val reticleDistance = r?.let { (it - camPos).length } ?: 0.0
        pts.forEach { p -> project(p)?.let { dots += ArDot(it) } }

        when (tool) {
            ArTool.LINE -> {
                var total = 0.0
                var i = 0
                while (i + 1 < pts.size) {
                    segment(pts[i], pts[i + 1], LineRole.MEASURED, label = true)?.let { lines += it }
                    total = (pts[i + 1] - pts[i]).length
                    i += 2
                }
                if (pts.size % 2 == 1 && r != null) {
                    segment(pts.last(), r, LineRole.LIVE, label = true)?.let { lines += it }
                    total = (r - pts.last()).length
                }
                message = if (pts.size % 2 == 0) "Aim at the start point and tap +" else "Aim at the end point and tap +"
                if (pts.isNotEmpty()) readout = ArReadout(total, title = "Distance")
            }
            ArTool.PATH -> {
                var total = 0.0
                for (i in 1 until pts.size) {
                    segment(pts[i - 1], pts[i], LineRole.MEASURED, label = true)?.let { lines += it }
                    total += (pts[i] - pts[i - 1]).length
                }
                var live = 0.0
                if (pts.isNotEmpty() && r != null) {
                    segment(pts.last(), r, LineRole.LIVE, label = true)?.let { lines += it }
                    live = (r - pts.last()).length
                }
                message = if (pts.isEmpty()) "Aim at the start of the path and tap +" else "Keep adding points along the path"
                if (pts.isNotEmpty()) readout = ArReadout(total + live, title = "Path length", secondary = listOf("Segments" to pts.size.toDouble()))
            }
            ArTool.AREA -> {
                val poly = if (r != null) pts + r else pts
                for (i in 1 until poly.size) {
                    segment(poly[i - 1], poly[i], if (i == poly.size - 1 && r != null) LineRole.LIVE else LineRole.MEASURED, label = true)?.let { lines += it }
                }
                if (poly.size >= 3) {
                    segment(poly.last(), poly.first(), LineRole.GUIDE)?.let { lines += it }
                    val screen = poly.mapNotNull { project(it) }
                    if (screen.size == poly.size) fills += screen
                    val area = polygonArea(poly)
                    var perim = 0.0
                    for (i in poly.indices) perim += (poly[(i + 1) % poly.size] - poly[i]).length
                    readout = ArReadout(area, isArea = true, title = "Area", secondary = listOf("Perimeter" to perim))
                }
                message = if (pts.size < 3) "Tap + at each corner of the area" else "Add more corners or save"
            }
            ArTool.HEIGHT -> {
                if (pts.isEmpty()) {
                    message = "Aim at the floor where the object stands"
                } else {
                    val base = pts[0]
                    val h = fixedHeight ?: heightAt(base) ?: 0.0
                    val top = Vec3(base.x, base.y + h, base.z)
                    segment(base, top, if (fixedHeight == null) LineRole.LIVE else LineRole.MEASURED, label = true)?.let { lines += it }
                    project(top)?.let { dots += ArDot(it, live = fixedHeight == null) }
                    readout = ArReadout(h, title = "Height")
                    message = if (fixedHeight == null) "Tilt up until the cross meets the top, then tap +" else "Height locked — tap + to measure again"
                }
            }
            ArTool.BOX -> {
                message = when {
                    pts.isEmpty() -> "Aim at a bottom corner of the box"
                    pts.size == 1 -> "Aim along one bottom edge to the next corner"
                    pts.size == 2 -> "Aim along the other bottom edge"
                    fixedHeight == null -> "Tilt up to the top edge, then tap +"
                    else -> "Box measured"
                }
                if (pts.size == 1 && r != null) segment(pts[0], r, LineRole.LIVE, label = true, tag = 'L')?.let { lines += it }
                if (pts.size >= 2) {
                    val a = pts[0]
                    val b = pts[1]
                    val c = if (pts.size >= 3) pts[2] else boxCorner()
                    if (c == null) {
                        segment(a, b, LineRole.MEASURED, label = true, tag = 'L')?.let { lines += it }
                    } else {
                        val base = a.y
                        val aa = Vec3(a.x, base, a.z)
                        val bb = Vec3(b.x, base, b.z)
                        val cc = Vec3(c.x, base, c.z)
                        val dd = aa + (cc - bb)
                        val h = if (pts.size >= 3) fixedHeight ?: heightAt(cc)?.coerceAtLeast(0.0) ?: 0.0 else 0.0
                        val up = Vec3(0.0, h, 0.0)
                        val bottom = listOf(aa, bb, cc, dd)
                        val topRing = bottom.map { it + up }
                        val liveW = pts.size == 2
                        val liveH = pts.size >= 3 && fixedHeight == null
                        for (i in 0 until 4) {
                            val j = (i + 1) % 4
                            val role = when {
                                i == 0 -> LineRole.MEASURED
                                i == 1 && liveW -> LineRole.LIVE
                                else -> LineRole.EDGE
                            }
                            segment(bottom[i], bottom[j], role, label = i <= 1, tag = if (i == 0) 'L' else if (i == 1) 'W' else ' ')?.let { lines += it }
                            if (h > 0.005) {
                                segment(topRing[i], topRing[j], LineRole.EDGE)?.let { lines += it }
                                segment(bottom[i], topRing[i], if (i == 2 && liveH) LineRole.LIVE else LineRole.EDGE, label = i == 2, tag = if (i == 2) 'H' else ' ')?.let { lines += it }
                            }
                        }
                        val faceSets = if (h > 0.005) listOf(bottom, topRing) else listOf(bottom)
                        faceSets.forEach { f -> f.mapNotNull { project(it) }.takeIf { it.size == 4 }?.let { fills += it } }
                        val length = (bb - aa).length
                        val width = (cc - bb).length
                        readout = ArReadout(
                            length * width * max(h, 0.0), title = "Box",
                            secondary = listOf("Length" to length, "Width" to width, "Height" to h),
                        )
                        if (fixedHeight != null) shape = ArShape(length, width, h)
                    }
                }
            }
            ArTool.CYLINDER -> {
                message = when {
                    pts.isEmpty() -> "Aim at one side of the base"
                    pts.size == 1 -> "Aim straight across at the opposite side"
                    fixedHeight == null -> "Tilt up to the top rim, then tap +"
                    else -> "Cylinder measured"
                }
                val b = if (pts.size >= 2) pts[1] else r
                if (pts.isNotEmpty() && b != null) {
                    val a = pts[0]
                    val d = Vec3(b.x - a.x, 0.0, b.z - a.z).length
                    val center = Vec3((a.x + b.x) / 2, a.y, (a.z + b.z) / 2)
                    val h = if (pts.size >= 2) fixedHeight ?: heightAt(Vec3(b.x, a.y, b.z))?.coerceAtLeast(0.0) ?: 0.0 else 0.0
                    val n = 48
                    val ringB = (0 until n).map { i -> val t = 2 * Math.PI * i / n; Vec3(center.x + cos(t) * d / 2, a.y, center.z + sin(t) * d / 2) }
                    for (i in 0 until n) {
                        segment(ringB[i], ringB[(i + 1) % n], LineRole.EDGE)?.let { lines += it }
                        if (h > 0.005) segment(ringB[i] + Vec3(0.0, h, 0.0), ringB[(i + 1) % n] + Vec3(0.0, h, 0.0), LineRole.EDGE)?.let { lines += it }
                    }
                    segment(a, Vec3(b.x, a.y, b.z), if (pts.size < 2) LineRole.LIVE else LineRole.MEASURED, label = true, tag = 'D')?.let { lines += it }
                    if (pts.size >= 2) segment(Vec3(b.x, a.y, b.z), Vec3(b.x, a.y + h, b.z), if (fixedHeight == null) LineRole.LIVE else LineRole.MEASURED, label = true, tag = 'H')?.let { lines += it }
                    readout = ArReadout(Math.PI * d * d / 4 * h, title = "Cylinder", secondary = listOf("Diameter" to d, "Height" to h))
                    if (fixedHeight != null) shape = ArShape(d, d, h)
                }
            }
            ArTool.SPHERE -> {
                message = if (pts.isEmpty()) "Aim at one side of the sphere" else if (pts.size == 1) "Aim at the opposite side" else "Sphere measured"
                val b = if (pts.size >= 2) pts[1] else r
                if (pts.isNotEmpty() && b != null) {
                    val a = pts[0]
                    segment(a, b, if (pts.size < 2) LineRole.LIVE else LineRole.MEASURED, label = true, tag = 'D')?.let { lines += it }
                    val d = (b - a).length
                    readout = ArReadout(Math.PI * d * d * d / 6, title = "Sphere", secondary = listOf("Diameter" to d))
                    if (pts.size >= 2) shape = ArShape(d, d, d)
                }
            }
        }
        if (!planes && pts.isEmpty() && r == null) message = "Move the phone slowly to find surfaces"
        _state.value = ArFrameState(
            status = TrackingStatus.TRACKING,
            message = message,
            reticleValid = r != null,
            reticleRing = ring,
            reticleDistance = reticleDistance,
            dots = dots,
            lines = lines,
            fills = fills,
            readout = readout,
            shape = shape,
            step = anchors.size + if (fixedHeight != null) 1 else 0,
            canUndo = anchors.isNotEmpty() || fixedHeight != null,
            depthEnabled = depthEnabled,
            planesFound = planes,
            tool = tool,
        )
    }

    companion object {
        const val NEAR = 0.05f

        /**
         * Closest points between the vertical line through [base] and the ray [origin] + t·[dir]:
         * returns how far up the vertical that point is (the height being aimed at), or null when
         * the ray is parallel to the vertical or the point lies behind the camera.
         */
        fun verticalHeight(base: Vec3, origin: Vec3, dir: Vec3): Double? {
            val u = Vec3.Y
            val w0 = base - origin
            val b = u dot dir
            val c = dir dot dir
            val d = u dot w0
            val e = dir dot w0
            val denom = c - b * b
            if (abs(denom) < 1e-9) return null
            val rayT = (e - b * d) / denom
            if (rayT < 0) return null
            return (b * e - c * d) / denom
        }

        /** Newell's method: area of a (nearly) planar 3D polygon. */
        fun polygonArea(poly: List<Vec3>): Double {
            var nx = 0.0
            var ny = 0.0
            var nz = 0.0
            for (i in poly.indices) {
                val a = poly[i]
                val b = poly[(i + 1) % poly.size]
                nx += (a.y - b.y) * (a.z + b.z)
                ny += (a.z - b.z) * (a.x + b.x)
                nz += (a.x - b.x) * (a.y + b.y)
            }
            return 0.5 * sqrt(nx * nx + ny * ny + nz * nz)
        }
    }
}
