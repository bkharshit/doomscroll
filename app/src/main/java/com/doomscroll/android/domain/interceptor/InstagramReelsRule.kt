package com.doomscroll.android.domain.interceptor

class InstagramReelsRule : InterceptionRule {

    override val id: String = "instagram.reels"
    override val targetPackage: String = "com.instagram.android"

    companion object {
        // Short-form Video (Reels) IDs
        const val ID_CLIPS_TAB = "com.instagram.android:id/clips_tab"
        const val ID_CLIPS_VIEWER_CONTAINER = "com.instagram.android:id/clips_viewer_container"
        const val ID_CLIPS_VIDEO_LAYOUT = "com.instagram.android:id/clips_viewer_video_layout"
        const val ID_CLIPS_VIEW_PAGER = "com.instagram.android:id/clips_viewer_view_pager"

        // Safe Navigation Target
        const val ID_FEED_TAB = "com.instagram.android:id/feed_tab"

        // 24-hour Stories IDs
        const val ID_STORY_VIEWER = "com.instagram.android:id/reel_viewer_container"
        const val ID_STORY_ROOT = "com.instagram.android:id/reels_viewer_root"

        // Camera / Creation Studio IDs
        const val ID_QUICK_CAPTURE_FRAGMENT = "com.instagram.android:id/quick_capture_fragment_container"
        const val ID_QUICK_CAPTURE_ROOT = "com.instagram.android:id/quick_capture_root_container"
        const val ID_CAMERA_PRE_CAPTURE = "com.instagram.android:id/pre_capture_controls_container"
        const val ID_CAMERA_POST_CAPTURE = "com.instagram.android:id/post_capture_controls_container"
        const val ID_CAMERA_PERMISSIONS = "com.instagram.android:id/camera_permissions_cover"

        // In-app back button on full-screen Reels opened from Explore/Search/Profile
        const val ID_BACK_BUTTON = "com.instagram.android:id/action_bar_button_back"

        // Direct Message Exemption
        const val ID_THREAD_COMPOSER = "com.instagram.android:id/row_thread_composer_edittext"
    }

    override fun evaluate(screen: Screen): Decision {
        // 1. DETECT: Check if Reels tab is selected or fullscreen Reels player is active
        val isClipsTabSelected = screen.selected(ID_CLIPS_TAB)
        val isFullscreenReel = screen.anyVisible(
            ID_CLIPS_VIEWER_CONTAINER,
            ID_CLIPS_VIDEO_LAYOUT,
            ID_CLIPS_VIEW_PAGER
        )

        // If neither the tab is selected nor the player is visible, PASS immediately
        if (!isClipsTabSelected && !isFullscreenReel) {
            return Decision.Pass
        }

        // 2. EXEMPTIONS: Only exempt if exempt containers are actually visible to user (Fixes R5)
        if (screen.anyVisible(
                ID_QUICK_CAPTURE_FRAGMENT,
                ID_QUICK_CAPTURE_ROOT,
                ID_CAMERA_PRE_CAPTURE,
                ID_CAMERA_POST_CAPTURE,
                ID_CAMERA_PERMISSIONS
            )
        ) {
            return Decision.Pass
        }

        if (screen.anyVisible(ID_STORY_VIEWER, ID_STORY_ROOT)) {
            return Decision.Pass
        }

        if (screen.visible(ID_THREAD_COMPOSER).isNotEmpty()) {
            return Decision.Pass
        }

        // 3. ACT: Choose the most surgical exit action
        if (isClipsTabSelected) {
            // User is on the dedicated Reels tab: click Home feed tab
            val hasFeedTab = screen.has(ID_FEED_TAB)
            return if (hasFeedTab) {
                Decision.Act(
                    reason = "Instagram Reels Tab Selected",
                    action = Action.Click(ID_FEED_TAB)
                )
            } else {
                Decision.Act(
                    reason = "Instagram Reels Tab Selected",
                    action = Action.Back
                )
            }
        }

        if (isFullscreenReel) {
            // R4 Fix: If Reel was opened from Home Feed, the Home Feed tab is selected underneath!
            // Clicking Home would do nothing or cause a refresh loop, so pop the Reel with Back!
            val isFeedTabSelectedUnderneath = screen.selected(ID_FEED_TAB)
            if (isFeedTabSelectedUnderneath) {
                return Decision.Act(
                    reason = "Instagram Reel Opened from Home Feed",
                    action = Action.Back
                )
            }

            // Opened from Explore, Search, or Profile: click in-app back (or pop with Back)
            return Decision.Act(
                reason = "Instagram Reel Opened",
                action = Action.ClickInAppBack(listOf(ID_BACK_BUTTON))
            )
        }

        return Decision.Pass
    }
}
