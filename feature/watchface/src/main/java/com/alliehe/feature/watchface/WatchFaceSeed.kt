package com.alliehe.feature.watchface

/**
 * WFF side branch (Phase 5): export seed color into ThemeRepository via :app.
 * :core:theme must never depend on this module.
 */
object WatchFaceSeed {
    /** ARGB seed when WFF / user accent is available; null → theme falls back. */
    fun currentSeedArgb(): Long? = null
}
