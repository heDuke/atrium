package com.alliehe.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.AppIconLoader
import com.alliehe.core.data.InstalledAppsRepository
import com.alliehe.core.model.componentKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapLatest
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.AppEntry
import android.graphics.Bitmap
import com.alliehe.core.model.DrawerLayoutMode
import com.alliehe.core.model.LayoutDensity
import kotlinx.coroutines.flow.map
import com.alliehe.core.model.PerformanceMode
import com.alliehe.core.model.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val ONBOARDING_ICON_PX = 64

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPrefsRepository: UserPrefsRepository,
    installedAppsRepository: InstalledAppsRepository,
    private val iconLoader: AppIconLoader,
) : ViewModel() {
    val prefs: StateFlow<UserPrefs?> = userPrefsRepository.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hiddenApps: StateFlow<List<AppEntry>> = combine(
        installedAppsRepository.catalog, userPrefsRepository.prefs,
    ) { catalog, prefs ->
        catalog.apps.filter { it.packageName in prefs.hiddenPackages }
            .distinctBy { it.packageName }.sortedBy { it.label }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** A few installed app icons for the OOBE welcome pop-in. */
    val onboardingIcons: StateFlow<List<Bitmap>> = installedAppsRepository.catalog
        .map { catalog -> catalog.apps.take(5).map { it.componentKey }.joinToString() to catalog.apps.take(5) }
        .distinctUntilChangedBy { it.first }
        .mapLatest { (_, apps) -> apps.mapNotNull { iconLoader.load(it, ONBOARDING_ICON_PX) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val errors = Channel<Unit>(Channel.BUFFERED)
    val writeErrors = errors.receiveAsFlow()
    private val _savingOnboarding = MutableStateFlow(false)
    val savingOnboarding = _savingOnboarding.asStateFlow()

    fun setDrawerLayout(mode: DrawerLayoutMode) = write { userPrefsRepository.setDrawerLayout(mode) }
    fun setLayoutDensity(density: LayoutDensity) = write { userPrefsRepository.setLayoutDensity(density) }
    fun setPerformanceMode(mode: PerformanceMode) = write { userPrefsRepository.setPerformanceMode(mode) }
    fun setPowerSaverEnabled(enabled: Boolean) = write { userPrefsRepository.setPowerSaverEnabled(enabled) }
    fun setReduceMotion(enabled: Boolean) = write { userPrefsRepository.setReduceMotion(enabled) }
    fun restoreHidden(packageName: String) = write { userPrefsRepository.hidePackage(packageName, false) }

    fun completeOnboarding(layout: DrawerLayoutMode, onCompleted: () -> Unit) {
        if (_savingOnboarding.value) return
        _savingOnboarding.value = true
        viewModelScope.launch {
            try {
                userPrefsRepository.completeOnboarding(layout)
                onCompleted()
            } catch (_: IOException) {
                errors.send(Unit)
            } finally {
                _savingOnboarding.value = false
            }
        }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (_: IOException) { errors.send(Unit) }
        }
    }
}
