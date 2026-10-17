package com.alliehe.core.color.nativeimpl

import android.graphics.Bitmap
import android.os.Trace
import com.alliehe.core.color.KotlinSchemeGenerator
import com.alliehe.core.color.SchemeCache
import com.alliehe.core.color.SchemeGenerator
import com.alliehe.core.color.SeedExtractor
import com.alliehe.core.color.WearColorRoles

/**
 * [SchemeGenerator] backed by `libatrium_color.so` (C++17). One JNI call returns all roles; results are
 * cached per (seed, contrast). If the library fails to load, every call goes to [KotlinSchemeGenerator],
 * which implements the same algorithm (parity is enforced by the shared golden file).
 */
class NativeSchemeGenerator(
    private val useNative: Boolean = NativeColorCore.loaded,
) : SchemeGenerator {
    private val fallback = KotlinSchemeGenerator()
    private val cache = SchemeCache()

    val isNative: Boolean get() = useNative

    override fun generate(seedArgb: Int, contrast: Float): WearColorRoles {
        if (!useNative) return fallback.generate(seedArgb, contrast)
        return cache.getOrPut(seedArgb, contrast) {
            WearColorRoles.fromIntArray(NativeColorCore.nativeGenerate(seedArgb, contrast))
        }
    }

    override fun retone(argb: Int, maxChroma: Float, tone: Float): Int =
        if (useNative) NativeColorCore.nativeRetone(argb, maxChroma, tone) else fallback.retone(argb, maxChroma, tone)

    override fun extractSeed(pixels: IntArray, width: Int, height: Int): Int? {
        if (!useNative) return fallback.extractSeed(pixels, width, height)
        return NativeColorCore.nativeExtractPixels(pixels, width, height).takeIf { it != 0 }
    }

    /**
     * Dominant (seed) color of [bitmap], or null when it is gray/near-black/near-white/transparent.
     * Native path reads the pixels in place (`AndroidBitmap_lockPixels`, no copy); hardware or
     * non-ARGB_8888 bitmaps are copied and handled by the Kotlin extractor. Traced as "AtriumExtract".
     */
    fun dominantColor(bitmap: Bitmap): Int? {
        Trace.beginSection(TRACE_EXTRACT)
        try {
            if (useNative) {
                val r = NativeColorCore.nativeExtractBitmap(bitmap)
                if (r >= 0L) return r.toInt().takeIf { it != 0 }
            }
            return kotlinDominant(bitmap)
        } finally {
            Trace.endSection()
        }
    }

    private fun kotlinDominant(bitmap: Bitmap): Int? {
        val src = if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap
        // Sample on the same grid as the native path instead of reading every pixel.
        val step = maxOf(1, (maxOf(src.width, src.height) + SeedExtractor.MAX_SIDE - 1) / SeedExtractor.MAX_SIDE)
        val w = (src.width + step - 1) / step
        val h = (src.height + step - 1) / step
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) px[y * w + x] = src.getPixel(x * step, y * step)
        if (src !== bitmap) src.recycle()
        return fallback.extractSeed(px, w, h)
    }

    companion object {
        const val TRACE_EXTRACT = "AtriumExtract"
    }
}
