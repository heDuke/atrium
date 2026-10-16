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
