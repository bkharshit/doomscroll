package com.doomscroll.android.domain.interceptor

class YouTubeShortsRule : InterceptionRule {

    override val id: String = "youtube.shorts"
    override val targetPackage: String = "com.google.android.youtube"

    companion object {
        const val ID_SHORTS_PIVOT = "com.google.android.youtube:id/pivot_shorts_item"
        const val ID_HOME_PIVOT = "com.google.android.youtube:id/pivot_home_item"
        const val ID_REEL_RECYCLER = "com.google.android.youtube:id/reel_recycler"
        const val ID_SHORTS_CONTAINER = "com.google.android.youtube:id/shorts_container"
        const val ID_UP_BUTTON = "com.google.android.youtube:id/up_button"
    }

    override fun evaluate(screen: Screen): Decision {
        // 1. DETECT: Check if Shorts pivot tab is selected or Shorts player container is visible
        val isShortsPivotSelected = screen.selected(ID_SHORTS_PIVOT)
        val isShortsPlayer = screen.anyVisible(ID_REEL_RECYCLER, ID_SHORTS_CONTAINER)

        if (!isShortsPivotSelected && !isShortsPlayer) {
            return Decision.Pass
        }

        // 2. ACT
        if (isShortsPivotSelected) {
            val hasHomePivot = screen.has(ID_HOME_PIVOT)
            return if (hasHomePivot) {
                Decision.Act(
                    reason = "YouTube Shorts Tab Selected",
                    action = Action.Click(ID_HOME_PIVOT)
                )
            } else {
                Decision.Act(
                    reason = "YouTube Shorts Tab Selected",
                    action = Action.Back
                )
            }
        }

        if (isShortsPlayer) {
            // R4 Fix: If Short was opened from Home feed shelf, Home pivot is selected underneath!
            val isHomePivotSelected = screen.selected(ID_HOME_PIVOT)
            if (isHomePivotSelected) {
                return Decision.Act(
                    reason = "YouTube Short Opened from Home Shelf",
                    action = Action.Back
                )
            }

            // In-app up button if present
            if (screen.visible(ID_UP_BUTTON).isNotEmpty()) {
                return Decision.Act(
                    reason = "YouTube Shorts Container Active",
                    action = Action.Click(ID_UP_BUTTON)
                )
            }

            // Fallback to back
            return Decision.Act(
                reason = "YouTube Shorts Container Active",
                action = Action.Back
            )
        }

        return Decision.Pass
    }
}
