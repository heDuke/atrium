package com.alliehe.feature.drawer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.AppIconLoader
import com.alliehe.core.data.InstalledAppsRepository
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.AppEntry
import com.alliehe.core.model.componentKey
import com.alliehe.core.model.visibleApps
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val results: List<AppEntry> = emptyList(),
    val loading: Boolean = true,
    val refreshFailed: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val installedAppsRepository: InstalledAppsRepository,
    private val userPrefsRepository: UserPrefsRepository,
    val iconLoader: AppIconLoader,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val query = savedStateHandle.getStateFlow("query", "")
    val uiState: StateFlow<SearchUiState> = combine(
        query, installedAppsRepository.catalog, userPrefsRepository.prefs,
    ) { q, catalog, prefs ->
        SearchUiState(q, catalog.apps.visibleApps(prefs, q, prioritize = false),
            catalog.loading, catalog.failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun onQueryChange(value: String) { savedStateHandle["query"] = value }
    fun refresh() = installedAppsRepository.refresh()
    fun recordLaunch(entry: AppEntry) {
        viewModelScope.launch { runCatching { userPrefsRepository.recordLaunch(entry.componentKey) } }
    }
}
