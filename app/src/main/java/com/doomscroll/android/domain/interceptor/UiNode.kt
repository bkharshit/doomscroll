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
    val root: UiNode,
    /** Traversal caps so a pathological tree can't stall the evaluation thread. */
    val maxDepth: Int = 40,
    val maxNodes: Int = 2500
) {
    /** Built by one traversal; every rule check reads this map instead of querying the tree again. */
    val byId: Map<String, List<UiNode>> by lazy {
        val map = mutableMapOf<String, MutableList<UiNode>>()
        var visited = 0
        fun traverse(node: UiNode, depth: Int) {
            if (depth > maxDepth || visited >= maxNodes) return
            visited++
            node.viewId?.let { id ->
                map.getOrPut(id) { mutableListOf() }.add(node)
            }
            for (child in node.children) {
                if (visited >= maxNodes) return
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
