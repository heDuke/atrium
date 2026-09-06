package com.alliehe.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alliehe.core.model.DrawerLayoutMode
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
    val PERFORMANCE_MODE = stringPreferencesKey("performance_mode")
    val LAST_NON_SAVER = stringPreferencesKey("last_non_saver")
    val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
    val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
    val SEED_COLOR = longPreferencesKey("seed_color")
}

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
        performanceMode = performance,
        lastNonSaverMode = lastNonSaver,
        reduceMotion = this[UserPrefsKeys.REDUCE_MOTION] ?: false,
        onboardingCompleted = this[UserPrefsKeys.ONBOARDING_DONE] ?: false,
        seedColorArgb = this[UserPrefsKeys.SEED_COLOR],
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
