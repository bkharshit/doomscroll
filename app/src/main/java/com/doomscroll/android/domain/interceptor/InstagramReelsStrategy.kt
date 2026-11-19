package com.doomscroll.android.domain.interceptor

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class InstagramReelsStrategy : ContentInterceptionStrategy {

    override val targetPackage: String = "com.instagram.android"

    private var lastActionTimestamp: Long = 0L
    private val actionCooldownMillis = 1000L

    companion object {
        private const val TAG = "InstagramReelsStrategy"

        // Short-form Video (Reels) IDs
        private const val ID_CLIPS_TAB = "com.instagram.android:id/clips_tab"
        private const val ID_CLIPS_VIEWER_CONTAINER = "com.instagram.android:id/clips_viewer_container"
        private const val ID_CLIPS_VIDEO_LAYOUT = "com.instagram.android:id/clips_viewer_video_layout"
        private const val ID_CLIPS_VIEW_PAGER = "com.instagram.android:id/clips_viewer_view_pager"

        // Safe Navigation Targets
        private const val ID_FEED_TAB = "com.instagram.android:id/feed_tab"

        // 24-hour Stories IDs (Meta calls stories "reel_viewer") - must NEVER be blocked
        private const val ID_STORY_VIEWER = "com.instagram.android:id/reel_viewer_container"
        private const val ID_STORY_ROOT = "com.instagram.android:id/reels_viewer_root"

        // Camera / Creation Studio IDs - must NEVER be intercepted
        private const val ID_QUICK_CAPTURE_FRAGMENT = "com.instagram.android:id/quick_capture_fragment_container"
        private const val ID_QUICK_CAPTURE_ROOT = "com.instagram.android:id/quick_capture_root_container"
        private const val ID_CAMERA_PRE_CAPTURE = "com.instagram.android:id/pre_capture_controls_container"
        private const val ID_CAMERA_POST_CAPTURE = "com.instagram.android:id/post_capture_controls_container"
        private const val ID_CAMERA_PERMISSIONS = "com.instagram.android:id/camera_permissions_cover"

        // Direct Message Exemption Context
        private const val ID_THREAD_COMPOSER = "com.instagram.android:id/row_thread_composer_edittext"
    }

    override fun evaluateAndIntercept(
        root: AccessibilityNodeInfo,
        service: AccessibilityService,
        allowSentReelsInChat: Boolean
    ): InterceptionResult {
        // 1. Camera & Creation Studio Exemption: Never interfere when user is taking a photo or creating content
        if (isCameraOrCreationScreen(root)) {
            return InterceptionResult.PASS
        }

        // 2. Stories Exemption: Never interfere with 24-hour friend stories
        if (isViewingStory(root)) {
            return InterceptionResult.PASS
        }

        // 3. Direct Message Exemption: Keep chat messaging intact
        if (allowSentReelsInChat && isInDirectMessageContext(root)) {
            return InterceptionResult.PASS
        }

        // 4. HOME TAB PROTECTION:
        // If the Home feed tab is selected, user is on the Home feed.
        // Never click anything while already on Home (prevents pull-to-refresh loops).
        val feedTabs = root.findAccessibilityNodeInfosByViewId(ID_FEED_TAB)
        val isFeedTabSelected = feedTabs.any { it.isSelected }
        if (isFeedTabSelected) {
            return InterceptionResult.PASS
        }

        val now = System.currentTimeMillis()
        if (now - lastActionTimestamp < actionCooldownMillis) {
            return InterceptionResult.PASS
        }

        // 5. Check if dedicated Clips (Reels) Tab is selected on the bottom bar
        val clipsTabs = root.findAccessibilityNodeInfosByViewId(ID_CLIPS_TAB)
        val isClipsTabSelected = clipsTabs.any { it.isSelected }

        // 6. Check for Fullscreen Reels Video Viewer Containers that are actually visible on screen
        val isFullscreenReel = isVisibleNodePresent(root, ID_CLIPS_VIEWER_CONTAINER) ||
                isVisibleNodePresent(root, ID_CLIPS_VIDEO_LAYOUT) ||
                isVisibleNodePresent(root, ID_CLIPS_VIEW_PAGER)

        if (isClipsTabSelected || isFullscreenReel) {
            // Case A: User tapped the dedicated Reels Tab on the bottom navigation bar
            // Switch back to Home feed tab
            if (isClipsTabSelected && !feedTabs.isNullOrEmpty()) {
                val feedTab = feedTabs[0]
                val clicked = clickNodeOrParent(feedTab)
                if (clicked) {
                    lastActionTimestamp = now
                    Log.i(TAG, "Action: Clicked Home Feed Tab from Reels tab")
                    return InterceptionResult.intercepted(
                        reason = "Instagram Reels Tab Selected",
                        action = "Redirected to Home Feed"
                    )
                }
                return InterceptionResult.PASS
            }

            // Case B: User opened a Reel from Explore, Search, or Profile
            // Attempt to click the in-app back arrow (contentDescription = "Back")
            val inAppBackClicked = clickInAppBackButton(root)
            if (inAppBackClicked) {
                lastActionTimestamp = now
                Log.i(TAG, "Action: Clicked in-app Back button on Reel")
                return InterceptionResult.intercepted(
                    reason = "Instagram Reel Opened from Explore/Profile",
                    action = "Clicked In-App Back"
                )
            }

            // If in-app back arrow not clickable, perform system back to pop the Reel fragment
            lastActionTimestamp = now
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            Log.i(TAG, "Action: Popped Reel fragment via Global Back")
            return InterceptionResult.intercepted(
                reason = "Instagram Reel Opened from Explore/Profile",
                action = "Simulated Global Back"
            )
        }

        return InterceptionResult.PASS
    }

    private fun clickInAppBackButton(root: AccessibilityNodeInfo): Boolean {
        // Look specifically for nodes with contentDescription "Back"
        val backNodes = root.findAccessibilityNodeInfosByText("Back")
        for (node in backNodes) {
            val desc = node.contentDescription?.toString()
            if (desc.equals("Back", ignoreCase = true) || desc.equals("Navigate up", ignoreCase = true)) {
                if (clickNodeOrParent(node)) {
                    return true
                }
            }
        }
        return false
    }

    private fun isCameraOrCreationScreen(root: AccessibilityNodeInfo): Boolean {
        return root.findAccessibilityNodeInfosByViewId(ID_QUICK_CAPTURE_FRAGMENT).isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId(ID_QUICK_CAPTURE_ROOT).isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId(ID_CAMERA_PRE_CAPTURE).isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId(ID_CAMERA_POST_CAPTURE).isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId(ID_CAMERA_PERMISSIONS).isNotEmpty()
    }

    private fun isVisibleNodePresent(root: AccessibilityNodeInfo, viewId: String): Boolean {
        val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
        return nodes.any { it.isVisibleToUser }
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            parent = parent.parent
        }
        return false
    }

    private fun isViewingStory(root: AccessibilityNodeInfo): Boolean {
        return root.findAccessibilityNodeInfosByViewId(ID_STORY_VIEWER).isNotEmpty() ||
                root.findAccessibilityNodeInfosByViewId(ID_STORY_ROOT).isNotEmpty()
    }

    private fun isInDirectMessageContext(root: AccessibilityNodeInfo): Boolean {
        if (root.findAccessibilityNodeInfosByViewId(ID_THREAD_COMPOSER).isNotEmpty()) {
            return true
        }

        val backToChat = root.findAccessibilityNodeInfosByText("Back to chat")
        if (!backToChat.isNullOrEmpty()) return true

        val replyPrivately = root.findAccessibilityNodeInfosByText("Reply privately")
        if (!replyPrivately.isNullOrEmpty()) return true

        return false
    }
}
