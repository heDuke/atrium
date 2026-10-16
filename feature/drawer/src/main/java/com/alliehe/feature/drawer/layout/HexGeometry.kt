package com.alliehe.feature.drawer.layout

import kotlin.math.sqrt

/** Position in units of one hex spacing (center-to-center distance between neighbours). */
data class HexPoint(val x: Float, val y: Float) {
    fun distanceTo(other: HexPoint): Float {
        val dx = x - other.x
        val dy = y - other.y
        return sqrt(dx * dx + dy * dy)
    }
}

/** Pure geometry for the honeycomb layout (unit-tested on the JVM). */
object HexGeometry {
    private val SQRT3_2 = sqrt(3f) / 2f
    // Axial neighbour directions.
    private val DIRS = arrayOf(1 to 0, 1 to -1, 0 to -1, -1 to 0, -1 to 1, 0 to 1)

    /** Axial coordinates in spiral order: index 0 is the centre, then rings of 6k cells. */
    fun spiralAxial(count: Int): List<Pair<Int, Int>> {
        if (count <= 0) return emptyList()
        val out = ArrayList<Pair<Int, Int>>(count)
        out += 0 to 0
        var ring = 1
        while (out.size < count) {
            var q = DIRS[4].first * ring
            var r = DIRS[4].second * ring
            for (side in 0 until 6) {
                repeat(ring) {
                    if (out.size < count) out += q to r
                    q += DIRS[side].first
                    r += DIRS[side].second
                }
            }
            ring++
        }
        return out
    }

    fun toPoint(q: Int, r: Int): HexPoint = HexPoint(q + r / 2f, r * SQRT3_2)

    fun spiral(count: Int): List<HexPoint> = spiralAxial(count).map { (q, r) -> toPoint(q, r) }

    /** Fisheye scale: 1 at the centre, easing down to [min] at [radius]. */
    fun fisheyeScale(distance: Float, radius: Float, min: Float = 0.35f): Float {
        if (radius <= 0f) return 1f
        val t = (distance / radius).coerceIn(0f, 1f)
        return 1f - (1f - min) * t * t
    }

    /** Edge fade: opaque inside 60% of [radius], transparent at the rim. */
    fun edgeAlpha(distance: Float, radius: Float): Float {
        if (radius <= 0f) return 1f
        val t = (distance / radius).coerceIn(0f, 1f)
        return if (t < 0.6f) 1f else ((1f - t) / 0.4f).coerceIn(0f, 1f)
    }

    /** Index of the point nearest [target], or -1 for an empty list. */
    fun nearest(points: List<HexPoint>, target: HexPoint): Int {
        var best = -1
        var bestD = Float.MAX_VALUE
        points.forEachIndexed { i, p ->
            val d = p.distanceTo(target)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }
}
