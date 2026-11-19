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
    private val breakManager: MindfulBreakManager,
    /** Interception rule id -> package, for attributing blocks to the right app. */
    private val rulePackages: Map<String, String>
) : ViewModel() {

    /** Today's blocks per package, derived from per-rule counts (never estimated). */
    private var blockedByPackage: Map<String, Int> = emptyMap()

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
        breakManager.refreshDay()

        viewModelScope.launch {
            combine(
                settingsRepository.isMasterShieldEnabled,
                settingsRepository.isReelsInterceptionEnabled,
                settingsRepository.isShortsInterceptionEnabled,
                metricsRepository.reelsBlockedToday,
                metricsRepository.allTimeBlocked
            ) { masterEnabled, reelsEnabled, shortsEnabled, reelsToday, allTime ->
                // Average session saved estimate: 3 minutes per intercepted doomscroll loop
                val timeSavedMin = reelsToday * 3
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
            combine(settingsRepository.isStrictLockdownEnabled, breakManager.breaksUsedToday) { strict, _ ->
                strict to breakManager.getGlobalPausesRemaining(strict)
            }.collect { (strict, pausesLeft) ->
                _uiState.update { it.copy(isStrictLockdown = strict, globalPausesRemaining = pausesLeft) }
            }
        }

        viewModelScope.launch {
            settingsRepository.shieldPausedUntilMillis.collect { until ->
                _uiState.update { it.copy(shieldResumesAtMillis = until) }
            }
        }

        viewModelScope.launch {
            metricsRepository.blockedTodayByRule.collect { byRule ->
                blockedByPackage = byRule.entries
                    .mapNotNull { (ruleId, count) -> rulePackages[ruleId]?.let { it to count } }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { it.value.sum() }
                loadUsageStats()
            }
        }

        viewModelScope.launch {
            breakManager.breakSession.collect { session ->
                _uiState.update { it.copy(breakSession = session) }
            }
        }

        viewModelScope.launch {
            breakManager.tickingSeconds.collect { seconds ->
                _uiState.update { it.copy(breakRemainingSeconds = seconds) }
            }
        }

        viewModelScope.launch {
            settingsRepository.getAppLimits().collect { limits ->
                _uiState.update { it.copy(appLimits = limits) }
                loadUsageStats()
            }
        }
    }

    fun handleIntent(intent: DashboardIntent) {
        when (intent) {
            is DashboardIntent.ToggleMasterShield -> {
                // Strict Lockdown must be switched off (with its 60 s wait) before the shield can be paused.
                if (!intent.enabled && _uiState.value.isStrictLockdown) return
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
                breakManager.requestGlobalPause(
                    durationMinutes = intent.minutes,
                    intention = intent.intention,
                    isStrictLockdown = _uiState.value.isStrictLockdown
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

        _uiState.update {
            it.copy(
                isAccessibilityPermissionGranted = hasAccessibility,
                isUsagePermissionGranted = hasUsage
            )
        }
    }

    private fun loadUsageStats() {
        viewModelScope.launch {
            val limitsMap = _uiState.value.appLimits.associate { it.monitoredApp.packageName to it.dailyLimitMinutes }
            val stats = usageRepository.getUsageStatsForMonitoredApps(limitsMap, blockedByPackage)
            _uiState.update { it.copy(appUsageStats = stats) }
        }
    }

    class Factory(
        private val context: Context,
        private val settingsRepository: SettingsRepository,
        private val usageRepository: UsageRepository,
        private val metricsRepository: MetricsRepository,
        private val breakManager: MindfulBreakManager,
        private val rulePackages: Map<String, String>
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(
                context, settingsRepository, usageRepository, metricsRepository, breakManager, rulePackages
            ) as T
        }
    }
}
