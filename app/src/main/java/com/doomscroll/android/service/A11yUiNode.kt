package com.doomscroll.android.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.doomscroll.android.domain.interceptor.UiNode

/**
 * Adapter from a live [AccessibilityNodeInfo] to the domain's [UiNode]. Children are fetched once and
 * every node obtained is registered with [pool], so the caller can recycle them all afterwards
 * (needed below API 33, where nodes come from a pool).
 */
class A11yUiNode(
    val nodeInfo: AccessibilityNodeInfo,
    private val pool: NodePool
) : UiNode {

    override val viewId: String? get() = nodeInfo.viewIdResourceName
    override val text: String? get() = nodeInfo.text?.toString()
    override val contentDescription: String? get() = nodeInfo.contentDescription?.toString()
    override val className: String? get() = nodeInfo.className?.toString()
    override val isVisible: Boolean get() = nodeInfo.isVisibleToUser
    override val isSelected: Boolean get() = nodeInfo.isSelected
    override val isClickable: Boolean get() = nodeInfo.isClickable

    override val children: List<UiNode> by lazy {
        val count = nodeInfo.childCount
        if (count == 0) return@lazy emptyList()
        (0 until count).mapNotNull { i -> nodeInfo.getChild(i)?.let { A11yUiNode(pool.track(it), pool) } }
    }
}

/** Collects nodes obtained during one evaluation so they can be recycled together. */
class NodePool {
    private val nodes = ArrayList<AccessibilityNodeInfo>(256)

    fun track(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        nodes.add(node)
        return node
    }

    fun recycleAll() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            nodes.forEach { it.recycle() }
        }
        nodes.clear()
    }
}
