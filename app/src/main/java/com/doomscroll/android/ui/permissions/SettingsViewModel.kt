package com.doomscroll.android.ui.permissions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Today's dashboard pauses: how many are left out of the day's allowance. */
data class PauseAllowance(val remaining: Int, val allowance: Int)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val breakManager: MindfulBreakManager
) : ViewModel() {

    val isStrictLockdownEnabled: StateFlow<Boolean> = settingsRepository.isStrictLockdownEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val pauseAllowance: StateFlow<PauseAllowance> =
        combine(settingsRepository.isStrictLockdownEnabled, breakManager.breaksUsedToday) { strict, _ ->
            PauseAllowance(breakManager.getGlobalPausesRemaining(strict), breakManager.globalPauseAllowance())
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PauseAllowance(MindfulBreakManager.MAX_GLOBAL_PAUSES_PER_DAY, MindfulBreakManager.MAX_GLOBAL_PAUSES_PER_DAY)
        )

    /** Turning it off must go through the UI's 60 s confirmation first. */
    fun setStrictLockdown(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStrictLockdownEnabled(enabled) }
    }

    /** Adds [MindfulBreakManager.EXTRA_PAUSES_PER_GRANT] pauses for today. The UI puts this behind a 60 s wait. */
    fun addPauseForToday() {
        breakManager.grantExtraGlobalPause(isStrictLockdownEnabled.value)
    }
}
