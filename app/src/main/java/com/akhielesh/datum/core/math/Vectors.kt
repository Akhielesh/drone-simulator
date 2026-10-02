package com.akhielesh.datum.core.math

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable 3D vector in double precision; all sensor maths runs in SI units (m, m/s, m/s², rad). */
data class Vec3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Double) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(x * x + y * y + z * z)
    val lengthSquared: Double get() = x * x + y * y + z * z

    fun normalized(): Vec3 {
        val l = length
        return if (l < 1e-12) ZERO else Vec3(x / l, y / l, z / l)
    }

    fun lerp(o: Vec3, t: Double) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)

    /** Component of this vector perpendicular to the unit vector [n]. */
    fun rejectFrom(n: Vec3): Vec3 = this - n * (this dot n)

    fun angleTo(o: Vec3): Double {
        val d = length * o.length
        if (d < 1e-12) return 0.0
        return acos(((this dot o) / d).coerceIn(-1.0, 1.0))
    }

    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val X = Vec3(1.0, 0.0, 0.0)
        val Y = Vec3(0.0, 1.0, 0.0)
        val Z = Vec3(0.0, 0.0, 1.0)
        fun of(v: FloatArray) = Vec3(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
    }
}

operator fun Double.times(v: Vec3) = v * this

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    operator fun div(s: Double) = Vec2(x / s, y / s)
    infix fun dot(o: Vec2) = x * o.x + y * o.y
    infix fun cross(o: Vec2) = x * o.y - y * o.x
    val length: Double get() = hypot(x, y)
    fun normalized(): Vec2 {
        val l = length
        return if (l < 1e-12) Vec2(0.0, 0.0) else Vec2(x / l, y / l)
    }
    fun rotated(rad: Double): Vec2 {
        val c = cos(rad)
        val s = sin(rad)
        return Vec2(c * x - s * y, s * x + c * y)
    }
    val angle: Double get() = atan2(y, x)
}

/**
 * Unit quaternion (w, x, y, z). By convention in this codebase `q.rotate(v)` maps a vector from the
 * device (body) frame into the reference frame captured at the start of a tracking session.
 */
data class Quat(val w: Double, val x: Double, val y: Double, val z: Double) {
    operator fun times(q: Quat) = Quat(
        w * q.w - x * q.x - y * q.y - z * q.z,
        w * q.x + x * q.w + y * q.z - z * q.y,
        w * q.y - x * q.z + y * q.w + z * q.x,
        w * q.z + x * q.y - y * q.x + z * q.w,
    )

    fun conjugate() = Quat(w, -x, -y, -z)

    fun normalized(): Quat {
        val n = sqrt(w * w + x * x + y * y + z * z)
        return if (n < 1e-12) IDENTITY else Quat(w / n, x / n, y / n, z / n)
    }

    fun rotate(v: Vec3): Vec3 {
        // v' = v + 2w(q×v) + 2 q×(q×v)
        val tx = 2.0 * (y * v.z - z * v.y)
        val ty = 2.0 * (z * v.x - x * v.z)
        val tz = 2.0 * (x * v.y - y * v.x)
        return Vec3(
            v.x + w * tx + (y * tz - z * ty),
            v.y + w * ty + (z * tx - x * tz),
            v.z + w * tz + (x * ty - y * tx),
        )
    }

    /** Integrates a body-frame angular rate [omega] (rad/s) over [dt] seconds. */
    fun integrate(omega: Vec3, dt: Double): Quat = (this * fromRotationVector(omega * dt)).normalized()

    /** Total rotation angle represented by this quaternion, in radians (0..π). */
    val angle: Double get() = 2.0 * acos(abs(w).coerceAtMost(1.0))

    companion object {
        val IDENTITY = Quat(1.0, 0.0, 0.0, 0.0)

        fun fromAxisAngle(axis: Vec3, angle: Double): Quat {
            val a = axis.normalized()
            val s = sin(angle / 2)
            return Quat(cos(angle / 2), a.x * s, a.y * s, a.z * s)
        }

        /** Exponential map: the rotation of |rv| radians about rv. */
        fun fromRotationVector(rv: Vec3): Quat {
            val theta = rv.length
            if (theta < 1e-9) return Quat(1.0, rv.x / 2, rv.y / 2, rv.z / 2).normalized()
            val s = sin(theta / 2) / theta
            return Quat(cos(theta / 2), rv.x * s, rv.y * s, rv.z * s)
        }

        /** Shortest rotation that takes direction [a] onto direction [b]. */
        fun fromTwoVectors(a: Vec3, b: Vec3): Quat {
            val u = a.normalized()
            val v = b.normalized()
            val d = u dot v
            if (d > 1 - 1e-12) return IDENTITY
            if (d < -1 + 1e-12) {
                var axis = Vec3.X cross u
                if (axis.length < 1e-6) axis = Vec3.Y cross u
                return fromAxisAngle(axis, Math.PI)
            }
            val c = u cross v
            return Quat(1 + d, c.x, c.y, c.z).normalized()
        }
    }
}

/** Row-major 3×3 matrix used for homographies and frame fitting. */
class Mat3(val m: DoubleArray) {
    init {
        require(m.size == 9)
    }

    operator fun get(r: Int, c: Int) = m[r * 3 + c]

    operator fun times(o: Mat3): Mat3 {
        val r = DoubleArray(9)
        for (i in 0 until 3) for (j in 0 until 3) {
            var s = 0.0
            for (k in 0 until 3) s += this[i, k] * o[k, j]
            r[i * 3 + j] = s
        }
        return Mat3(r)
    }

    operator fun times(v: Vec3) = Vec3(
        m[0] * v.x + m[1] * v.y + m[2] * v.z,
        m[3] * v.x + m[4] * v.y + m[5] * v.z,
        m[6] * v.x + m[7] * v.y + m[8] * v.z,
    )

    fun transpose() = Mat3(doubleArrayOf(m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]))

    fun determinant(): Double =
        m[0] * (m[4] * m[8] - m[5] * m[7]) -
            m[1] * (m[3] * m[8] - m[5] * m[6]) +
            m[2] * (m[3] * m[7] - m[4] * m[6])

    fun inverse(): Mat3? {
        val det = determinant()
        if (abs(det) < 1e-18) return null
        val inv = DoubleArray(9)
        inv[0] = (m[4] * m[8] - m[5] * m[7]) / det
        inv[1] = (m[2] * m[7] - m[1] * m[8]) / det
        inv[2] = (m[1] * m[5] - m[2] * m[4]) / det
        inv[3] = (m[5] * m[6] - m[3] * m[8]) / det
        inv[4] = (m[0] * m[8] - m[2] * m[6]) / det
        inv[5] = (m[2] * m[3] - m[0] * m[5]) / det
        inv[6] = (m[3] * m[7] - m[4] * m[6]) / det
        inv[7] = (m[1] * m[6] - m[0] * m[7]) / det
        inv[8] = (m[0] * m[4] - m[1] * m[3]) / det
        return Mat3(inv)
    }

    fun column(c: Int) = Vec3(this[0, c], this[1, c], this[2, c])

    companion object {
        val IDENTITY = Mat3(doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0))
        fun fromColumns(a: Vec3, b: Vec3, c: Vec3) =
            Mat3(doubleArrayOf(a.x, b.x, c.x, a.y, b.y, c.y, a.z, b.z, c.z))
    }
}

/** Solves A·x = b in place with partial pivoting. Returns null when the system is singular. */
fun solveLinearSystem(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
    val n = b.size
    val mat = Array(n) { i -> a[i].copyOf() }
    val rhs = b.copyOf()
    for (col in 0 until n) {
        var pivot = col
        var best = abs(mat[col][col])
        for (r in col + 1 until n) {
            val v = abs(mat[r][col])
            if (v > best) {
                best = v
                pivot = r
            }
        }
        if (best < 1e-12) return null
        if (pivot != col) {
            val tmp = mat[pivot]; mat[pivot] = mat[col]; mat[col] = tmp
            val t = rhs[pivot]; rhs[pivot] = rhs[col]; rhs[col] = t
        }
        for (r in col + 1 until n) {
            val f = mat[r][col] / mat[col][col]
            if (f == 0.0) continue
            for (c in col until n) mat[r][c] -= f * mat[col][c]
            rhs[r] -= f * rhs[col]
        }
    }
    val x = DoubleArray(n)
    for (r in n - 1 downTo 0) {
        var s = rhs[r]
        for (c in r + 1 until n) s -= mat[r][c] * x[c]
        x[r] = s / mat[r][r]
    }
    return x
}
