package com.alliehe.feature.drawer.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrownZoomTest {
    @Test fun zoomIsClamped() {
        val c = CrownZoom()
        repeat(100) { c.onRotary(-100f) }
        assertEquals(c.max, c.zoom, 0f)
        repeat(100) { c.onRotary(100f) }
        assertEquals(c.min, c.zoom, 0f)
    }

    @Test fun settingsRevealedAfterContinuedRotationAtMin() {
        val c = CrownZoom()
        var revealed = false
        repeat(50) { if (c.onRotary(40f)) revealed = true }
        assertTrue(revealed)
        assertTrue(c.settingsRevealed)
    }

    @Test fun reverseRotationHidesSettings() {
        val c = CrownZoom()
        repeat(50) { c.onRotary(40f) }
        c.onRotary(-5f)
        assertFalse(c.settingsRevealed)
    }

    @Test fun nearMinCountsAsMin() {
        val c = CrownZoom(pxPerZoom = 1000f)
        c.onRotary(395f) // zoom = 0.605, within epsilon of 0.6
        assertTrue(c.atMin)
    }
}
