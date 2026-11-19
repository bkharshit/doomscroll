package com.doomscroll.android.service

import android.view.accessibility.AccessibilityNodeInfo
import com.doomscroll.android.domain.interceptor.UiNode

class A11yUiNode(val nodeInfo: AccessibilityNodeInfo) : UiNode {

    override val viewId: String?
        get() = nodeInfo.viewIdResourceName

    override val text: String?
        get() = nodeInfo.text?.toString()

    override val contentDescription: String?
        get() = nodeInfo.contentDescription?.toString()

    override val className: String?
        get() = nodeInfo.className?.toString()

    override val isVisible: Boolean
        get() = nodeInfo.isVisibleToUser

    override val isSelected: Boolean
        get() = nodeInfo.isSelected

    override val isClickable: Boolean
        get() = nodeInfo.isClickable

    override val children: List<UiNode>
        get() {
            val count = nodeInfo.childCount
            if (count == 0) return emptyList()
            val list = ArrayList<UiNode>(count)
            for (i in 0 until count) {
                val child = nodeInfo.getChild(i)
                if (child != null) {
                    list.add(A11yUiNode(child))
                }
            }
            return list
        }
}
