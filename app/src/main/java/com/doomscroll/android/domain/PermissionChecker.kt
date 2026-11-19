package com.doomscroll.android.domain

/** Platform permission state, injected so ViewModels don't hold a Context (R19). */
interface PermissionChecker {
    fun isAccessibilityServiceEnabled(): Boolean
    fun isUsageAccessGranted(): Boolean
    fun isBatteryOptimizationIgnored(): Boolean
}
