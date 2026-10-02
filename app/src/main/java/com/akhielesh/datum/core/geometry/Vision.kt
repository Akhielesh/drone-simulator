package com.akhielesh.datum.core.geometry

import com.akhielesh.datum.core.math.Mat3
import com.akhielesh.datum.core.math.Vec2
import com.akhielesh.datum.core.math.Vec3
import com.akhielesh.datum.core.math.solveLinearSystem
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Planar projective geometry used by photo calibration:
 *  - [Homography] maps image pixels of a plane onto metric plane coordinates (via a reference card).
 *  - [rectangleAspect] recovers a rectangle's true width/height ratio from one perspective photo
 *    (Zhang & He, "Whiteboard scanning and image enhancement", 2007), no reference object needed.
 */
class Homography(private val h: Mat3) {
    fun map(p: Vec2): Vec2 {
        val v = h * Vec3(p.x, p.y, 1.0)
        return Vec2(v.x / v.z, v.y / v.z)
    }

    fun inverse(): Homography? = h.inverse()?.let { Homography(it) }

    val matrix: Mat3 get() = h

    companion object {
        /** Solves the DLT system for the homography taking [src][i] → [dst][i] (exactly 4 pairs). */
        fun fromQuad(src: List<Vec2>, dst: List<Vec2>): Homography? {
            require(src.size == 4 && dst.size == 4)
            val a = Array(8) { DoubleArray(8) }
            val b = DoubleArray(8)
            for (i in 0 until 4) {
                val (x, y) = src[i]
                val (u, v) = dst[i]
                a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y)
                b[2 * i] = u
                a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y)
                b[2 * i + 1] = v
            }
            val s = solveLinearSystem(a, b) ?: return null
            return Homography(Mat3(doubleArrayOf(s[0], s[1], s[2], s[3], s[4], s[5], s[6], s[7], 1.0)))
        }
    }
}

/** Corners of an imaged rectangle, in pixels: top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: Vec2, val tr: Vec2, val br: Vec2, val bl: Vec2) {
    val points: List<Vec2> get() = listOf(tl, tr, br, bl)

    fun isConvex(): Boolean {
        val p = points
        var sign = 0.0
        for (i in 0 until 4) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val c = p[(i + 2) % 4]
            val cr = (b - a) cross (c - b)
            if (abs(cr) < 1e-9) return false
            if (sign == 0.0) sign = cr else if (sign * cr < 0) return false
        }
        return true
    }

    fun area(): Double {
        val p = points
        var s = 0.0
        for (i in 0 until 4) s += p[i] cross p[(i + 1) % 4]
        return abs(s) / 2
    }

    fun withPoint(index: Int, v: Vec2) = when (index) {
        0 -> copy(tl = v)
        1 -> copy(tr = v)
        2 -> copy(br = v)
        else -> copy(bl = v)
    }

    companion object {
        fun centered(cx: Double, cy: Double, w: Double, h: Double) = Quad(
            Vec2(cx - w / 2, cy - h / 2), Vec2(cx + w / 2, cy - h / 2),
            Vec2(cx + w / 2, cy + h / 2), Vec2(cx - w / 2, cy + h / 2),
        )
    }
}

/** Real-world size of a rectangle measured in a photo. Units follow the reference (metres). */
data class PlaneMeasurement(
    val width: Double,
    val height: Double,
    /** Relative 1σ uncertainty estimate (e.g. 0.02 = 2 %). */
    val relativeSigma: Double,
)

/**
 * Measures [target] using a coplanar reference rectangle of known size [refWidth] × [refHeight].
 * Both quads must lie on the same physical plane (e.g. a credit card resting on a box face).
 */
fun measureWithReference(reference: Quad, refWidth: Double, refHeight: Double, target: Quad): PlaneMeasurement? {
    if (!reference.isConvex() || !target.isConvex()) return null
    val metric = listOf(Vec2(0.0, 0.0), Vec2(refWidth, 0.0), Vec2(refWidth, refHeight), Vec2(0.0, refHeight))
    val h = Homography.fromQuad(reference.points, metric) ?: return null
    val p = target.points.map(h::map)
    if (p.any { !it.x.isFinite() || !it.y.isFinite() }) return null
    // Average opposite edges: reduces the effect of a single misplaced corner.
    val w = ((p[1] - p[0]).length + (p[2] - p[3]).length) / 2
    val ht = ((p[3] - p[0]).length + (p[2] - p[1]).length) / 2
    // Uncertainty grows when the reference is small relative to the target (extrapolation) and when
    // opposite edges disagree (corner placement error or a non-coplanar reference).
    val refPx = sqrt(reference.area())
    val targetPx = sqrt(target.area())
    val extrapolation = (targetPx / refPx).coerceAtLeast(1.0)
    val disagreeW = abs((p[1] - p[0]).length - (p[2] - p[3]).length) / max(w, 1e-9)
    val disagreeH = abs((p[3] - p[0]).length - (p[2] - p[1]).length) / max(ht, 1e-9)
    val sigma = 0.008 + 0.004 * extrapolation + 0.5 * (disagreeW + disagreeH) / 2
    return PlaneMeasurement(w, ht, sigma.coerceAtMost(0.25))
}

data class AspectResult(
    /** True width / height of the photographed rectangle. */
    val ratio: Double,
    /** Focal length (px) recovered from the quad, or the fallback that was used. */
    val focalPx: Double,
    val focalFromImage: Boolean,
)

/**
 * Zhang–He rectification: recovers the aspect ratio of a rectangle seen in perspective.
 * [principal] is the image centre; [fallbackFocalPx] (from EXIF / camera specs) is used when the
 * quad is too fronto-parallel for the focal length to be observable.
 */
fun rectangleAspect(q: Quad, principal: Vec2, fallbackFocalPx: Double): AspectResult? {
    if (!q.isConvex()) return null
    // Zhang–He label corners m1=(0,0) m2=(w,0) m3=(0,h) m4=(w,h).
    fun hv(p: Vec2) = Vec3(p.x - principal.x, p.y - principal.y, 1.0)
    val m1 = hv(q.tl)
    val m2 = hv(q.tr)
    val m3 = hv(q.bl)
    val m4 = hv(q.br)
    val k2den = (m2 cross m4) dot m3
    val k3den = (m3 cross m4) dot m2
    if (abs(k2den) < 1e-12 || abs(k3den) < 1e-12) return null
    val k2 = ((m1 cross m4) dot m3) / k2den
    val k3 = ((m1 cross m4) dot m2) / k3den
    val n2 = m2 * k2 - m1
    val n3 = m3 * k3 - m1
    var focal = fallbackFocalPx
    var fromImage = false
    val denom = n2.z * n3.z
    // Only trust the recovered focal length when perspective is strong enough to observe it.
    val perspective = abs(n2.z) + abs(n3.z)
    if (abs(denom) > 1e-10 && perspective > 0.04) {
        val f2 = -(n2.x * n3.x + n2.y * n3.y) / denom
        if (f2 > 0) {
            val f = sqrt(f2)
            if (f > 0.3 * fallbackFocalPx && f < 3.0 * fallbackFocalPx) {
                focal = f
                fromImage = true
            }
        }
    }
    fun norm(n: Vec3) = sqrt((n.x * n.x + n.y * n.y) / (focal * focal) + n.z * n.z)
    val a = norm(n2)
    val b = norm(n3)
    if (b < 1e-12) return null
    val ratio = a / b
    if (!ratio.isFinite() || ratio <= 0) return null
    return AspectResult(ratio, focal, fromImage)
}

/**
 * "Magnetic" corner snapping: looks for the strongest Harris corner near [guess] in a grayscale
 * image and returns it when it is convincingly corner-like, otherwise null.
 */
class CornerSnapper(private val gray: FloatArray, private val width: Int, private val height: Int) {

    fun snap(guess: Vec2, radius: Int): Vec2? {
        val cx = guess.x.toInt()
        val cy = guess.y.toInt()
        val x0 = max(2, cx - radius - 3)
        val x1 = min(width - 3, cx + radius + 3)
        val y0 = max(2, cy - radius - 3)
        val y1 = min(height - 3, cy + radius + 3)
        if (x1 - x0 < 6 || y1 - y0 < 6) return null
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        val ixx = FloatArray(w * h)
        val iyy = FloatArray(w * h)
        val ixy = FloatArray(w * h)
        for (y in y0..y1) for (x in x0..x1) {
            val gx = (px(x + 1, y - 1) + 2 * px(x + 1, y) + px(x + 1, y + 1)) -
                (px(x - 1, y - 1) + 2 * px(x - 1, y) + px(x - 1, y + 1))
            val gy = (px(x - 1, y + 1) + 2 * px(x, y + 1) + px(x + 1, y + 1)) -
                (px(x - 1, y - 1) + 2 * px(x, y - 1) + px(x + 1, y - 1))
            val i = (y - y0) * w + (x - x0)
            ixx[i] = gx * gx
            iyy[i] = gy * gy
            ixy[i] = gx * gy
        }
        var best = 0.0
        var bestX = cx
        var bestY = cy
        var sum = 0.0
        var n = 0
        val r = 2
        for (y in max(y0 + r, cy - radius)..min(y1 - r, cy + radius)) {
            for (x in max(x0 + r, cx - radius)..min(x1 - r, cx + radius)) {
                var sxx = 0.0
                var syy = 0.0
                var sxy = 0.0
                for (dy in -r..r) for (dx in -r..r) {
                    val i = (y + dy - y0) * w + (x + dx - x0)
                    sxx += ixx[i]; syy += iyy[i]; sxy += ixy[i]
                }
                val det = sxx * syy - sxy * sxy
                val tr = sxx + syy
                val resp = det - 0.04 * tr * tr
                // Prefer corners close to the user's guess.
                val dist2 = ((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble()
                val weighted = resp / (1.0 + dist2 / (radius * radius * 2.0))
                sum += abs(resp)
                n++
                if (weighted > best) {
                    best = weighted
                    bestX = x
                    bestY = y
                }
            }
        }
        if (n == 0) return null
        val meanResp = sum / n
        // Require a clear peak relative to the neighbourhood and an absolute floor against noise.
        return if (best > 6 * meanResp && best > 2e-4) Vec2(bestX.toDouble(), bestY.toDouble()) else null
    }

    private fun px(x: Int, y: Int): Float = gray[y * width + x]
}

/**
 * Polar decomposition by Newton iteration: the rotation matrix closest to [m]
 * (used to square up a box from three hand-measured, slightly skewed edge vectors).
 */
fun closestRotation(m: Mat3): Mat3 {
    var r = m
    repeat(30) {
        val inv = r.inverse() ?: return r
        val invT = inv.transpose()
        val next = Mat3(DoubleArray(9) { i -> 0.5 * (r.m[i] + invT.m[i]) })
        var diff = 0.0
        for (i in 0 until 9) diff += abs(next.m[i] - r.m[i])
        r = next
        if (diff < 1e-12) return r
    }
    return r
}
