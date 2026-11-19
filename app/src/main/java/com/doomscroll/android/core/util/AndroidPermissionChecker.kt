package com.doomscroll.android.core.util

import android.content.Context
import com.doomscroll.android.domain.PermissionChecker

class AndroidPermissionChecker(private val context: Context) : PermissionChecker {
    override fun isAccessibilityServiceEnabled() = PermissionHelper.isAccessibilityServiceEnabled(context)
    override fun isUsageAccessGranted() = PermissionHelper.isUsageAccessGranted(context)
    override fun isBatteryOptimizationIgnored() = PermissionHelper.isBatteryOptimizationIgnored(context)
}
