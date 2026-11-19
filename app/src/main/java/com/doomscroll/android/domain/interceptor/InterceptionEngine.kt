package com.doomscroll.android.domain.interceptor

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo

class InterceptionEngine(
    private val strategies: List<ContentInterceptionStrategy> = listOf(
        InstagramReelsStrategy(),
        YouTubeShortsStrategy()
    )
) {
    fun shouldProcessPackage(packageName: String): Boolean {
        return strategies.any { it.canHandle(packageName) }
    }

    fun processEvent(
        packageName: String,
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        allowSentReelsInChat: Boolean = true
    ): InterceptionResult {
        val strategy = strategies.firstOrNull { it.canHandle(packageName) }
            ?: return InterceptionResult.PASS

        return strategy.evaluateAndIntercept(root, service, allowSentReelsInChat)
    }
}
