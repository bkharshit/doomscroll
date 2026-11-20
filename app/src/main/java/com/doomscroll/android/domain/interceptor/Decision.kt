package com.doomscroll.android.domain.interceptor

sealed interface Decision {
    object Pass : Decision

    /**
     * Short-form content is on screen but the rule acted moments ago. Evaluate again after [millis]
     * instead of letting it through: no further events may arrive while a video just plays.
     */
    data class RetryAfter(val millis: Long) : Decision

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
