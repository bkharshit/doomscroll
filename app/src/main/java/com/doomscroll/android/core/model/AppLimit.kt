package com.doomscroll.android.core.model

data class AppLimit(
    val monitoredApp: MonitoredApp,
    val isAppBlockingEnabled: Boolean = true,
    val dailyLimitMinutes: Int = monitoredApp.defaultDailyLimitMinutes,
    val isShortsInterceptionEnabled: Boolean = monitoredApp.supportsShortsInterception
)
