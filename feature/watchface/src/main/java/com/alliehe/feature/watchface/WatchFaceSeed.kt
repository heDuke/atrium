package com.alliehe.feature.watchface

import android.graphics.Bitmap
import com.alliehe.core.color.nativeimpl.NativeSchemeGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watch-face seed for theming, exposed as a [StateFlow] so the theme re-colours as soon as a seed is
 * published (no polling, no restart).
 *
 * Sources, highest first: a runtime [publishSeedArgb] / [publishFromBitmap] (Atrium's own face or an
 * editor preview), then the seed embedded in Atrium's WFF package (none ships yet, see `docs/WFF.md`).
 *
 * `:core:theme` must never depend on this module — `:app` combines it into the theme inputs.
 */
object WatchFaceSeed {
    private val published = MutableStateFlow<Int?>(null)

    /** Current seed (`0xAARRGGBB`) or null → theme falls back to the user seed / brand. */
    val seed: StateFlow<Int?> = published.asStateFlow()

    fun currentSeedArgb(): Int? = published.value ?: readEmbeddedWffSeedOrNull()

    /** Publish (or clear with null) a seed colour. */
    fun publishSeedArgb(argb: Int?) {
        published.value = argb ?: readEmbeddedWffSeedOrNull()
    }

    /**
     * Derives the seed from a rendered face / background image via the native extractor
     * (≤ 64×64 sampling, no pixel copy). Gray or empty images clear the seed. Returns the seed.
     */
    fun publishFromBitmap(bitmap: Bitmap, generator: NativeSchemeGenerator): Int? {
        val seed = generator.dominantColor(bitmap)
        publishSeedArgb(seed)
        return seed
    }

    /** Clears a runtime override (tests / reset). Does not affect an embedded WFF seed. */
    fun clearPublishedSeed() {
        published.value = readEmbeddedWffSeedOrNull()
    }

    /**
     * No WFF package ships yet. When one does, return its designer seed here (parsed once) — or derive it
     * from its preview image with [publishFromBitmap].
     */
    private fun readEmbeddedWffSeedOrNull(): Int? = null
}
