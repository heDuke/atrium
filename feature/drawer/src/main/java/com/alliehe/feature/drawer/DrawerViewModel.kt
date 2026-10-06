package com.alliehe.feature.drawer

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.InstalledAppsRepository
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.visibleApps
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
data class DrawerUiState(
    val apps: List<DrawerAppItem> = emptyList(),
    val icons: Map<String, Bitmap> = emptyMap(),
    val layout: DrawerLayoutMode = DrawerLayoutMode.List,
    val loading: Boolean = true,
    val refreshFailed: Boolean = false,
)

@HiltViewModel
class DrawerViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val userPrefsRepository: UserPrefsRepository,
) : ViewModel() {
    private val errors = Channel<Unit>(Channel.BUFFERED)
    val writeErrors = errors.receiveAsFlow()
    val uiState: StateFlow<DrawerUiState> = combine(
        installedAppsRepository.catalog, userPrefsRepository.prefs,
    ) { catalog, prefs ->
        DrawerUiState(
            apps = catalog.apps.visibleApps(prefs).map {
                DrawerAppItem(it, it.packageName in prefs.pinnedPackages, it.packageName in prefs.favoritePackages)
            },
            icons = catalog.icons,
            layout = prefs.drawerLayout,
            loading = catalog.loading,
            refreshFailed = catalog.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerUiState())

    fun refresh() = installedAppsRepository.refresh()
    fun setPinned(packageName: String, pinned: Boolean) = write { userPrefsRepository.pinPackage(packageName, pinned) }
    fun setFavorite(packageName: String, favorite: Boolean) = write { userPrefsRepository.favoritePackage(packageName, favorite) }
    fun setHidden(packageName: String, hidden: Boolean) = write { userPrefsRepository.hidePackage(packageName, hidden) }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (_: IOException) { errors.send(Unit) }
        }
    }
}
