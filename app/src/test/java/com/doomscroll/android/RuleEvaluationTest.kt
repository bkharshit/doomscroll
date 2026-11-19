package com.doomscroll.android

import com.doomscroll.android.domain.interceptor.Action
import com.doomscroll.android.domain.interceptor.Decision
import com.doomscroll.android.domain.interceptor.InstagramReelsRule
import com.doomscroll.android.domain.interceptor.Screen
import com.doomscroll.android.domain.interceptor.UiNode
import com.doomscroll.android.domain.interceptor.YouTubeShortsRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

data class MockUiNode(
    override val viewId: String? = null,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val className: String? = null,
    override val isVisible: Boolean = true,
    override val isSelected: Boolean = false,
    override val isClickable: Boolean = false,
    override val children: List<UiNode> = emptyList()
) : UiNode

class RuleEvaluationTest {

    private val instagramRule = InstagramReelsRule()
    private val youtubeRule = YouTubeShortsRule()

    @Test
    fun instagram_homeFeed_returnsPass() {
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = InstagramReelsRule.ID_FEED_TAB, isSelected = true),
                MockUiNode(viewId = "com.instagram.android:id/row_feed_photo", isVisible = true)
            )
        )
        val screen = Screen("com.instagram.android", root)
        val decision = instagramRule.evaluate(screen)
        assertTrue(decision is Decision.Pass)
    }

    @Test
    fun instagram_reelsTabSelected_redirectsToFeedTab() {
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = InstagramReelsRule.ID_FEED_TAB, isSelected = false),
                MockUiNode(viewId = InstagramReelsRule.ID_CLIPS_TAB, isSelected = true)
            )
        )
        val screen = Screen("com.instagram.android", root)
        val decision = instagramRule.evaluate(screen)
        assertTrue(decision is Decision.Act)
        val act = decision as Decision.Act
        assertEquals(Action.Click(InstagramReelsRule.ID_FEED_TAB), act.action)
    }

    @Test
    fun instagram_reelOpenedFromHomeFeed_r4Fix_returnsBack() {
        // R4 bug: In previous code, if feed_tab was selected, it immediately passed!
        // Here, the fullscreen reel is visible AND the feed_tab is selected underneath.
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = InstagramReelsRule.ID_FEED_TAB, isSelected = true),
                MockUiNode(viewId = InstagramReelsRule.ID_CLIPS_VIEWER_CONTAINER, isVisible = true)
            )
        )
        val screen = Screen("com.instagram.android", root)
        val decision = instagramRule.evaluate(screen)
        assertTrue(decision is Decision.Act)
        val act = decision as Decision.Act
        // Must pop the Reel with Back rather than ignoring it or looping on Home!
        assertEquals(Action.Back, act.action)
    }

    @Test
    fun instagram_storyViewer_exemptFromInterception() {
        // Story viewer visible even if a reel container exists in hierarchy
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = InstagramReelsRule.ID_CLIPS_VIEWER_CONTAINER, isVisible = true),
                MockUiNode(viewId = InstagramReelsRule.ID_STORY_VIEWER, isVisible = true)
            )
        )
        val screen = Screen("com.instagram.android", root)
        val decision = instagramRule.evaluate(screen)
        assertTrue(decision is Decision.Pass)
    }

    @Test
    fun instagram_cameraCreation_exemptFromInterception() {
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = InstagramReelsRule.ID_CLIPS_VIEWER_CONTAINER, isVisible = true),
                MockUiNode(viewId = InstagramReelsRule.ID_QUICK_CAPTURE_FRAGMENT, isVisible = true)
            )
        )
        val screen = Screen("com.instagram.android", root)
        val decision = instagramRule.evaluate(screen)
        assertTrue(decision is Decision.Pass)
    }

    @Test
    fun youtube_shortsPivotSelected_clicksHomePivot() {
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = YouTubeShortsRule.ID_HOME_PIVOT, isSelected = false),
                MockUiNode(viewId = YouTubeShortsRule.ID_SHORTS_PIVOT, isSelected = true)
            )
        )
        val screen = Screen("com.google.android.youtube", root)
        val decision = youtubeRule.evaluate(screen)
        assertTrue(decision is Decision.Act)
        val act = decision as Decision.Act
        assertEquals(Action.Click(YouTubeShortsRule.ID_HOME_PIVOT), act.action)
    }

    @Test
    fun youtube_shortOpenedFromHomeShelf_r4Fix_returnsBack() {
        // Short opened from home shelf: Home pivot is selected underneath
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = YouTubeShortsRule.ID_HOME_PIVOT, isSelected = true),
                MockUiNode(viewId = YouTubeShortsRule.ID_SHORTS_CONTAINER, isVisible = true)
            )
        )
        val screen = Screen("com.google.android.youtube", root)
        val decision = youtubeRule.evaluate(screen)
        assertTrue(decision is Decision.Act)
        val act = decision as Decision.Act
        assertEquals(Action.Back, act.action)
    }

    @Test
    fun youtube_shortContainerActive_withUpButton_clicksUpButton() {
        val root = MockUiNode(
            viewId = "root",
            children = listOf(
                MockUiNode(viewId = YouTubeShortsRule.ID_SHORTS_CONTAINER, isVisible = true),
                MockUiNode(viewId = YouTubeShortsRule.ID_UP_BUTTON, isVisible = true)
            )
        )
        val screen = Screen("com.google.android.youtube", root)
        val decision = youtubeRule.evaluate(screen)
        assertTrue(decision is Decision.Act)
        val act = decision as Decision.Act
        assertEquals(Action.Click(YouTubeShortsRule.ID_UP_BUTTON), act.action)
    }
}
