package com.alliehe.core.color

/**
 * Seed → Wear color roles. Implementations: `NativeSchemeGenerator` (C++/JNI, `:core:color-native`)
 * and [KotlinSchemeGenerator] (pure-Kotlin port of the same algorithm, used as fallback and in JVM tests).
 */
interface SchemeGenerator {
    /** Full role set for [seedArgb]. [contrast] in 0..1 raises the minimum text contrast from 4.5:1 to 7:1. */
    fun generate(seedArgb: Int, contrast: Float = 0f): WearColorRoles

    /** Same HCT hue as [argb], chroma capped at [maxChroma], at HCT [tone] (0..100). */
    fun retone(argb: Int, maxChroma: Float, tone: Float): Int

    /** Dominant "seed" color of ARGB [pixels] (row-major, unpremultiplied), or null if none qualifies. */
    fun extractSeed(pixels: IntArray, width: Int, height: Int): Int?
}

/** Small (seed, contrast) LRU shared by implementations; palettes are immutable so caching is safe. */
class SchemeCache(private val capacity: Int = 16) {
    private val map = object : LinkedHashMap<Long, WearColorRoles>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, WearColorRoles>?) = size > capacity
    }

    @Synchronized
    fun getOrPut(seedArgb: Int, contrast: Float, create: () -> WearColorRoles): WearColorRoles {
        val key = (seedArgb.toLong() shl 32) or (contrast.toRawBits().toLong() and 0xFFFFFFFFL)
        return map.getOrPut(key, create)
    }
}
