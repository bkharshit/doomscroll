package com.doomscroll.android.core.model

/** Remaining time is exposed as a ticking flow by MindfulBreakManager.tickingSeconds, not computed here. */
data class BreakSession(
    val isActive: Boolean = false,
    val endTimeMillis: Long = 0L,
    val allowedPackage: String? = null,
    val intention: String? = null
)
