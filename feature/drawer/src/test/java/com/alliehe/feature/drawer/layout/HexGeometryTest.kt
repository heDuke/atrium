package com.alliehe.feature.drawer.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HexGeometryTest {
    @Test fun spiralStartsAtCentreAndHasNoDuplicates() {
        val axial = HexGeometry.spiralAxial(37) // centre + rings 1..3
        assertEquals(0 to 0, axial.first())
        assertEquals(37, axial.toSet().size)
    }

    @Test fun ringOneIsAllNeighboursAtUnitDistance() {
        val pts = HexGeometry.spiral(7)
        pts.drop(1).forEach { assertEquals(1f, it.distanceTo(pts[0]), 1e-4f) }
    }

    @Test fun spiralOrderNeverMovesToAnOuterRingEarly() {
        val pts = HexGeometry.spiral(19)
        val d = pts.map { it.distanceTo(HexPoint(0f, 0f)) }
        assertTrue(d.subList(1, 7).all { it < 1.01f })
        assertTrue(d.subList(7, 19).all { it > 1.5f })
    }

    @Test fun fisheyeAndAlphaBounds() {
        assertEquals(1f, HexGeometry.fisheyeScale(0f, 100f), 0f)
        assertEquals(0.35f, HexGeometry.fisheyeScale(100f, 100f), 1e-4f)
        assertEquals(1f, HexGeometry.edgeAlpha(50f, 100f), 0f)
        assertEquals(0f, HexGeometry.edgeAlpha(200f, 100f), 0f)
    }

    @Test fun nearestPicksClosest() {
        val pts = HexGeometry.spiral(7)
        assertEquals(0, HexGeometry.nearest(pts, HexPoint(0.1f, 0.1f)))
        assertEquals(-1, HexGeometry.nearest(emptyList(), HexPoint(0f, 0f)))
    }
}

class HexGestureMathTest {
    private fun len(p: HexPoint) = p.distanceTo(HexPoint(0f, 0f))

    @Test fun rubberBandIsIdentityInsideAndAsymptoticOutside() {
        val inside = HexPoint(1f, 1f)
        assertEquals(inside, HexGeometry.rubberBand(inside, 3f))
        val far = HexGeometry.rubberBand(HexPoint(100f, 0f), 3f, maxOverscroll = 0.75f)
        assertTrue(len(far) > 3f && len(far) < 3.75f)
        // Monotonic: dragging further always moves a little further.
        val a = len(HexGeometry.rubberBand(HexPoint(4f, 0f), 3f))
        val b = len(HexGeometry.rubberBand(HexPoint(5f, 0f), 3f))
        assertTrue(b > a)
        // Direction is preserved.
        val diag = HexGeometry.rubberBand(HexPoint(10f, 10f), 2f)
        assertEquals(diag.x, diag.y, 1e-4f)
    }

    @Test fun clampStopsAtLimit() {
        assertEquals(2f, len(HexGeometry.clamp(HexPoint(0f, -9f), 2f)), 1e-4f)
        assertEquals(HexPoint(0.5f, 0f), HexGeometry.clamp(HexPoint(0.5f, 0f), 2f))
    }

    @Test fun edgePanOnlyNearTheRim() {
        assertEquals(HexPoint(0f, 0f), HexGeometry.edgePan(HexPoint(50f, 0f), 100f))
        val rim = HexGeometry.edgePan(HexPoint(100f, 0f), 100f)
        assertEquals(1f, rim.x, 1e-4f)
        val mid = HexGeometry.edgePan(HexPoint(0f, -85f), 100f)
        assertEquals(0f, mid.x, 1e-4f)
        assertTrue(mid.y < 0f && mid.y > -1f)
    }

    @Test fun extentOfSpiralRings() {
        assertEquals(0f, HexGeometry.extent(HexGeometry.spiral(1)), 0f)
        assertEquals(1f, HexGeometry.extent(HexGeometry.spiral(7)), 1e-4f)
        assertEquals(0f, HexGeometry.extent(emptyList()), 0f)
    }
}
