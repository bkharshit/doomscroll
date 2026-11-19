package com.doomscroll.android.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.doomscroll.android.core.util.PermissionHelper
import com.doomscroll.android.data.repository.MetricsRepository
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.data.repository.UsageRepository
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val usageRepository: UsageRepository,
    private val metricsRepository: MetricsRepository,
    private val breakManager: MindfulBreakManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        observeRepositories()
        refreshPermissions()
    }

    private fun observeRepositories() {
        viewModelScope.launch {
            metricsRepository.ensureDailyReset()
        }

        viewModelScope.launch {
            combine(
                settingsRepository.isMasterShieldEnabled,
                settingsRepository.isReelsInterceptionEnabled,
                settingsRepository.isShortsInterceptionEnabled,
                metricsRepository.reelsBlockedToday,
                metricsRepository.allTimeBlocked
            ) { masterEnabled, reelsEnabled, shortsEnabled, reelsToday, allTime ->
                val timeSavedMin = (reelsToday * 45) / 60
                _uiState.update { current ->
                    current.copy(
                        isMasterShieldActive = masterEnabled,
                        isReelsInterceptionActive = reelsEnabled,
                        isShortsInterceptionActive = shortsEnabled,
                        reelsBlockedToday = reelsToday,
                        allTimeBlocked = allTime,
                        totalTimeSavedMinutes = timeSavedMin
                    )
                }
            }.collect {}
        }

        viewModelScope.launch {
            breakManager.breakSession.collect { session ->
                _uiState.update { it.copy(breakSession = session) }
            }
        }

        viewModelScope.launch {
            settingsRepository.getAppLimits().collect { limits ->
                _uiState.update { it.copy(appLimits = limits) }
            }
        }

        viewModelScope.launch {
            loadUsageStats()
        }
    }

    fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.ToggleMasterShield -> {
                viewModelScope.launch {
                    settingsRepository.setMasterShieldEnabled(intent.enabled)
                }
            }
            is DashboardIntent.ToggleReelsInterception -> {
                viewModelScope.launch {
                    settingsRepository.setReelsInterceptionEnabled(intent.enabled)
                }
            }
            is DashboardIntent.ToggleShortsInterception -> {
                viewModelScope.launch {
                    settingsRepository.setShortsInterceptionEnabled(intent.enabled)
                }
            }
            DashboardIntent.ShowBreakDialog -> {
                _uiState.update { it.copy(isBreakDialogVisible = true) }
            }
            DashboardIntent.DismissBreakDialog -> {
                _uiState.update { it.copy(isBreakDialogVisible = false) }
            }
            is DashboardIntent.StartBreak -> {
                breakManager.startBreak(
                    durationMinutes = intent.minutes,
                    intention = intent.intention
                )
            }
            DashboardIntent.CancelBreak -> {
                breakManager.cancelBreak()
            }
            DashboardIntent.RefreshPermissionsAndStats -> {
                refreshPermissions()
                loadUsageStats()
            }
        }
    }

    fun refreshPermissions() {
        val hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(context)
        val hasUsage = PermissionHelper.isUsageAccessGranted(context)
        val hasOverlay = PermissionHelper.isOverlayPermissionGranted(context)

        _uiState.update {
            it.copy(
                isAccessibilityPermissionGranted = hasAccessibility,
                isUsagePermissionGranted = hasUsage,
                isOverlayPermissionGranted = hasOverlay
            )
        }
    }

    private fun loadUsageStats() {
        viewModelScope.launch {
            val limitsMap = _uiState.value.appLimits.associate { it.monitoredApp.packageName to it.dailyLimitMinutes }
            val blockedMap = mapOf("com.instagram.android" to _uiState.value.reelsBlockedToday)
            val stats = usageRepository.getUsageStatsForMonitoredApps(limitsMap, blockedMap)
            _uiState.update { it.copy(appUsageStats = stats) }
        }
    }

    class Factory(
        private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val usageRepository: UsageRepository,
        private val metricsRepository: MetricsRepository,
        private val breakManager: MindfulBreakManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                context, settingsRepository, usageRepository, metricsRepository, breakManager
            ) as T
        }
    }
}
