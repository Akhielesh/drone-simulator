package com.akhielesh.datum.core.geometry

import com.akhielesh.datum.core.data.Dim
import com.akhielesh.datum.core.data.DimEstimate
import com.akhielesh.datum.core.data.EstimateSource
import com.akhielesh.datum.core.data.RatioConstraint
import com.akhielesh.datum.core.data.ShapeKind
import com.akhielesh.datum.core.math.Quat
import com.akhielesh.datum.core.math.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sqrt

class ObjectGeometryTest {

    @Test
    fun inverseVarianceFusionWeightsPreciseSourcesMore() {
        val fused = ObjectFusion.fuse(
            listOf(
                DimEstimate(Dim.L, 0.42, 0.01, EstimateSource.AR),
                DimEstimate(Dim.L, 0.40, 0.02, EstimateSource.MOTION),
            ),
            emptyList(),
        )
        val l = fused.l!!
        assertEquals(0.416, l.value, 1e-9)
        assertEquals(sqrt(1.0 / 12_500), l.sigma, 1e-9)
        assertEquals(2, l.sources)
        assertNull(fused.w)
        assertFalse(fused.complete)
    }

    @Test
    fun photoRatioPropagatesToMissingDimension() {
        val fused = ObjectFusion.fuse(
            listOf(DimEstimate(Dim.L, 0.60, 0.006, EstimateSource.AR), DimEstimate(Dim.W, 0.30, 0.01, EstimateSource.MOTION)),
            listOf(RatioConstraint(Dim.L, Dim.H, 2.0, 0.02)),
        )
        // H = L / 2 from the photo proportion.
        assertEquals(0.30, fused.h!!.value, 1e-6)
        assertTrue(fused.complete)
    }

    @Test
    fun cylinderSharesDiameter() {
        val fused = ObjectFusion.fuse(
            listOf(DimEstimate(Dim.L, 0.12, 0.002, EstimateSource.AR), DimEstimate(Dim.H, 0.30, 0.002, EstimateSource.AR)),
            emptyList(), ShapeKind.CYLINDER,
        )
        assertEquals(0.12, fused.w!!.value, 1e-9)
        assertTrue(fused.complete)
    }

    @Test
    fun boxFitterSquaresUpSkewedCornerWalk() {
        val rot = Quat.fromAxisAngle(Vec3(0.3, 1.0, 0.2), 0.7)
        val a = Vec3(0.1, 0.2, 0.3)
        // Edges deliberately 2–3° off perpendicular, as a hand would place them.
        val e1 = rot.rotate(Vec3(0.42, 0.0, 0.0))
        val e2 = rot.rotate(Vec3(0.012, 0.31, 0.0))
        val e3 = rot.rotate(Vec3(0.0, 0.01, 0.25))
        val fit = BoxFitter.fromWalk(a, a + e1, a + e1 + e2, a + e1 + e2 + e3)
        assertEquals(0.42, fit.length, 0.004)
        assertEquals(0.31, fit.width, 0.004)
        assertEquals(0.25, fit.height, 0.004)
        assertTrue(fit.maxAngleErrorDeg in 1.0..4.0)
    }

    @Test
    fun boxMetrics() {
        val m = ShapeMath.metrics(ShapeKind.BOX, 0.4, 0.3, 0.2)
        assertEquals(0.024, m.volume, 1e-12)
        assertEquals(2 * (0.12 + 0.08 + 0.06), m.surfaceArea, 1e-12)
        assertEquals(sqrt(0.16 + 0.09 + 0.04), m.spaceDiagonal, 1e-12)
        assertEquals(4.8, m.dimWeightKg, 1e-9)
        val cyl = ShapeMath.metrics(ShapeKind.CYLINDER, 0.2, 0.2, 0.5)
        assertEquals(PI * 0.01 * 0.5, cyl.volume, 1e-12)
        val sphere = ShapeMath.metrics(ShapeKind.SPHERE, 0.2, 0.2, 0.2)
        assertEquals(4.0 / 3 * PI * 0.001, sphere.volume, 1e-12)
    }

    @Test
    fun containersAllowRotation() {
        val bag = Containers.common.first { it.name.startsWith("Cabin") }
        assertTrue(Containers.fits(0.20, 0.50, 0.35, bag))
        assertFalse(Containers.fits(0.60, 0.30, 0.20, bag))
        assertTrue(Containers.fitsThroughOpening(2.4, 0.7, 0.6, 0.81, 2.03))
        assertFalse(Containers.fitsThroughOpening(2.4, 1.0, 0.9, 0.81, 2.03))
    }

    @Test
    fun weightUsesDensityOrBoardArea() {
        val steel = Materials.byName("Steel")
        assertEquals(7850.0 * 0.001, ShapeMath.weightKg(steel, ShapeKind.BOX, 0.1, 0.1, 0.1), 1e-9)
        val box = Materials.byName("Cardboard box")
        assertEquals(0.06 * 0.55, ShapeMath.weightKg(box, ShapeKind.BOX, 0.1, 0.1, 0.1), 1e-9)
    }
}
