package com.alliehe.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.AppIconLoader
import com.alliehe.core.data.AppShortcut
import com.alliehe.core.data.AppShortcuts
import com.alliehe.core.data.InstalledAppsRepository
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.LayoutDensity
import com.alliehe.core.model.componentKey
import com.alliehe.core.model.moved
import com.alliehe.core.model.sectioned
import com.alliehe.core.model.withCustomOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawerAppItem(val entry: AppEntry, val pinned: Boolean, val favorite: Boolean)

enum class SectionKind { Recents, Pinned, Favorites, All }

data class DrawerSection(val kind: SectionKind, val apps: List<DrawerAppItem>) {
    /** TLC keys must be unique: an app can appear in Recents and again in its own section. */
    fun itemKey(app: DrawerAppItem): String =
        if (kind == SectionKind.Recents) "recent-${app.entry.componentKey}" else app.entry.componentKey
}

/** Pure data for every [com.alliehe.feature.drawer.layout.AppListLayout]; rendering lives in the layouts. */
data class DrawerUiState(
    val sections: List<DrawerSection> = emptyList(),
    /** Honeycomb order: pinned → favourites → all, overridden by the user's drag-arranged order. */
    val honeycombApps: List<DrawerAppItem> = emptyList(),
    val layout: DrawerLayoutMode = DrawerLayoutMode.List,
    val density: LayoutDensity = LayoutDensity.Standard,
    val loading: Boolean = true,
    val refreshFailed: Boolean = false,
) {
    /** Every visible app once (Recents excluded). */
    val apps: List<DrawerAppItem> get() = sections.filter { it.kind != SectionKind.Recents }.flatMap { it.apps }
}

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val userPrefsRepository: UserPrefsRepository,
    private val shortcuts: AppShortcuts,
    val iconLoader: AppIconLoader,
) : ViewModel() {
    private val errors = Channel<Unit>(Channel.BUFFERED)
    val writeErrors = errors.receiveAsFlow()
    val uiState: StateFlow<DrawerUiState> = combine(
        installedAppsRepository.catalog, userPrefsRepository.prefs,
    ) { catalog, prefs ->
        val s = catalog.apps.sectioned(prefs)
        fun List<AppEntry>.items() = map {
            DrawerAppItem(it, it.packageName in prefs.pinnedPackages, it.packageName in prefs.favoritePackages)
        }
        val sections = listOf(
            DrawerSection(SectionKind.Recents, s.recents.items()),
            DrawerSection(SectionKind.Pinned, s.pinned.items()),
            DrawerSection(SectionKind.Favorites, s.favorites.items()),
            DrawerSection(SectionKind.All, s.all.items()),
        ).filter { it.apps.isNotEmpty() }
        DrawerUiState(
            sections = sections,
            honeycombApps = s.flattened.items().withCustomOrder(prefs.honeycombOrder) { it.entry.componentKey },
            layout = prefs.drawerLayout,
            density = prefs.layoutDensity,
            loading = catalog.loading,
            refreshFailed = catalog.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    fun refresh() = installedAppsRepository.refresh()
    fun setPinned(packageName: String, pinned: Boolean) = write { userPrefsRepository.pinPackage(packageName, pinned) }
    fun setFavorite(packageName: String, favorite: Boolean) = write { userPrefsRepository.favoritePackage(packageName, favorite) }
    fun setHidden(packageName: String, hidden: Boolean) = write { userPrefsRepository.hidePackage(packageName, hidden) }
    fun recordLaunch(entry: AppEntry) = write { userPrefsRepository.recordLaunch(entry.componentKey) }

    /** Honeycomb drag-reorder: persists the full arranged order so it survives catalog changes. */
    fun moveHoneycomb(from: Int, to: Int) {
        val keys = uiState.value.honeycombApps.map { it.entry.componentKey }
        val next = keys.moved(from, to)
        if (next != keys) write { userPrefsRepository.setHoneycombOrder(next) }
    }

    fun shortcutsAvailable(): Boolean = shortcuts.available()
    suspend fun shortcutsFor(packageName: String): List<AppShortcut> = shortcuts.forPackage(packageName)
    fun startShortcut(shortcut: AppShortcut): Boolean = shortcuts.start(shortcut)

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (_: IOException) { errors.send(Unit) }
        }
    }
}
