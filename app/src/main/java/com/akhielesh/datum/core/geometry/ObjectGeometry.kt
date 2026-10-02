package com.akhielesh.datum.core.geometry

import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.DimEstimate
import com.akhielesh.datum.core.data.RatioConstraint
import com.akhielesh.datum.core.data.ShapeKind
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sqrt

/** A dimension value with its 1σ uncertainty (metres). */
data class Measured(val value: Double, val sigma: Double, val sources: Int)

data class FusedDims(val l: Measured?, val w: Measured?, val h: Measured?) {
    fun get(d: Dim) = when (d) {
        Dim.L -> l
        Dim.W -> w
        Dim.H -> h
    }

    val complete: Boolean get() = l != null && w != null && h != null
}

/**
 * "Auto-calibration" of an object from every measurement the user has made of it.
 *
 * Each source (AR, IMU motion, photo with reference) reports a value and an uncertainty. The fused
 * estimate is the inverse-variance weighted mean – the maximum-likelihood combination of
 * independent Gaussian measurements – so a precise source (AR, a photo with a credit card)
 * automatically outweighs a rough one (a quick motion sweep).
 *
 * Photos without a reference contribute *ratios* (true width/height of a face, recovered from
 * perspective). A ratio turns any measured dimension into an estimate of its partner.
 */
object ObjectFusion {

    fun fuse(estimates: List<DimEstimate>, ratios: List<RatioConstraint>, shape: ShapeKind = ShapeKind.BOX): FusedDims {
        val direct = Dim.entries.associateWith { d -> combine(estimates.filter { it.dim == d }.map { it.value to it.sigma }) }
        // Propagate ratio constraints from the direct estimates (one hop, no double counting).
        val derived = mutableMapOf<Dim, MutableList<Pair<Double, Double>>>()
        for (r in ratios) {
            if (r.ratio <= 0 || !r.ratio.isFinite()) continue
            direct[r.b]?.let { b ->
                val v = r.ratio * b.value
                val s = sqrt((r.ratio * b.sigma) * (r.ratio * b.sigma) + (v * r.relSigma) * (v * r.relSigma))
                derived.getOrPut(r.a) { mutableListOf() } += v to s
            }
            direct[r.a]?.let { a ->
                val v = a.value / r.ratio
                val s = sqrt((a.sigma / r.ratio) * (a.sigma / r.ratio) + (v * r.relSigma) * (v * r.relSigma))
                derived.getOrPut(r.b) { mutableListOf() } += v to s
            }
        }
        val fused = Dim.entries.associateWith { d ->
            val all = estimates.filter { it.dim == d }.map { it.value to it.sigma } + (derived[d] ?: emptyList())
            combine(all)
        }
        return when (shape) {
            ShapeKind.BOX -> FusedDims(fused[Dim.L], fused[Dim.W], fused[Dim.H])
            // Cylinder: L is the diameter; W mirrors it. Sphere: everything is the diameter.
            ShapeKind.CYLINDER -> {
                val d = combine(listOfNotNull(fused[Dim.L], fused[Dim.W]).map { it.value to it.sigma })
                FusedDims(d, d, fused[Dim.H])
            }
            ShapeKind.SPHERE -> {
                val d = combine(listOfNotNull(fused[Dim.L], fused[Dim.W], fused[Dim.H]).map { it.value to it.sigma })
                FusedDims(d, d, d)
            }
        }
    }

    /** Inverse-variance weighted mean of (value, sigma) pairs. */
    fun combine(values: List<Pair<Double, Double>>): Measured? {
        val valid = values.filter { it.first.isFinite() && it.first > 0 }
        if (valid.isEmpty()) return null
        var wSum = 0.0
        var vSum = 0.0
        for ((v, s) in valid) {
            val sigma = max(s, 1e-4)
            val w = 1.0 / (sigma * sigma)
            wSum += w
            vSum += w * v
        }
        return Measured(vSum / wSum, sqrt(1.0 / wSum), valid.size)
    }
}

data class Material(val name: String, val densityKgM3: Double, val hollowArealKgM2: Double = 0.0) {
    val isHollow: Boolean get() = hollowArealKgM2 > 0
}

object Materials {
    val all = listOf(
        Material("Cardboard box", 0.0, hollowArealKgM2 = 0.55),
        Material("Water", 1000.0),
        Material("Pine", 510.0),
        Material("Oak", 750.0),
        Material("Plywood", 600.0),
        Material("MDF", 750.0),
        Material("PLA (3D print, solid)", 1240.0),
        Material("ABS", 1050.0),
        Material("Aluminium", 2700.0),
        Material("Steel", 7850.0),
        Material("Glass", 2500.0),
        Material("Concrete", 2400.0),
        Material("Marble", 2700.0),
        Material("Ice", 917.0),
        Material("EPS foam", 25.0),
        Material("Gold", 19300.0),
    )

    fun byName(name: String) = all.firstOrNull { it.name == name } ?: all.first()
}

data class ShapeMetrics(
    val volume: Double,
    val volumeSigma: Double,
    val surfaceArea: Double,
    val spaceDiagonal: Double,
    val footprint: Double,
    val basePerimeter: Double,
    val totalEdgeLength: Double,
    val faceDiagonals: List<Pair<String, Double>>,
    /** Courier volumetric weight, kg, at 5000 cm³/kg (international express). */
    val dimWeightKg: Double,
    /** US domestic volumetric weight, lb, at 139 in³/lb. */
    val dimWeightLb: Double,
)

object ShapeMath {
    fun metrics(shape: ShapeKind, l: Double, w: Double, h: Double, sl: Double = 0.0, sw: Double = 0.0, sh: Double = 0.0): ShapeMetrics {
        val dimWeightKg: Double
        val dimWeightLb: Double
        return when (shape) {
            ShapeKind.BOX -> {
                val v = l * w * h
                val rel = sqrt(sq(sl / l) + sq(sw / w) + sq(sh / h))
                dimWeightKg = v * 1e6 / 5000.0
                dimWeightLb = v / (0.0254 * 0.0254 * 0.0254) / 139.0
                ShapeMetrics(
                    volume = v,
                    volumeSigma = v * rel,
                    surfaceArea = 2 * (l * w + l * h + w * h),
                    spaceDiagonal = sqrt(l * l + w * w + h * h),
                    footprint = l * w,
                    basePerimeter = 2 * (l + w),
                    totalEdgeLength = 4 * (l + w + h),
                    faceDiagonals = listOf(
                        "Top" to sqrt(l * l + w * w),
                        "Front" to sqrt(l * l + h * h),
                        "Side" to sqrt(w * w + h * h),
                    ),
                    dimWeightKg = dimWeightKg,
                    dimWeightLb = dimWeightLb,
                )
            }
            ShapeKind.CYLINDER -> {
                val d = l
                val r = d / 2
                val v = PI * r * r * h
                val rel = sqrt(sq(2 * sl / d) + sq(sh / h))
                // Couriers bill a cylinder as its bounding box.
                val box = d * d * h
                dimWeightKg = box * 1e6 / 5000.0
                dimWeightLb = box / (0.0254 * 0.0254 * 0.0254) / 139.0
                ShapeMetrics(
                    volume = v,
                    volumeSigma = v * rel,
                    surfaceArea = 2 * PI * r * h + 2 * PI * r * r,
                    spaceDiagonal = sqrt(d * d + h * h),
                    footprint = PI * r * r,
                    basePerimeter = PI * d,
                    totalEdgeLength = 2 * PI * d,
                    faceDiagonals = listOf("Profile" to sqrt(d * d + h * h)),
                    dimWeightKg = dimWeightKg,
                    dimWeightLb = dimWeightLb,
                )
            }
            ShapeKind.SPHERE -> {
                val d = l
                val r = d / 2
                val v = 4.0 / 3.0 * PI * r * r * r
                val box = d * d * d
                dimWeightKg = box * 1e6 / 5000.0
                dimWeightLb = box / (0.0254 * 0.0254 * 0.0254) / 139.0
                ShapeMetrics(
                    volume = v,
                    volumeSigma = v * 3 * sl / d,
                    surfaceArea = 4 * PI * r * r,
                    spaceDiagonal = d,
                    footprint = PI * r * r,
                    basePerimeter = PI * d,
                    totalEdgeLength = PI * d,
                    faceDiagonals = emptyList(),
                    dimWeightKg = dimWeightKg,
                    dimWeightLb = dimWeightLb,
                )
            }
        }
    }

    fun weightKg(material: Material, shape: ShapeKind, l: Double, w: Double, h: Double): Double {
        val m = metrics(shape, l, w, h)
        return if (material.isHollow) m.surfaceArea * material.hollowArealKgM2 else m.volume * material.densityKgM3
    }

    private fun sq(x: Double) = if (x.isFinite()) x * x else 0.0
}

data class Container(val name: String, val l: Double, val w: Double, val h: Double)

object Containers {
    val common = listOf(
        Container("Cabin bag (IATA)", 0.55, 0.40, 0.23),
        Container("Under-seat bag", 0.40, 0.30, 0.15),
        Container("Shoebox", 0.33, 0.19, 0.12),
        Container("Medium flat-rate box", 0.346, 0.302, 0.086),
        Container("Small moving box", 0.40, 0.30, 0.30),
        Container("Large moving box", 0.60, 0.45, 0.45),
        Container("Euro pallet load", 1.20, 0.80, 1.80),
        Container("20 ft shipping container", 5.90, 2.35, 2.39),
    )

    /** Axis-aligned fit allowing any 90° rotation: compare sorted dimensions. */
    fun fits(l: Double, w: Double, h: Double, c: Container): Boolean {
        val a = listOf(l, w, h).sortedDescending()
        val b = listOf(c.l, c.w, c.h).sortedDescending()
        return a[0] <= b[0] && a[1] <= b[1] && a[2] <= b[2]
    }

    /** Whether the object can pass through a rectangular opening (e.g. a door), longest axis first. */
    fun fitsThroughOpening(l: Double, w: Double, h: Double, openingW: Double, openingH: Double): Boolean {
        val a = listOf(l, w, h).sortedDescending()
        return a[1] <= max(openingW, openingH) && a[2] <= minOf(openingW, openingH)
    }
}

/** A box recovered from a corner walk A→B→C→D (three mutually perpendicular edges). */
data class BoxFit(
    val length: Double,
    val width: Double,
    val height: Double,
    /** Worst deviation of the measured edges from right angles, degrees. */
    val maxAngleErrorDeg: Double,
)

object BoxFitter {
    /**
     * Hand-measured edges are never exactly perpendicular. The orthonormal frame closest to the
     * measured directions (polar decomposition) spreads that error evenly; each dimension is then
     * the measured edge projected onto its own axis.
     */
    fun fromWalk(a: com.akhielesh.datum.core.math.Vec3, b: com.akhielesh.datum.core.math.Vec3, c: com.akhielesh.datum.core.math.Vec3, d: com.akhielesh.datum.core.math.Vec3): BoxFit {
        val e1 = b - a
        val e2 = c - b
        val e3 = d - c
        val m = com.akhielesh.datum.core.math.Mat3.fromColumns(e1.normalized(), e2.normalized(), e3.normalized())
        val r = closestRotation(m)
        val l = kotlin.math.abs(e1 dot r.column(0))
        val w = kotlin.math.abs(e2 dot r.column(1))
        val h = kotlin.math.abs(e3 dot r.column(2))
        val errs = listOf(e1.angleTo(e2), e2.angleTo(e3), e1.angleTo(e3)).map { kotlin.math.abs(Math.toDegrees(it) - 90.0) }
        return BoxFit(l, w, h, errs.max())
    }
}
