package com.alliehe.core.color.nativeimpl

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alliehe.core.color.BrandColors
import com.alliehe.core.color.Hct
import com.alliehe.core.color.KotlinSchemeGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import org.junit.runner.RunWith

/** On-device: libatrium_color.so loads and matches the Kotlin implementation (±1 per channel). */
@RunWith(AndroidJUnit4::class)
class NativeParityTest {
    private val native = NativeSchemeGenerator()
    private val kotlin = KotlinSchemeGenerator()

    private fun close(a: Int, b: Int) = intArrayOf(24, 16, 8, 0).all { s -> abs((a shr s and 0xFF) - (b shr s and 0xFF)) <= 1 }

    @Test
    fun libraryLoads() = assertTrue(native.isNative)

    @Test
    fun schemesMatchKotlin() {
        val seeds = BrandColors.PRESETS + (0 until 360 step 7).map { Hct.toArgb(it.toDouble(), 50.0, 55.0) }
        for (seed in seeds) for (contrast in floatArrayOf(0f, 0.5f, 1f)) {
            val n = native.generate(seed, contrast).toIntArray()
            val k = kotlin.generate(seed, contrast).toIntArray()
            for (i in n.indices) assertTrue("%08X role %d: %08X vs %08X".format(seed, i, n[i], k[i]), close(n[i], k[i]))
        }
    }

    @Test
    fun bitmapExtractionMatchesKotlin() {
        val w = 96; val h = 96
        val px = IntArray(w * h) { i -> if (i % 10 < 7) 0xFF1E88E5.toInt() else 0xFFE53935.toInt() }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val step = (w + 63) / 64
        val sw = (w + step - 1) / step; val sh = (h + step - 1) / step
        val sampled = IntArray(sw * sh) { i -> px[(i / sw) * step * w + (i % sw) * step] }
        assertEquals(kotlin.extractSeed(sampled, sw, sh), native.dominantColor(bmp))
    }
}
