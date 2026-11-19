package com.doomscroll.android.core.model

/**
 * Standard monitored social apps that encourage doomscrolling.
 */
enum class MonitoredApp(
    val id: String,
    val displayName: String,
    val packageName: String,
    val supportsShortsInterception: Boolean,
    val defaultDailyLimitMinutes: Int
) {
    INSTAGRAM(
        id = "instagram",
        displayName = "Instagram",
        packageName = "com.instagram.android",
        supportsShortsInterception = true,
        defaultDailyLimitMinutes = 30
    ),
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        packageName = "com.google.android.youtube",
        supportsShortsInterception = true,
        defaultDailyLimitMinutes = 45
    ),
    TIKTOK(
        id = "tiktok",
        displayName = "TikTok",
        packageName = "com.zhiliaoapp.musically",
        supportsShortsInterception = false, // Entire app is short video
        defaultDailyLimitMinutes = 20
    ),
    REDDIT(
        id = "reddit",
        displayName = "Reddit",
        packageName = "com.reddit.frontpage",
        supportsShortsInterception = false,
        defaultDailyLimitMinutes = 40
    ),
    TWITTER_X(
        id = "twitter",
        displayName = "X (Twitter)",
        packageName = "com.twitter.android",
        supportsShortsInterception = false,
        defaultDailyLimitMinutes = 30
    );

    companion object {
        fun fromPackage(packageName: String): MonitoredApp? {
            return entries.firstOrNull { it.packageName == packageName }
        }

        fun fromId(id: String): MonitoredApp? {
            return entries.firstOrNull { it.id == id }
        }
    }
}
