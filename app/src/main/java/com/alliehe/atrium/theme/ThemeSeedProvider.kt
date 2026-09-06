package com.alliehe.atrium.theme

import com.alliehe.feature.watchface.WatchFaceSeed

/**
 * Bridges WFF seed and UserPrefs without `:core:theme` depending on `:feature:watchface`.
 * Order: [WatchFaceSeed.currentSeedArgb] → prefs seed.
 */
object ThemeSeedProvider {
    fun resolveSeedArgb(prefsSeedArgb: Long?): Long? =
        WatchFaceSeed.currentSeedArgb() ?: prefsSeedArgb
}
