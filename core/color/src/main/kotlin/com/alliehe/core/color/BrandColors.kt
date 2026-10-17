package com.alliehe.core.color

/** Atrium (中庭) brand seed and the 8 preset seeds offered on the Theme page. */
object BrandColors {
    /** Courtyard jade. The brand palette is *generated* from this seed (no hand-authored roles). */
    const val BRAND_SEED: Int = 0xFF2F8F7A.toInt()

    val PRESETS: List<Int> = listOf(
        BRAND_SEED,
        0xFF4285F4.toInt(), // blue
        0xFF7E57C2.toInt(), // violet
        0xFFD81B60.toInt(), // rose
        0xFFE53935.toInt(), // red
        0xFFF4511E.toInt(), // orange
        0xFFF9A825.toInt(), // amber
        0xFF43A047.toInt(), // green
    )
}
