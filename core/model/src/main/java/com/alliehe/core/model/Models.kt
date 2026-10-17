package com.alliehe.core.model

import com.alliehe.core.color.ThemeSource

/**
 * Installed app entry with MAIN+LAUNCHER (F1 / G1).
 */
data class AppEntry(
    val packageName: String,
    val activityName: String,
    val label: String,
    val isSystemApp: Boolean = false,
    /** Lower-case ASCII romanization of [label] (Han → pinyin), used for sorting and search. */
    val romanized: String = "",
    /** First letter of each romanized word ("微信" → "wx", "Google Maps" → "gm"). */
    val initials: String = "",
)

enum class PerformanceMode {
    Performance,
    Balanced,
    PowerSaver,
}

enum class DrawerLayoutMode {
    List,
    Grid,
    Honeycomb,
}

/** Row/cell density for List and Grid layouts (Honeycomb ignores it). */
enum class LayoutDensity {
    Standard,
    Compact,
}

/**
 * G5 + G3 single source of truth (UserPrefs).
 */
data class UserPrefs(
    val pinnedPackages: Set<String> = emptySet(),
    val favoritePackages: Set<String> = emptySet(),
    val hiddenPackages: Set<String> = emptySet(),
    val drawerLayout: DrawerLayoutMode = DrawerLayoutMode.List,
    val layoutDensity: LayoutDensity = LayoutDensity.Standard,
    val performanceMode: PerformanceMode = PerformanceMode.Balanced,
    val lastNonSaverMode: PerformanceMode = PerformanceMode.Balanced,
    val reduceMotion: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val seedColorArgb: Long? = null,
    val themeSource: ThemeSource = ThemeSource.System,
    /** Tint adaptive icons that ship a monochrome layer with the theme (off by default). */
    val themedIcons: Boolean = false,
    /** Most recent first; component keys, at most [RECENTS_MAX]. */
    val recentComponents: List<String> = emptyList(),
    /** User-arranged honeycomb order (component keys); apps not listed follow in default order. */
    val honeycombOrder: List<String> = emptyList(),
) {
    /** Effective motion/performance mode after G3 reduce-motion lock. */
    val effectivePerformanceMode: PerformanceMode
        get() = if (reduceMotion) PerformanceMode.PowerSaver else performanceMode

    /** G5: set tier; entering PowerSaver remembers the prior non-saver tier. */
    fun withPerformanceMode(mode: PerformanceMode): UserPrefs {
        val nextLast = when (mode) {
            PerformanceMode.PowerSaver ->
                performanceMode.takeUnless { it == PerformanceMode.PowerSaver }
                    ?: lastNonSaverMode
            else -> mode
        }.coerceNonSaver()
        return copy(performanceMode = mode, lastNonSaverMode = nextLast)
    }

    /** G5 one-tap power saver: on → PowerSaver; off → restore lastNonSaverMode. */
    fun withPowerSaverEnabled(enabled: Boolean): UserPrefs =
        if (enabled) {
            val nextLast = if (performanceMode != PerformanceMode.PowerSaver) {
                performanceMode
            } else {
                lastNonSaverMode
            }.coerceNonSaver()
            copy(
                performanceMode = PerformanceMode.PowerSaver,
                lastNonSaverMode = nextLast,
            )
        } else {
            copy(performanceMode = lastNonSaverMode.coerceNonSaver())
        }

    /**
     * G3 reduce motion: true forces PowerSaver tier + locks effective mode;
     * false restores lastNonSaverMode.
     */
    fun withReduceMotion(enabled: Boolean): UserPrefs {
        if (enabled) {
            val nextLast = if (performanceMode != PerformanceMode.PowerSaver) {
                performanceMode
            } else {
                lastNonSaverMode
            }.coerceNonSaver()
            return copy(
                reduceMotion = true,
                performanceMode = PerformanceMode.PowerSaver,
                lastNonSaverMode = nextLast,
            )
        }
        return copy(
            reduceMotion = false,
            performanceMode = lastNonSaverMode.coerceNonSaver(),
        )
    }
}

private fun PerformanceMode.coerceNonSaver(): PerformanceMode =
    takeUnless { it == PerformanceMode.PowerSaver } ?: PerformanceMode.Balanced

const val RECENTS_MAX = 5

/** Moves [componentKey] to the front of [recents], de-duplicated and capped at [max]. */
fun recordRecent(recents: List<String>, componentKey: String, max: Int = RECENTS_MAX): List<String> =
    (listOf(componentKey) + recents.filter { it != componentKey }).take(max)
