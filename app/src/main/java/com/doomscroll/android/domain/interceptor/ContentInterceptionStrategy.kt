package com.doomscroll.android.domain.interceptor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

data class InterceptionResult(
    val wasIntercepted: Boolean,
    val reason: String? = null,
    val actionTaken: String? = null
) {
    companion object {
        val PASS = InterceptionResult(wasIntercepted = false)
        fun intercepted(reason: String, action: String) = InterceptionResult(
            wasIntercepted = true,
            reason = reason,
            actionTaken = action
        )
    }
}

/**
 * Strategy interface for surgical short-form video detection and eviction.
 */
interface ContentInterceptionStrategy {
    val targetPackage: String
    fun canHandle(packageName: String): Boolean = targetPackage == packageName
    fun evaluateAndIntercept(
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        allowSentReelsInChat: Boolean = true
    ): InterceptionResult
}
