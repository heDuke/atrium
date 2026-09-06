package com.alliehe.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_prefs",
)

@Singleton
class UserPrefsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    val prefs: Flow<UserPrefs> = context.userPrefsDataStore.data.map { it.toUserPrefs() }

    suspend fun setPerformanceMode(mode: PerformanceMode) {
        mutateUserPrefs { it.withPerformanceMode(mode) }
    }

    /**
     * G5 one-tap power saver: on → PowerSaver; off → restore lastNonSaverMode.
     */
    suspend fun setPowerSaverEnabled(enabled: Boolean) {
        mutateUserPrefs { it.withPowerSaverEnabled(enabled) }
    }

    /**
     * G3 reduce motion: true forces PowerSaver motion tier + locks mode;
     * false restores lastNonSaverMode.
     */
    suspend fun setReduceMotion(enabled: Boolean) {
        mutateUserPrefs { it.withReduceMotion(enabled) }
    }

    suspend fun setDrawerLayout(mode: DrawerLayoutMode) {
        context.userPrefsDataStore.edit { it[Keys.DRAWER_LAYOUT] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.userPrefsDataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }

    suspend fun setSeedColorArgb(argb: Long?) {
        context.userPrefsDataStore.edit { prefs ->
            if (argb == null) {
                prefs.remove(Keys.SEED_COLOR)
            } else {
                prefs[Keys.SEED_COLOR] = argb
            }
        }
    }

    suspend fun pinPackage(packageName: String, pinned: Boolean) {
        mutateSet(Keys.PINNED, packageName, pinned)
    }

    suspend fun favoritePackage(packageName: String, favorite: Boolean) {
        mutateSet(Keys.FAVORITES, packageName, favorite)
    }

    suspend fun hidePackage(packageName: String, hidden: Boolean) {
        mutateSet(Keys.HIDDEN, packageName, hidden)
    }

    private suspend fun mutateUserPrefs(transform: (UserPrefs) -> UserPrefs) {
        context.userPrefsDataStore.edit { prefs ->
            val next = transform(prefs.toUserPrefs())
            prefs[Keys.PERFORMANCE_MODE] = next.performanceMode.name
            prefs[Keys.LAST_NON_SAVER] = next.lastNonSaverMode.name
            prefs[Keys.REDUCE_MOTION] = next.reduceMotion
        }
    }

    private suspend fun mutateSet(key: Preferences.Key<Set<String>>, value: String, add: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (add) current + value else current - value
        }
    }

    private object Keys {
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

    private fun Preferences.toUserPrefs(): UserPrefs {
        val performance = enumValueOr(this[Keys.PERFORMANCE_MODE], PerformanceMode.Balanced)
        val lastNonSaver = enumValueOr(this[Keys.LAST_NON_SAVER], PerformanceMode.Balanced)
            .takeUnless { it == PerformanceMode.PowerSaver }
            ?: PerformanceMode.Balanced
        return UserPrefs(
            pinnedPackages = this[Keys.PINNED].orEmpty(),
            favoritePackages = this[Keys.FAVORITES].orEmpty(),
            hiddenPackages = this[Keys.HIDDEN].orEmpty(),
            drawerLayout = enumValueOr(this[Keys.DRAWER_LAYOUT], DrawerLayoutMode.List),
            performanceMode = performance,
            lastNonSaverMode = lastNonSaver,
            reduceMotion = this[Keys.REDUCE_MOTION] ?: false,
            onboardingCompleted = this[Keys.ONBOARDING_DONE] ?: false,
            seedColorArgb = this[Keys.SEED_COLOR],
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOr(raw: String?, fallback: T): T {
        if (raw == null) return fallback
        return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
    }
}
