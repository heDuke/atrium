package com.alliehe.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alliehe.core.color.ThemeSource
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.LayoutDensity
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

internal val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
)

internal object UserPrefsKeys {
    val PINNED = stringSetPreferencesKey("pinned")
    val FAVORITES = stringSetPreferencesKey("favorites")
    val HIDDEN = stringSetPreferencesKey("hidden")
    val DRAWER_LAYOUT = stringPreferencesKey("drawer_layout")
    val LAYOUT_DENSITY = stringPreferencesKey("layout_density")
    val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
    val LAST_NON_SAVER = stringPreferencesKey("last_non_saver")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val SEED_COLOR = longPreferencesKey("seed_color")
    val THEME_SOURCE = stringPreferencesKey("theme_source")
    val THEMED_ICONS = booleanPreferencesKey("themed_icons")
    /** Newline-separated component keys (keys never contain '\n'). */
    val RECENTS = stringPreferencesKey("recents")
    val HONEYCOMB_ORDER = stringPreferencesKey("honeycomb_order")
}

internal fun String?.toKeyList(): List<String> = this?.split('\n')?.filter { it.isNotEmpty() }.orEmpty()
internal fun List<String>.toKeyString(): String = joinToString("\n")

internal fun Preferences.toUserPrefs(): UserPrefs {
    val performance = enumValueOr(this[UserPrefsKeys.PERFORMANCE_MODE], PerformanceMode.Balanced)
    val lastNonSaver = enumValueOr(this[UserPrefsKeys.LAST_NON_SAVER], PerformanceMode.Balanced)
        .takeUnless { it == PerformanceMode.PowerSaver }
        ?: PerformanceMode.Balanced
    return UserPrefs(
        pinnedPackages = this[UserPrefsKeys.PINNED].orEmpty(),
        favoritePackages = this[UserPrefsKeys.FAVORITES].orEmpty(),
        hiddenPackages = this[UserPrefsKeys.HIDDEN].orEmpty(),
        drawerLayout = enumValueOr(this[UserPrefsKeys.DRAWER_LAYOUT], DrawerLayoutMode.List),
        layoutDensity = enumValueOr(this[UserPrefsKeys.LAYOUT_DENSITY], LayoutDensity.Standard),
        performanceMode = performance,
        lastNonSaverMode = lastNonSaver,
        reduceMotion = this[UserPrefsKeys.REDUCE_MOTION] ?: false,
        onboardingCompleted = this[UserPrefsKeys.ONBOARDING_DONE] ?: false,
        seedColorArgb = this[UserPrefsKeys.SEED_COLOR],
        themeSource = enumValueOr(this[UserPrefsKeys.THEME_SOURCE], ThemeSource.System),
        themedIcons = this[UserPrefsKeys.THEMED_ICONS] ?: false,
        recentComponents = this[UserPrefsKeys.RECENTS].toKeyList(),
        honeycombOrder = this[UserPrefsKeys.HONEYCOMB_ORDER].toKeyList(),
    )
}

internal inline fun <reified T : Enum<T>> enumValueOr(raw: String?, fallback: T): T {
    if (raw == null) return fallback
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}

/**
 * Non-Hilt snapshot for TileService / other process contexts that share the app DataStore.
 */
object UserPrefsReader {
    private const val READ_TIMEOUT_MS = 500L

    fun currentOrDefault(context: Context): UserPrefs =
        runCatching {
            runBlocking {
                withTimeout(READ_TIMEOUT_MS) {
                    context.applicationContext.userPrefsDataStore.data.first().toUserPrefs()
                }
            }
        }.getOrDefault(UserPrefs())
}
