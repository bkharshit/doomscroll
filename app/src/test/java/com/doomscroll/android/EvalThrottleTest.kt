package com.doomscroll.android

import com.doomscroll.android.domain.interceptor.EvalThrottle
import com.doomscroll.android.domain.interceptor.Screen
import com.doomscroll.android.domain.interceptor.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvalThrottleTest {

    @Test
    fun threeSlowEvaluations_doubleTheWindow_upToTheCap() {
        val t = EvalThrottle()
        assertEquals(150L, t.windowMillis)
        repeat(2) { assertFalse(t.record(80)) }
        assertTrue(t.record(80))
        assertEquals(300L, t.windowMillis)
        repeat(3) { t.record(80) }
        assertEquals(600L, t.windowMillis)
        repeat(3) { t.record(80) }
        assertEquals("capped", 600L, t.windowMillis)
    }

    @Test
    fun aFastEvaluation_breaksTheSlowStreak_andFastRunsRecover() {
        val t = EvalThrottle()
        t.record(80); t.record(80); t.record(5); t.record(80)
        assertEquals(150L, t.windowMillis)
        repeat(3) { t.record(80) }
        assertEquals(300L, t.windowMillis)
        repeat(20) { t.record(5) }
        assertEquals(150L, t.windowMillis)
    }

    private class Node(override val viewId: String?, override val children: List<UiNode> = emptyList()) : UiNode {
        override val text: String? = null
        override val contentDescription: String? = null
        override val className: String? = null
        override val isVisible = true
        override val isSelected = false
        override val isClickable = false
    }

    @Test
    fun screenIndex_stopsAtTheNodeCap() {
        val root = Node("root", (1..5000).map { Node("n$it") })
        val screen = Screen("pkg", root, maxNodes = 100)
        assertTrue(screen.has("n98"))
        assertFalse("beyond the cap", screen.has("n150"))
    }
}
