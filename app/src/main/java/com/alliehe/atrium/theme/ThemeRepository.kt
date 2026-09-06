package com.alliehe.atrium.theme

import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.PerformanceMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bridges UserPrefs → theme inputs.
 * WFF seed (Phase 5) merges here via :app; :core:theme stays free of watchface.
 */
data class ThemePrefs(
    val performanceMode: PerformanceMode = PerformanceMode.Balanced,
    val seedColorArgb: Long? = null,
)

@Singleton
class ThemeRepository @Inject constructor(
    userPrefsRepository: UserPrefsRepository,
) {
    val themePrefs: Flow<ThemePrefs> = userPrefsRepository.prefs.map { prefs ->
        ThemePrefs(
            performanceMode = prefs.effectivePerformanceMode,
            seedColorArgb = prefs.seedColorArgb,
        )
    }
}
