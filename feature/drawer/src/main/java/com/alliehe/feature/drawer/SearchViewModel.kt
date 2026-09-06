package com.alliehe.feature.drawer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.InstalledAppsRepository
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.AppEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val results: List<AppEntry> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    installedAppsRepository: InstalledAppsRepository,
    userPrefsRepository: UserPrefsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        userPrefsRepository.prefs,
    ) { q, prefs ->
        val needle = q.trim()
        val apps = installedAppsRepository.queryLauncherApps()
            .filterNot { it.packageName in prefs.hiddenPackages }
            .filter { entry ->
                needle.isEmpty() ||
                    entry.label.contains(needle, ignoreCase = true) ||
                    entry.packageName.contains(needle, ignoreCase = true)
            }
            .sortedBy { it.label.lowercase() }
        SearchUiState(query = q, results = apps)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }
}
