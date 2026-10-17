package com.alliehe.core.data

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppShortcut(val packageName: String, val id: String, val label: String)

/**
 * App shortcuts (static + dynamic + pinned) via [LauncherApps]. The platform only grants shortcut
 * access to the default HOME app, so everything is gated on [available]; otherwise nothing is queried.
 */
@Singleton
class AppShortcuts @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val launcherApps: LauncherApps? get() = context.getSystemService(LauncherApps::class.java)

    fun available(): Boolean = try {
        DefaultLauncherProbe.state(context) == HomeRoleState.Held && launcherApps?.hasShortcutHostPermission() == true
    } catch (_: RuntimeException) {
        false
    }

    suspend fun forPackage(packageName: String, max: Int = MAX_SHORTCUTS): List<AppShortcut> {
        if (!available()) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val query = LauncherApps.ShortcutQuery()
                    .setPackage(packageName)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
                launcherApps?.getShortcuts(query, Process.myUserHandle()).orEmpty()
                    .filter { it.isEnabled }
                    .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
                    .take(max)
                    .map { AppShortcut(it.`package`, it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString()) }
            } catch (_: SecurityException) {
                emptyList()
            } catch (_: IllegalStateException) {
                emptyList() // user locked
            }
        }
    }

    fun start(shortcut: AppShortcut): Boolean = try {
        launcherApps?.startShortcut(shortcut.packageName, shortcut.id, null, null, Process.myUserHandle())
        launcherApps != null
    } catch (_: RuntimeException) {
        false // ActivityNotFound / Security / IllegalState
    }

    private companion object {
        const val MAX_SHORTCUTS = 4
    }
}
