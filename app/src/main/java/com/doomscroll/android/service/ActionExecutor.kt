package com.doomscroll.android.service

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.doomscroll.android.domain.interceptor.Action

class ActionExecutor(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "ActionExecutor"
    }

    fun execute(action: Action, root: AccessibilityNodeInfo): Boolean {
        return when (action) {
            is Action.Click -> {
                val nodes = root.findAccessibilityNodeInfosByViewId(action.targetViewId)
                var clicked = false
                if (!nodes.isNullOrEmpty()) {
                    for (node in nodes) {
                        if (clickNodeOrParent(node)) {
                            clicked = true
                            break
                        }
                    }
                    recycleNodeList(nodes)
                }
                clicked
            }

            Action.ClickInAppBack -> {
                // Try to click in-app back control by text or content description
                val inAppClicked = clickInAppBackButton(root)
                if (inAppClicked) {
                    true
                } else {
                    // Fall back to system back
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }
            }

            Action.Back -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }

            Action.Home -> {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            }
        }
    }

    private fun clickInAppBackButton(root: AccessibilityNodeInfo): Boolean {
        val backTexts = listOf("Back", "Navigate up", "Close")
        for (query in backTexts) {
            val byText = root.findAccessibilityNodeInfosByText(query)
            if (!byText.isNullOrEmpty()) {
                for (node in byText) {
                    val desc = node.contentDescription?.toString()
                    val text = node.text?.toString()
                    if (desc.equals(query, ignoreCase = true) || text.equals(query, ignoreCase = true)) {
                        if (clickNodeOrParent(node)) {
                            recycleNodeList(byText)
                            return true
                        }
                    }
                }
                recycleNodeList(byText)
            }
        }
        return false
    }

    fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            val parentClickable = parent.isClickable
            if (parentClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    @Suppress("DEPRECATION")
                    parent.recycle()
                }
                return true
            }
            val nextParent = parent.parent
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                parent.recycle()
            }
            parent = nextParent
        }
        return false
    }

    private fun recycleNodeList(nodes: List<AccessibilityNodeInfo>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            for (node in nodes) {
                @Suppress("DEPRECATION")
                node.recycle()
            }
        }
    }
}
