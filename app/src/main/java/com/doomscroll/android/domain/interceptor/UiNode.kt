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

/**
 * Read-only view of the current window for rules. Lookups are by view ID and memoised, so a rule
 * pays only for the IDs it actually checks (detection usually stops after a few).
 *
 * On device the lookup is `findAccessibilityNodeInfosByViewId`, which the target app resolves in its
 * own process; walking the whole tree node by node over IPC measured 130+ ms for Instagram's feed.
 * Tests (and recorded fixtures) build the lookup from an in-memory tree with the secondary constructor.
 */
class Screen(
    val packageName: String,
    private val findById: (viewId: String) -> List<UiNode>
) {
    /** Indexes an in-memory tree once, with depth and node caps. */
    constructor(packageName: String, root: UiNode, maxDepth: Int = 40, maxNodes: Int = 2500) :
        this(packageName, treeLookup(root, maxDepth, maxNodes))

    private val cache = HashMap<String, List<UiNode>>()

    fun nodes(id: String): List<UiNode> = cache.getOrPut(id) { findById(id) }

    fun has(id: String): Boolean = nodes(id).isNotEmpty()

    fun visible(id: String): List<UiNode> = nodes(id).filter { it.isVisible }

    fun anyVisible(vararg ids: String): Boolean = ids.any { id -> visible(id).isNotEmpty() }

    fun selected(id: String): Boolean = nodes(id).any { it.isSelected && it.isVisible }

    companion object {
        private fun treeLookup(root: UiNode, maxDepth: Int, maxNodes: Int): (String) -> List<UiNode> {
            val index = indexTree(root, maxDepth, maxNodes)
            return { id -> index[id].orEmpty() }
        }

        fun indexTree(root: UiNode, maxDepth: Int, maxNodes: Int): Map<String, List<UiNode>> {
            val map = mutableMapOf<String, MutableList<UiNode>>()
            var visited = 0
            fun traverse(node: UiNode, depth: Int) {
                if (depth > maxDepth || visited >= maxNodes) return
                visited++
                node.viewId?.let { id -> map.getOrPut(id) { mutableListOf() }.add(node) }
                for (child in node.children) {
                    if (visited >= maxNodes) return
                    traverse(child, depth + 1)
                }
            }
            traverse(root, 0)
            return map
        }
    }
}
