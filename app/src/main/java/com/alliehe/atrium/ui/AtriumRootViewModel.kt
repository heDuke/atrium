package com.alliehe.atrium.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alliehe.core.data.UserPrefsRepository
import com.alliehe.core.model.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AtriumRootViewModel @Inject constructor(
    userPrefsRepository: UserPrefsRepository,
) : ViewModel() {

    val prefs: StateFlow<UserPrefs?> = userPrefsRepository.prefs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )
}
