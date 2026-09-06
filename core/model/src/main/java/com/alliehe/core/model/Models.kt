package com.alliehe.core.model

/**
 * Installed app entry with MAIN+LAUNCHER (F1 / G1).
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean = false,
)

enum class PerformanceMode {
    Performance,
    Balanced,
    PowerSaver,
}

enum class DrawerLayoutMode {
    List,
    Grid,
}

/**
 * G5 + G3 single source of truth (UserPrefs).
 */
data class UserPrefs(
    val pinnedPackages: Set<String> = emptySet(),
    val favoritePackages: Set<String> = emptySet(),
    val hiddenPackages: Set<String> = emptySet(),
    val drawerLayout: DrawerLayoutMode = DrawerLayoutMode.List,
    val performanceMode: PerformanceMode = PerformanceMode.Balanced,
    val lastNonSaverMode: PerformanceMode = PerformanceMode.Balanced,
    val reduceMotion: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val seedColorArgb: Long? = null,
) {
    /** Effective motion/performance mode after G3 reduce-motion lock. */
    val effectivePerformanceMode: PerformanceMode
        get() = if (reduceMotion) PerformanceMode.PowerSaver else performanceMode
}
