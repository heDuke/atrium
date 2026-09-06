package com.alliehe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefsRepository: UserPrefsRepository,
) : ViewModel() {

    val prefs: StateFlow<UserPrefs> = userPrefsRepository.prefs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserPrefs(),
    )

    fun setDrawerLayout(mode: DrawerLayoutMode) {
        viewModelScope.launch { userPrefsRepository.setDrawerLayout(mode) }
    }

    fun setPerformanceMode(mode: PerformanceMode) {
        viewModelScope.launch { userPrefsRepository.setPerformanceMode(mode) }
    }

    fun setPowerSaverEnabled(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setPowerSaverEnabled(enabled) }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { userPrefsRepository.setReduceMotion(enabled) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { userPrefsRepository.setOnboardingCompleted(true) }
    }
}
