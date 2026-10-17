package com.alliehe.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemePropertiesTest {
    private val generator = KotlinSchemeGenerator()
    private val seeds = GoldenFixtures.SEEDS.map { it.first }.distinct() +
        (0 until 360 step 15).map { Hct.toArgb(it.toDouble(), 60.0, 50.0) }

    private fun pairs(r: WearColorRoles) = listOf(
        "onPrimary/primary" to (r.onPrimary to r.primary),
        "onPrimaryContainer/primaryContainer" to (r.onPrimaryContainer to r.primaryContainer),
        "onSecondary/secondary" to (r.onSecondary to r.secondary),
        "onSecondaryContainer/secondaryContainer" to (r.onSecondaryContainer to r.secondaryContainer),
        "onTertiary/tertiary" to (r.onTertiary to r.tertiary),
        "onTertiaryContainer/tertiaryContainer" to (r.onTertiaryContainer to r.tertiaryContainer),
        "onSurface/surfaceContainerHigh" to (r.onSurface to r.surfaceContainerHigh),
        "onSurface/surfaceContainer" to (r.onSurface to r.surfaceContainer),
        "onSurfaceVariant/surfaceContainerHigh" to (r.onSurfaceVariant to r.surfaceContainerHigh),
        "onBackground/background" to (r.onBackground to r.background),
        "onError/error" to (r.onError to r.error),
        "onErrorContainer/errorContainer" to (r.onErrorContainer to r.errorContainer),
        "primary/background" to (r.primary to r.background),
    )

    @Test
    fun textPairsMeetFourPointFive() {
        for (seed in seeds) {
            val roles = generator.generate(seed)
            for ((name, p) in pairs(roles)) {
                val ratio = Hct.contrastRatio(p.first, p.second)
                assertTrue("${"%08X".format(seed)} $name = $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun highContrastMeetsSeven() {
        for (seed in seeds) {
            val roles = generator.generate(seed, 1f)
            for ((name, p) in pairs(roles)) {
                val ratio = Hct.contrastRatio(p.first, p.second)
                assertTrue("${"%08X".format(seed)} $name = $ratio", ratio >= 7.0)
            }
        }
    }

    @Test
    fun backgroundIsPureBlack() {
        for (seed in seeds) assertEquals(0xFF000000.toInt(), generator.generate(seed).background)
    }

    @Test
    fun outlineVisibleOnBlack() {
        for (seed in seeds) assertTrue(Hct.contrastRatio(generator.generate(seed).outline, 0xFF000000.toInt()) >= 3.0)
    }

    @Test
    fun cacheReturnsSameInstance() {
        val a = generator.generate(BrandColors.BRAND_SEED)
        assertTrue(a === generator.generate(BrandColors.BRAND_SEED))
    }

    @Test
    fun hctRoundTripsInGamutColors() {
        val samples = intArrayOf(0xFF2F8F7A.toInt(), 0xFF4285F4.toInt(), 0xFFE53935.toInt(), 0xFFF9A825.toInt(), 0xFF7E57C2.toInt(), 0xFF123456.toInt())
        for (argb in samples) {
            val hct = Hct.fromArgb(argb)
            val back = Hct.toArgb(hct[0], hct[1], hct[2])
            for (shift in intArrayOf(16, 8, 0)) {
                val d = ((argb shr shift) and 0xFF) - ((back shr shift) and 0xFF)
                assertTrue("%08X -> %08X".format(argb, back), d in -2..2)
            }
        }
    }

    @Test
    fun toneIsLstar() {
        for (t in listOf(10.0, 30.0, 50.0, 70.0, 90.0)) {
            val c = Hct.toArgb(200.0, 30.0, t)
            assertEquals(t, Hct.toneOf(c), 0.6)
        }
    }

    @Test
    fun graysAreAchromaticAndMonochromeSchemes() {
        // CAM16 with D < 1 leaves sRGB grays a little chroma (white ≈ 2.87 @ 209.5°); < 4 counts as achromatic.
        val white = Hct.fromArgb(0xFFFFFFFF.toInt())
        assertEquals(209.5, white[0], 0.5)
        assertEquals(2.87, white[1], 0.05)
        assertTrue(Hct.fromArgb(0xFF808080.toInt())[1] < 4.0)
        val roles = generator.generate(0xFF808080.toInt())
        for (c in intArrayOf(roles.primary, roles.secondary, roles.tertiary, roles.primaryContainer)) {
            assertTrue("%08X".format(c), (c shr 16 and 0xFF) == (c and 0xFF) && (c shr 8 and 0xFF) == (c and 0xFF))
        }
    }

    @Test
    fun extractionFindsDominantBlue() {
        val (px, w, h) = GoldenFixtures.image("stripes")
        val seed = generator.extractSeed(px, w, h)!!
        val hue = Hct.fromArgb(seed)[0]
        val blue = Hct.fromArgb(0xFF1E88E5.toInt())[0]
        assertEquals(blue, hue, 10.0)
    }

    @Test
    fun extractionIgnoresGrayAndTransparent() {
        val (px, w, h) = GoldenFixtures.image("gray")
        assertEquals(null, generator.extractSeed(px, w, h))
        assertEquals(null, generator.extractSeed(IntArray(16), 4, 4))
    }
}
