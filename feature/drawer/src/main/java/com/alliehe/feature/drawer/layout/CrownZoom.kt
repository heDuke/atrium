package com.alliehe.feature.drawer.layout

/**
 * Pure crown → zoom state machine for the honeycomb (unit-tested).
 *
 * Positive [onRotary] deltas zoom out. Once at (or within [epsilon] of) [min], further positive
 * rotation accumulates and reveals the Settings entry after [revealPx]; any negative rotation hides it.
 */
class CrownZoom(
    val min: Float = 0.6f,
    val max: Float = 1.6f,
    private val pxPerZoom: Float = 900f,
    private val revealPx: Float = 160f,
    private val epsilon: Float = 0.01f,
) {
    var zoom: Float = 1f
        private set
    var settingsRevealed: Boolean = false
        private set
    private var overscroll = 0f

    val atMin: Boolean get() = zoom - min <= epsilon

    /** Returns true when this event newly revealed the settings entry. */
    fun onRotary(deltaPx: Float): Boolean {
        if (deltaPx < 0f) {
            overscroll = 0f
            settingsRevealed = false
        } else if (atMin) {
            overscroll += deltaPx
        }
        zoom = (zoom - deltaPx / pxPerZoom).coerceIn(min, max)
        if (!settingsRevealed && atMin && overscroll >= revealPx) {
            settingsRevealed = true
            return true
        }
        return false
    }
}
