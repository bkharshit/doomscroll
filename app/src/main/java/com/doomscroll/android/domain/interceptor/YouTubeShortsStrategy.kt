package com.doomscroll.android.domain.interceptor

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class YouTubeShortsStrategy : ContentInterceptionStrategy {

    override val targetPackage: String = "com.google.android.youtube"

    private var lastActionTimestamp: Long = 0L
    private val actionCooldownMillis = 1000L

    companion object {
        private const val TAG = "YouTubeShortsStrategy"
        private const val ID_SHORTS_PIVOT = "com.google.android.youtube:id/pivot_shorts_item"
        private const val ID_HOME_PIVOT = "com.google.android.youtube:id/pivot_home_item"
        private const val ID_REEL_RECYCLER = "com.google.android.youtube:id/reel_recycler"
        private const val ID_SHORTS_CONTAINER = "com.google.android.youtube:id/shorts_container"
        private const val ID_UP_BUTTON = "com.google.android.youtube:id/up_button"
    }

    override fun evaluateAndIntercept(
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        allowSentReelsInChat: Boolean
    ): InterceptionResult {
        // HOME PIVOT PROTECTION:
        // If the YouTube Home tab is already selected, user is on the Home feed.
        // NEVER click pivot_home_item when already on Home (avoids refresh loops).
        val homeTabs = root.findAccessibilityNodeInfosByViewId(ID_HOME_PIVOT)
        val isHomePivotSelected = homeTabs.any { it.isSelected }
        if (isHomePivotSelected) {
            return InterceptionResult.PASS
        }

        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < actionCooldownMillis) {
            return InterceptionResult.PASS
        }

        // 1. Check if Shorts bottom pivot tab is selected
        val shortsTabs = root.findAccessibilityNodeInfosByViewId(ID_SHORTS_PIVOT)
        val isShortsTabSelected = shortsTabs.any { it.isSelected }

        // 2. Check for Fullscreen Shorts player/recycler containers that are visible
        val isShortsPlayer = isVisibleNodePresent(root, ID_REEL_RECYCLER) ||
                isVisibleNodePresent(root, ID_SHORTS_CONTAINER)

        if (isShortsTabSelected || isShortsPlayer) {
            // Case A: Bottom pivot bar is visible and user is on Shorts tab
            // Click Home pivot to switch away from Shorts back to Home.
            if (!homeTabs.isNullOrEmpty()) {
                val homeTab = homeTabs[0]
                val clicked = clickNodeOrParent(homeTab)
                if (clicked) {
                    lastActionTimestamp = now
                    Log.i(TAG, "Action: Clicked YouTube Home Pivot Tab")
                    return InterceptionResult.intercepted(
                        reason = "YouTube Shorts Tab Selected",
                        action = "Redirected to YouTube Home"
                    )
                }
                return InterceptionResult.PASS
            }

            // Case B: In-app up/back button inside YouTube viewer
            val upButtons = root.findAccessibilityNodeInfosByViewId(ID_UP_BUTTON)
            if (!upButtons.isNullOrEmpty()) {
                val clicked = clickNodeOrParent(upButtons[0])
                if (clicked) {
                    lastActionTimestamp = now
                    Log.i(TAG, "Action: Clicked in-app Up Button")
                    return InterceptionResult.intercepted(
                        reason = "YouTube Shorts Container Active",
                        action = "Clicked In-App Up Button"
                    )
                }
            }

            // Fallback to Global Back only if bottom bar is hidden
            lastActionTimestamp = now
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Log.i(TAG, "Action: Exited YouTube Shorts Container via Global Back")
            return InterceptionResult.intercepted(
                reason = "YouTube Standalone Shorts Overlay",
                action = "Simulated Global Back"
            )
        }

        return InterceptionResult.PASS
    }

    private fun isVisibleNodePresent(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes.any { it.isVisibleToUser }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        return false
    }
}
