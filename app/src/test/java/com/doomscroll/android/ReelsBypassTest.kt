package com.doomscroll.android

import com.doomscroll.android.domain.interceptor.Action
import com.doomscroll.android.domain.interceptor.Decision
import com.doomscroll.android.domain.interceptor.InstagramReelsRule
import com.doomscroll.android.domain.interceptor.InterceptionEngine
import com.doomscroll.android.domain.interceptor.Screen
import com.doomscroll.android.domain.interceptor.UiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Repeated attempts to open Reels must never get through. */
class ReelsBypassTest {

    private class Node(
        override val viewId: String?,
        override val isSelected: Boolean = false,
        override val children: List<UiNode> = emptyList()
    ) : UiNode {
        override val text: String? = null
        override val contentDescription: String? = null
        override val className: String? = null
        override val isVisible = true
        override val isClickable = true
    }

    private val ig = "com.instagram.android"
    private fun reelsTab() = Screen(ig, Node("root", children = listOf(
        Node(InstagramReelsRule.ID_CLIPS_TAB, isSelected = true),
        Node(InstagramReelsRule.ID_FEED_TAB)
    )))
    private fun reelFromFeed() = Screen(ig, Node("root", children = listOf(
        Node(InstagramReelsRule.ID_CLIPS_VIEWER_CONTAINER),
        Node(InstagramReelsRule.ID_FEED_TAB, isSelected = true)
    )))

    @Test
    fun tappingReelsRepeatedly_isBlockedEveryTime() {
        val engine = InterceptionEngine()
        // A tap every 1.2 s for 30 s: previously the 3rd action triggered a 30 s "loop backoff" (Pass).
        var t = 0L
        repeat(25) {
            val d = engine.evaluate(reelsTab(), t)
            assertTrue("tap at ${t}ms got $d", d is Decision.Act && d.action is Action.Click)
            t += 1_200
        }
    }

    @Test
    fun tapWithinCooldown_isRetried_notLetThrough() {
        val engine = InterceptionEngine()
        assertTrue(engine.evaluate(reelsTab(), 0) is Decision.Act)
        val d = engine.evaluate(reelsTab(), 300)
        assertEquals(Decision.RetryAfter(700), d)
        assertTrue("acts again once the cooldown is over", engine.evaluate(reelsTab(), 1_000) is Decision.Act)
    }

    @Test
    fun backThatKeepsFailing_escalatesToHome_neverPasses() {
        val engine = InterceptionEngine()
        val actions = (0 until 6).map { i -> (engine.evaluate(reelFromFeed(), i * 1_500L) as Decision.Act).action }
        assertEquals(listOf(Action.Back, Action.Back, Action.Home, Action.Back, Action.Back, Action.Home), actions)
    }

    @Test
    fun nothingOnScreen_passes() {
        val engine = InterceptionEngine()
        assertEquals(Decision.Pass, engine.evaluate(Screen(ig, Node("root", children = listOf(Node(InstagramReelsRule.ID_FEED_TAB, isSelected = true)))), 0))
    }
}
