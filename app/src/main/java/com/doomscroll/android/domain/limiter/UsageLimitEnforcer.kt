package com.doomscroll.android.domain.limiter

import android.content.Context
import android.content.Intent
import com.doomscroll.android.core.util.TimeUtils
import com.doomscroll.android.ui.blockscreen.BlockedAppActivity
import java.util.concurrent.ConcurrentHashMap

class UsageLimitEnforcer(
    private val context: Context
) : Enforcer {
    // Per-package debounce to prevent cross-package debounce collision (R25)
    private val lastLaunchPerPackage = ConcurrentHashMap<String, Long>()
    private val debounceWindowMillis = 2000L

    override fun enforceLimit(
        packageName: String,
        usedMinutes: Int,
        dailyLimitMinutes: Int,
        isSleepDowntime: Boolean
    ): Boolean {
        if (dailyLimitMinutes <= 0 && !isSleepDowntime) return false

        val isOverLimit = isSleepDowntime || (usedMinutes >= dailyLimitMinutes)
        if (!isOverLimit) return false

        val now = System.currentTimeMillis()
        val lastLaunch = lastLaunchPerPackage[packageName] ?: 0L
        if (now - lastLaunch < debounceWindowMillis) {
            return true // Already triggered recently for this package
        }
        lastLaunchPerPackage[packageName] = now

        val reason = if (isSleepDowntime) {
            "Scheduled Downtime is active"
        } else {
            "Daily limit of ${TimeUtils.formatMinutes(dailyLimitMinutes)} reached (${TimeUtils.formatMinutes(usedMinutes)} used)"
        }

        // Don't press Home first: the Home transition lands after the block screen starts and
        // the system trims the block screen's excludeFromRecents task, so it vanishes instantly.
        // Re-entering the blocked app from recents re-triggers the block via the session tracker.
        launchBlockedScreen(packageName, reason, isSleepDowntime)
        return true
    }

    private fun launchBlockedScreen(packageName: String, reason: String, isSleep: Boolean) {
        val intent = Intent(context, BlockedAppActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockedAppActivity.EXTRA_REASON, reason)
            putExtra(BlockedAppActivity.EXTRA_IS_SLEEP, isSleep)
        }
        context.startActivity(intent)
    }
}
