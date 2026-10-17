package com.alliehe.core.color

/** Pure-Kotlin [SchemeGenerator]: JVM tests and the fallback when the native library is unavailable. */
class KotlinSchemeGenerator : SchemeGenerator {
    private val cache = SchemeCache()

    override fun generate(seedArgb: Int, contrast: Float): WearColorRoles =
        cache.getOrPut(seedArgb, contrast) { WearColorRoles.fromIntArray(TonalScheme.generate(seedArgb, contrast)) }

    override fun retone(argb: Int, maxChroma: Float, tone: Float): Int {
        val hct = Hct.fromArgb(argb)
        return Hct.toArgb(hct[0], minOf(hct[1], maxChroma.toDouble()), tone.toDouble())
    }

    override fun extractSeed(pixels: IntArray, width: Int, height: Int): Int? =
        SeedExtractor.extract(pixels, width, height)
}
