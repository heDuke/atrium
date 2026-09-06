package com.alliehe.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.InstalledAppsRepository
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.DrawerLayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DrawerAppItem(
    val entry: AppEntry,
    val pinned: Boolean,
    val favorite: Boolean,
)

data class DrawerUiState(
    val apps: List<DrawerAppItem> = emptyList(),
    val layout: DrawerLayoutMode = DrawerLayoutMode.List,
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val userPrefsRepository: UserPrefsRepository,
) : ViewModel() {

    init {
        installedAppsRepository.refresh()
    }

    val uiState: StateFlow<DrawerUiState> = combine(
        installedAppsRepository.launcherApps,
        userPrefsRepository.prefs,
    ) { catalog, prefs ->
        val visible = catalog.filterNot { it.packageName in prefs.hiddenPackages }
        val sorted = visible.sortedWith(
            compareByDescending<AppEntry> { it.packageName in prefs.pinnedPackages }
                .thenByDescending { it.packageName in prefs.favoritePackages }
                .thenBy { it.label.lowercase() },
        )
        DrawerUiState(
            apps = sorted.map { entry ->
                DrawerAppItem(
                    entry = entry,
                    pinned = entry.packageName in prefs.pinnedPackages,
                    favorite = entry.packageName in prefs.favoritePackages,
                )
            },
            layout = prefs.drawerLayout,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DrawerUiState(),
    )

    fun setPinned(packageName: String, pinned: Boolean) {
        viewModelScope.launch { userPrefsRepository.pinPackage(packageName, pinned) }
    }

    fun setFavorite(packageName: String, favorite: Boolean) {
        viewModelScope.launch { userPrefsRepository.favoritePackage(packageName, favorite) }
    }

    fun setHidden(packageName: String, hidden: Boolean) {
        viewModelScope.launch { userPrefsRepository.hidePackage(packageName, hidden) }
    }
}
