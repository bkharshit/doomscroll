package com.doomscroll.android.domain.interceptor

sealed interface Decision {
    object Pass : Decision
    data class Act(
        val reason: String,
        val action: Action
    ) : Decision
}

sealed interface Action {
    data class Click(val targetViewId: String) : Action
    /**
     * Click the app's own back control: first by [viewIds], then by a localised label
     * ([BackLabels]); if neither is found, fall back to the system Back action.
     */
    data class ClickInAppBack(val viewIds: List<String> = emptyList()) : Action
    object Back : Action
    object Home : Action
}
