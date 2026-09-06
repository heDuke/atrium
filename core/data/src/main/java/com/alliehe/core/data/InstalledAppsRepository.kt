package com.alliehe.core.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.alliehe.core.model.AppEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Resolves installed MAIN+LAUNCHER activities for F1 / G1.
 * Launch must go straight to the resolved activity (F7: no trampoline).
 *
 * Catalog is exposed as a [StateFlow] so prefs updates do not re-query PackageManager.
 * Call [refresh] when the installed-app set may have changed.
 */
@Singleton
class InstalledAppsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val _launcherApps = MutableStateFlow(queryLauncherApps())
    val launcherApps: StateFlow<List<AppEntry>> = _launcherApps.asStateFlow()

    fun refresh() {
        _launcherApps.value = queryLauncherApps()
    }

    fun queryLauncherApps(): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PackageManager.ResolveInfoFlags.of(0L)
        return pm.queryIntentActivities(intent, flags)
            .mapNotNull { info ->
                val activityInfo = info.activityInfo ?: return@mapNotNull null
                AppEntry(
                    packageName = activityInfo.packageName,
                    activityName = activityInfo.name,
                    label = info.loadLabel(pm)?.toString().orEmpty().ifBlank {
                        activityInfo.packageName
                    },
                    isSystemApp = (activityInfo.applicationInfo.flags and
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
