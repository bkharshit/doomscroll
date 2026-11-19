package com.doomscroll.android.core.model

data class UsageStat(
    val monitoredApp: MonitoredApp,
    val foregroundSecondsToday: Long,
    val dailyLimitMinutes: Int,
    val reelsBlockedCount: Int
) {
    val usageMinutes: Int
        get() = (foregroundSecondsToday / 60).toInt()

    val progressPercent: Float
        get() = if (dailyLimitMinutes > 0) {
            (usageMinutes.toFloat() / dailyLimitMinutes).coerceIn(0f, 1f)
        } else 0f

    val isLimitExceeded: Boolean
        get() = dailyLimitMinutes in 1..usageMinutes
}
