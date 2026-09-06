package com.alliehe.feature.watchface

/**
 * Phase 5 WFF stub: export seed ARGB for theme bridging via `:app`.
 *
 * Full Watch Face Format XML / binary assets land when designer assets are ready
 * (see `docs/WFF.md`). Until then [currentSeedArgb] returns null unless a seed
 * was [publishSeedArgb] at runtime.
 *
 * `:core:theme` must never depend on this module — inject through `:app` only.
 */
object WatchFaceSeed {
    @Volatile
    private var publishedSeedArgb: Long? = null

    /**
     * Current watch-face seed as ARGB (`0xAARRGGBB` in the low 32 bits).
     * Null → callers fall back to [com.alliehe.core.model.UserPrefs.seedColorArgb].
     */
    fun currentSeedArgb(): Long? = publishedSeedArgb ?: readEmbeddedWffSeedOrNull()

    /**
     * Publish or clear a seed from a future WFF editor / face service.
     * Pass null to clear the runtime override.
     */
    fun publishSeedArgb(argb: Long?) {
        publishedSeedArgb = argb
    }

    /** Clears a runtime override (tests / reset). Does not affect embedded WFF. */
    fun clearPublishedSeed() {
        publishedSeedArgb = null
    }

    /**
     * Stub: embedded WFF XML is not shipped yet.
     * When assets land under `feature/watchface/src/main/res/raw/` (or equivalent),
     * parse the designer seed here and return it.
     */
    private fun readEmbeddedWffSeedOrNull(): Long? = null
}
