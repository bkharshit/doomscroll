package com.doomscroll.android.domain.limiter

object LimitDecider {

    /**
     * Calculates remaining time in seconds for a package given baseline usage and limit.
     * Returns null if no limit or limit is disabled (<= 0) or blocking is disabled.
     */
    fun calculateRemainingSeconds(
        usedSeconds: Long,
        limitMinutes: Int,
        isBlockEnabled: Boolean = true
    ): Long? {
        if (!isBlockEnabled || limitMinutes <= 0) return null
        val limitSeconds = limitMinutes * 60L
        return (limitSeconds - usedSeconds).coerceAtLeast(0L)
    }

    /**
     * Determines whether the package has exhausted its daily limit.
     */
    fun isOverLimit(
        usedSeconds: Long,
        limitMinutes: Int,
        isBlockEnabled: Boolean = true
    ): Boolean {
        if (!isBlockEnabled || limitMinutes <= 0) return false
        return usedSeconds >= (limitMinutes * 60L)
    }
}
