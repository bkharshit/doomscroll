package com.doomscroll.android.domain.limiter

/** Shows the block screen for a package. Implemented on Android by [UsageLimitEnforcer]. */
interface Enforcer {
    /** Returns true when the package is over its limit (whether or not a new block screen was shown). */
    fun enforceLimit(
        packageName: String,
        usedMinutes: Int,
        dailyLimitMinutes: Int,
        isSleepDowntime: Boolean = false
    ): Boolean
}
