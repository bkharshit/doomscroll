package com.doomscroll.android.domain.interceptor

/**
 * Labels that identify an in-app Back/Up control, in the target app's own UI language.
 *
 * The labels are read from the target app's string resources (so they're already translated), then
 * an English list is added as a fallback. Matching is exact and case-insensitive on a node's
 * content description or text, so captions that merely contain "back" never match.
 */
object BackLabels {
    /** String resource names commonly used for back/up/close controls (AppCompat, Material, app-specific). */
    val RESOURCE_NAMES = listOf(
        "back",
        "go_back",
        "navigate_up",
        "abc_action_bar_up_description",
        "action_bar_up_description",
        "abc_toolbar_collapse_description",
        "mtrl_picker_navigate_up",
        "close"
    )

    val ENGLISH_FALLBACK = listOf("Back", "Navigate up", "Go back", "Close")

    /** [lookup] returns the target app's localised string for a resource name, or null if it has none. */
    fun resolve(lookup: (resourceName: String) -> String?): Set<String> =
        (RESOURCE_NAMES.mapNotNull { name -> runCatching { lookup(name) }.getOrNull() } + ENGLISH_FALLBACK)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    fun matches(labels: Set<String>, text: CharSequence?, contentDescription: CharSequence?): Boolean =
        contentDescription?.toString()?.trim()?.lowercase() in labels ||
            text?.toString()?.trim()?.lowercase() in labels
}
