package com.doomscroll.android.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscroll.android.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val isStrictLockdownEnabled: StateFlow<Boolean> = settingsRepository.isStrictLockdownEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Turning it off must go through the UI's 60 s confirmation first. */
    fun setStrictLockdown(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStrictLockdownEnabled(enabled) }
    }
}
