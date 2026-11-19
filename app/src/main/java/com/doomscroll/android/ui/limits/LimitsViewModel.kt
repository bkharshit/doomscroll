package com.doomscroll.android.ui.limits

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.DayUsageData
import com.doomscroll.android.core.model.UsageStat
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.data.repository.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LimitsUiState(
    val appLimits: List<AppLimit> = emptyList(),
    val usageStats: Map<String, UsageStat> = emptyMap(),
    val weeklyUsage: List<DayUsageData> = emptyList(),
    val isReelsBlockerEnabled: Boolean = true,
    val isShortsBlockerEnabled: Boolean = true
)

@HiltViewModel
class LimitsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LimitsUiState())
    val uiState: StateFlow<LimitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.getAppLimits().collect { limits ->
                _uiState.update { it.copy(appLimits = limits) }
            }
        }
        viewModelScope.launch {
            settingsRepository.isReelsInterceptionEnabled.collect { enabled ->
                _uiState.update { it.copy(isReelsBlockerEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            settingsRepository.isShortsInterceptionEnabled.collect { enabled ->
                _uiState.update { it.copy(isShortsBlockerEnabled = enabled) }
            }
        }
        loadUsage()
    }

    fun loadUsage() {
        viewModelScope.launch {
            val limitsMap = _uiState.value.appLimits.associate { it.monitoredApp.packageName to it.dailyLimitMinutes }
            val stats = usageRepository.getUsageStatsForMonitoredApps(limitsMap, emptyMap())
            val weekly = usageRepository.getWeeklyUsageHistory()
            _uiState.update {
                it.copy(
                    usageStats = stats.associateBy { stat -> stat.monitoredApp.packageName },
                    weeklyUsage = weekly
                )
            }
        }
    }

    fun updateLimit(packageName: String, minutes: Int) {
        // Optimistically update in-memory state for immediate fluid slider response
        _uiState.update { current ->
            val updatedLimits = current.appLimits.map { limit ->
                if (limit.monitoredApp.packageName == packageName) {
                    limit.copy(dailyLimitMinutes = minutes)
                } else limit
            }
            current.copy(appLimits = updatedLimits)
        }
        viewModelScope.launch {
            settingsRepository.setDailyLimit(packageName, minutes)
        }
    }

    fun toggleAppBlock(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAppBlockEnabled(packageName, enabled)
        }
    }

    fun toggleReelsBlocker(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReelsInterceptionEnabled(enabled)
        }
    }

    fun toggleShortsBlocker(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setShortsInterceptionEnabled(enabled)
        }
    }
}
