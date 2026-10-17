package com.alliehe.core.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.alliehe.core.color.ThemeSource
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.recordRecent
import com.alliehe.core.model.LayoutDensity
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
        context.userPrefsDataStore.edit { it[UserPrefsKeys.DRAWER_LAYOUT] = mode.name }
    }

    suspend fun setLayoutDensity(density: LayoutDensity) {
        context.userPrefsDataStore.edit { it[UserPrefsKeys.LAYOUT_DENSITY] = density.name }
    }

    /** OOBE finish: persist the chosen layout and the done flag in one transaction. */
    suspend fun completeOnboarding(layout: DrawerLayoutMode) {
        context.userPrefsDataStore.edit {
            it[UserPrefsKeys.DRAWER_LAYOUT] = layout.name
            it[UserPrefsKeys.ONBOARDING_DONE] = true
        }
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

    suspend fun setThemeSource(source: ThemeSource) {
        context.userPrefsDataStore.edit { it[UserPrefsKeys.THEME_SOURCE] = source.name }
    }

    /** Picking a preset also switches the source to Seed so the choice is visible immediately. */
    suspend fun selectSeed(argb: Int) {
        context.userPrefsDataStore.edit {
            it[UserPrefsKeys.SEED_COLOR] = argb.toLong() and 0xFFFFFFFFL
            it[UserPrefsKeys.THEME_SOURCE] = ThemeSource.Seed.name
        }
    }

    suspend fun setThemedIcons(enabled: Boolean) {
        context.userPrefsDataStore.edit { it[UserPrefsKeys.THEMED_ICONS] = enabled }
    }

    /** Recents: most recent first, de-duplicated, capped at [com.alliehe.core.model.RECENTS_MAX]. */
    suspend fun recordLaunch(componentKey: String) {
        context.userPrefsDataStore.edit {
            it[UserPrefsKeys.RECENTS] = recordRecent(it[UserPrefsKeys.RECENTS].toKeyList(), componentKey).toKeyString()
        }
    }

    suspend fun clearRecents() {
        context.userPrefsDataStore.edit { it.remove(UserPrefsKeys.RECENTS) }
    }

    suspend fun setHoneycombOrder(order: List<String>) {
        context.userPrefsDataStore.edit {
            if (order.isEmpty()) it.remove(UserPrefsKeys.HONEYCOMB_ORDER)
            else it[UserPrefsKeys.HONEYCOMB_ORDER] = order.toKeyString()
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

    private suspend fun mutateUserPrefs(transform: (UserPrefs) -> UserPrefs) {
        context.userPrefsDataStore.edit { prefs ->
            val next = transform(prefs.toUserPrefs())
            prefs[UserPrefsKeys.PERFORMANCE_MODE] = next.performanceMode.name
            prefs[UserPrefsKeys.LAST_NON_SAVER] = next.lastNonSaverMode.name
            prefs[UserPrefsKeys.REDUCE_MOTION] = next.reduceMotion
        }
    }

    private suspend fun mutateSet(key: Preferences.Key<Set<String>>, value: String, add: Boolean) {
        context.userPrefsDataStore.edit { prefs ->
            val current = prefs[key].orEmpty()
            prefs[key] = if (add) current + value else current - value
        }
    }
}
