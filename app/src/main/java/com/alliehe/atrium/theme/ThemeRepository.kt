package com.alliehe.atrium.theme

import com.alliehe.core.color.ThemeSource
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.PerformanceMode
import com.alliehe.feature.watchface.WatchFaceSeed
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * All theme inputs in one flow: UserPrefs (source, preset seed, motion, themed icons) + the watch-face
 * seed [StateFlow]. `:core:theme` stays free of `:feature:watchface`; this is the only bridge.
 */
data class ThemePrefs(
    val performanceMode: PerformanceMode = PerformanceMode.Balanced,
    val reduceMotion: Boolean = false,
    val themeSource: ThemeSource = ThemeSource.System,
    val userSeedArgb: Int? = null,
    val watchFaceSeedArgb: Int? = null,
    val themedIcons: Boolean = false,
)

@Singleton
class ThemeRepository @Inject constructor(
    userPrefsRepository: UserPrefsRepository,
) {
    val themePrefs: Flow<ThemePrefs> = combine(userPrefsRepository.prefs, WatchFaceSeed.seed) { prefs, face ->
        ThemePrefs(
            performanceMode = prefs.effectivePerformanceMode,
            reduceMotion = prefs.reduceMotion,
            themeSource = prefs.themeSource,
            userSeedArgb = prefs.seedColorArgb?.toInt(),
            watchFaceSeedArgb = face,
            themedIcons = prefs.themedIcons,
        )
    }.distinctUntilChanged()
}
