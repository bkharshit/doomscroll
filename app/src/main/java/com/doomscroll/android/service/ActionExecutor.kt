package com.doomscroll.android.service

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.doomscroll.android.domain.interceptor.Action
import com.doomscroll.android.domain.interceptor.BackLabels
import java.util.concurrent.ConcurrentHashMap

class ActionExecutor(
    private val service: AccessibilityService
) {
    companion object {
        private const val MAX_NODES = 2500
    }

    /** Localised back labels per (package, versionCode): resources only change with an app update. */
    private val backLabelCache = ConcurrentHashMap<String, Set<String>>()

    fun execute(action: Action, root: AccessibilityNodeInfo): Boolean {
        return when (action) {
            is Action.Click -> clickFirstById(root, action.targetViewId)

            is Action.ClickInAppBack -> {
                action.viewIds.any { clickFirstById(root, it) } ||
                    clickBackByLabel(root) ||
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }

            Action.Back -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

            Action.Home -> service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private fun clickFirstById(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        if (nodes.isNullOrEmpty()) return false
        val clicked = nodes.any { it.isVisibleToUser && clickNodeOrParent(it) }
        recycleAll(nodes)
        return clicked
    }

    /** One bounded walk of the tree looking for a visible node whose label is a localised "Back". */
    private fun clickBackByLabel(root: AccessibilityNodeInfo): Boolean {
        val pkg = root.packageName?.toString() ?: return false
        val labels = backLabelsFor(pkg)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val obtained = mutableListOf<AccessibilityNodeInfo>()
        for (i in 0 until root.childCount) root.getChild(i)?.let { queue.add(it); obtained.add(it) }
        var visited = 0
        var clicked = false
        while (queue.isNotEmpty() && visited < MAX_NODES && !clicked) {
            val node = queue.removeFirst()
            visited++
            if (node.isVisibleToUser && BackLabels.matches(labels, node.text, node.contentDescription)) {
                clicked = clickNodeOrParent(node)
            }
            if (!clicked) {
                for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it); obtained.add(it) }
            }
        }
        recycleAll(obtained)
        return clicked
    }

    private fun backLabelsFor(pkg: String): Set<String> {
        val pm = service.packageManager
        val version = try {
            val info = pm.getPackageInfo(pkg, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        } catch (e: PackageManager.NameNotFoundException) {
            -1L
        }
        return backLabelCache.getOrPut("$pkg:$version") {
            val res = try {
                pm.getResourcesForApplication(pkg)
            } catch (e: Exception) {
                null
            }
            BackLabels.resolve { name ->
                val id = res?.getIdentifier(name, "string", pkg) ?: 0
                if (id != 0) res?.getString(id) else null
            }
        }
    }

    fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                recycle(parent)
                return true
            }
            val nextParent = parent.parent
            recycle(parent)
            parent = nextParent
        }
        return false
    }

    private fun recycle(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            node.recycle()
        }
    }

    private fun recycleAll(nodes: List<AccessibilityNodeInfo>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) nodes.forEach { recycle(it) }
    }
}
