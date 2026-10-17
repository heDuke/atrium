package com.alliehe.core.color.nativeimpl

import android.graphics.Bitmap

/** Raw JNI surface of `libatrium_color.so`. Use [NativeSchemeGenerator]; it handles load failure. */
internal object NativeColorCore {
    /** True once `System.loadLibrary` succeeded; false (never throws) on unsupported ABIs or broken installs. */
    val loaded: Boolean = try {
        System.loadLibrary("atrium_color")
        true
    } catch (_: Throwable) {
        false
    }

    @JvmStatic external fun nativeGenerate(seed: Int, contrast: Float): IntArray
    @JvmStatic external fun nativeRetone(argb: Int, maxChroma: Float, tone: Float): Int
    @JvmStatic external fun nativeExtractPixels(pixels: IntArray, width: Int, height: Int): Int
    /** Unsigned seed (0 = none) or -1 when the bitmap can't be locked in place. */
    @JvmStatic external fun nativeExtractBitmap(bitmap: Bitmap): Long
}
