package com.doomscroll.android.domain.interceptor

interface UiNode {
    val viewId: String?
    val text: String?
    val contentDescription: String?
    val className: String?
    val isVisible: Boolean
    val isSelected: Boolean
    val isClickable: Boolean
    val children: List<UiNode>
}

data class Screen(
    val packageName: String,
    val root: UiNode
) {
    val byId: Map<String, List<UiNode>> by lazy {
        val map = mutableMapOf<String, MutableList<UiNode>>()
        fun traverse(node: UiNode, depth: Int) {
            if (depth > 40) return
            node.viewId?.let { id ->
                map.getOrPut(id) { mutableListOf() }.add(node)
            }
            for (child in node.children) {
                traverse(child, depth + 1)
            }
        }
        traverse(root, 0)
        map
    }

    fun visible(id: String): List<UiNode> = byId[id].orEmpty().filter { it.isVisible }

    fun anyVisible(vararg ids: String): Boolean = ids.any { id -> visible(id).isNotEmpty() }

    fun selected(id: String): Boolean = byId[id].orEmpty().any { it.isSelected && it.isVisible }
}
