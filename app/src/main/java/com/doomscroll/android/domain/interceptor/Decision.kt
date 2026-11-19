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
    object ClickInAppBack : Action
    object Back : Action
    object Home : Action
}
