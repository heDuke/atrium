package com.alliehe.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class UserPrefsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val prefs: Flow<UserPrefs> = context.userPrefsDataStore.data.map { it.toUserPrefs() }

    suspend fun setPerformanceMode(mode: PerformanceMode) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs.toUserPrefs()
            if (mode == PerformanceMode.PowerSaver) {
                prefs[UserPrefsKeys.LAST_NON_SAVER] = current.performanceMode
                    .takeUnless { it == PerformanceMode.PowerSaver }
                    ?.name
                    ?: current.lastNonSaverMode.name
            } else {
                prefs[UserPrefsKeys.LAST_NON_SAVER] = mode.name
            }
            prefs[UserPrefsKeys.PERFORMANCE_MODE] = mode.name
        }
    }

    /**
     * G5 one-tap power saver: on → PowerSaver; off → restore lastNonSaverMode.
     */
    suspend fun setPowerSaverEnabled(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs.toUserPrefs()
            if (enabled) {
                if (current.performanceMode != PerformanceMode.PowerSaver) {
                    prefs[UserPrefsKeys.LAST_NON_SAVER] = current.performanceMode.name
                }
                prefs[UserPrefsKeys.PERFORMANCE_MODE] = PerformanceMode.PowerSaver.name
            } else {
                prefs[UserPrefsKeys.PERFORMANCE_MODE] = current.lastNonSaverMode.name
            }
        }
    }

    /**
     * G3 reduce motion: true forces PowerSaver motion tier + locks mode;
     * false restores lastNonSaverMode.
     */
    suspend fun setReduceMotion(enabled: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs.toUserPrefs()
            prefs[UserPrefsKeys.REDUCE_MOTION] = enabled
            if (enabled) {
                if (current.performanceMode != PerformanceMode.PowerSaver) {
                    prefs[UserPrefsKeys.LAST_NON_SAVER] = current.performanceMode.name
                }
                prefs[UserPrefsKeys.PERFORMANCE_MODE] = PerformanceMode.PowerSaver.name
            } else {
                prefs[UserPrefsKeys.PERFORMANCE_MODE] = current.lastNonSaverMode.name
            }
        }
    }

    suspend fun setDrawerLayout(mode: DrawerLayoutMode) {
        context.userPrefsDataStore.edit { it[UserPrefsKeys.DRAWER_LAYOUT] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.userPrefsDataStore.edit { it[UserPrefsKeys.ONBOARDING_DONE] = completed }
    }

    suspend fun setSeedColorArgb(argb: Long?) {
        context.userPrefsDataStore.edit { prefs ->
            if (argb == null) {
                prefs.remove(UserPrefsKeys.SEED_COLOR)
            } else {
                prefs[UserPrefsKeys.SEED_COLOR] = argb
            }
        }
    }

    suspend fun pinPackage(packageName: String, pinned: Boolean) {
        mutateSet(UserPrefsKeys.PINNED, packageName, pinned)
    }

    suspend fun favoritePackage(packageName: String, favorite: Boolean) {
        mutateSet(UserPrefsKeys.FAVORITES, packageName, favorite)
    }

    suspend fun hidePackage(packageName: String, hidden: Boolean) {
        mutateSet(UserPrefsKeys.HIDDEN, packageName, hidden)
    }

    private suspend fun mutateSet(key: Preferences.Key<Set<String>>, value: String, add: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (add) current + value else current - value
        }
    }
}
