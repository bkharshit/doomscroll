package com.doomscroll.android.core.model

data class BreakSession(
    val isActive: Boolean = false,
    val endTimeMillis: Long = 0L,
    val allowedPackage: String? = null,
    val intention: String? = null
) {
    val remainingSeconds: Long
        get() {
            if (!isActive) return 0L
            val diff = (endTimeMillis - System.currentTimeMillis()) / 1000
            return diff.coerceAtLeast(0L)
        }

    val isExpired: Boolean
        get() = !isActive || System.currentTimeMillis() >= endTimeMillis
}
