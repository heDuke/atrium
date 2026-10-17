package com.alliehe.core.color

import kotlin.math.max

/**
 * The palette algorithm shared by [KotlinSchemeGenerator] and the native generator.
 * Keep in lock-step with `color_core.cpp` (`generate_roles`).
 *
 * Palettes from seed HCT (h, c):
 * - primary (h, max(c, 36)), secondary (h, 16), tertiary (h + 60, 24); for near-gray seeds (c < 4) all three
 *   use chroma 0 (exact sRGB grays), so gray seeds stay monochrome
 * - neutral (h, 4), neutralVariant (h, 8), error (25, 80)
 * Fixed tone ladder (watch = "build from black"): accent 80 / dim 70 / container 30 / on 20 / onContainer 90;
 * surfaces 12 / 17 / 22; onSurface 92; onSurfaceVariant 80; outline 60; outlineVariant 40; background 0.
 * Contrast: every on-role vs. its container (and onSurface* vs. the brightest surface) is pushed away in
 * 1-tone steps until it reaches 4.5:1 (+2.5 × contrast, so 7:1 at contrast = 1). Outline vs. black ≥ 3:1.
 */
object TonalScheme {
    const val MIN_RATIO = 4.5
    const val OUTLINE_MIN_RATIO = 3.0

    fun generate(seedArgb: Int, contrast: Float): IntArray {
        val hct = Hct.fromArgb(seedArgb)
        val h = hct[0]
        val c = hct[1]
        val minRatio = MIN_RATIO + 2.5 * contrast.coerceIn(0f, 1f)
        // Achromatic seeds (c < 4) give a fully monochrome scheme: no invented hue for any accent.
        val mono = c < 4.0
        val pc = if (mono) 0.0 else max(c, 36.0)
        val sc = if (mono) 0.0 else 16.0
        val tc = if (mono) 0.0 else 24.0
        val out = IntArray(WearColorRoles.ROLE_COUNT)

        fun accent(base: Int, hue: Double, chroma: Double) {
            val accent = Hct.toArgb(hue, chroma, 80.0)
            val container = Hct.toArgb(hue, chroma, 30.0)
            out[base] = accent
            out[base + 1] = Hct.toArgb(hue, chroma, 70.0)
            out[base + 2] = container
            out[base + 3] = onColor(hue, chroma, 20.0, accent, minRatio)
            out[base + 4] = onColor(hue, chroma, 90.0, container, minRatio)
        }
        accent(0, h, pc)
        accent(5, h, sc)
        accent(10, h + 60.0, tc)

        val surfHigh = Hct.toArgb(h, 4.0, 22.0)
        out[15] = Hct.toArgb(h, 4.0, 12.0)
        out[16] = Hct.toArgb(h, 4.0, 17.0)
        out[17] = surfHigh
        out[18] = onColor(h, 4.0, 92.0, surfHigh, minRatio)
        out[19] = onColor(h, 8.0, 80.0, surfHigh, minRatio)
        out[20] = onColor(h, 8.0, 60.0, BLACK, OUTLINE_MIN_RATIO)
        out[21] = Hct.toArgb(h, 8.0, 40.0)
        out[22] = BLACK
        out[23] = onColor(h, 4.0, 95.0, BLACK, minRatio)

        accent(24, 25.0, 80.0)
        return out
    }

    /** Start at [tone]; step away from [background]'s tone until contrast ≥ [minRatio] (or 0/100 reached). */
    private fun onColor(hue: Double, chroma: Double, tone: Double, background: Int, minRatio: Double): Int {
        val lighter = Hct.toneOf(background) < 50.0
        var t = tone
        var color = Hct.toArgb(hue, chroma, t)
        while (Hct.contrastRatio(color, background) < minRatio) {
            t += if (lighter) 1.0 else -1.0
            if (t < 0.0 || t > 100.0) break
            color = Hct.toArgb(hue, chroma, t)
        }
        return color
    }

    private const val BLACK = 0xFF000000.toInt()
}
