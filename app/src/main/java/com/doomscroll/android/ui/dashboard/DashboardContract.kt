package com.doomscroll.android.ui.dashboard

import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.BreakSession
import com.doomscroll.android.core.model.UsageStat

data class DashboardUiState(
    val isMasterShieldActive: Boolean = true,
    val isReelsInterceptionActive: Boolean = true,
    val isShortsInterceptionActive: Boolean = true,
    val isAccessibilityPermissionGranted: Boolean = false,
    val isUsagePermissionGranted: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val reelsBlockedToday: Int = 0,
    val allTimeBlocked: Int = 0,
    val breakSession: BreakSession = BreakSession(),
    val appUsageStats: List<UsageStat> = emptyList(),
    val appLimits: List<AppLimit> = emptyList(),
    val isBreakDialogVisible: Boolean = false,
    val totalTimeSavedMinutes: Int = 0
)

sealed interface DashboardIntent {
    data class ToggleMasterShield(val enabled: Boolean) : DashboardIntent
    data class ToggleReelsInterception(val enabled: Boolean) : DashboardIntent
    data class ToggleShortsInterception(val enabled: Boolean) : DashboardIntent
    data object ShowBreakDialog : DashboardIntent
    data object DismissBreakDialog : DashboardIntent
    data class StartBreak(val minutes: Int, val intention: String) : DashboardIntent
    data object CancelBreak : DashboardIntent
    data object RefreshPermissionsAndStats : DashboardIntent
}
