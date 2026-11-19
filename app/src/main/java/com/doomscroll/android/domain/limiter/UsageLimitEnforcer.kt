package com.doomscroll.android.domain.limiter

import android.content.Context
import android.content.Intent
import com.doomscroll.android.ui.blockscreen.BlockedAppActivity
import java.util.concurrent.atomic.AtomicLong

class UsageLimitEnforcer(
    private val context: Context
) {
    // Cooldown to prevent launching block screen repeatedly in quick bursts
    private val lastLaunchTimestamp = AtomicLong(0L)
    private val debounceWindowMillis = 2000L

    fun enforceLimit(
        packageName: String,
        usedMinutes: Int,
        dailyLimitMinutes: Int,
        isSleepDowntime: Boolean = false
    ): Boolean {
        if (dailyLimitMinutes <= 0 && !isSleepDowntime) return false

        val isOverLimit = isSleepDowntime || (usedMinutes >= dailyLimitMinutes)
        if (!isOverLimit) return false

        val now = System.currentTimeMillis()
        val lastLaunch = lastLaunchTimestamp.get()
        if (now - lastLaunch < debounceWindowMillis) {
            return true // Already triggered recently
        }
        lastLaunchTimestamp.set(now)

        val reason = if (isSleepDowntime) {
            "Scheduled Downtime is active"
        } else {
            "Daily limit of ${com.doomscroll.android.core.util.TimeUtils.formatMinutes(dailyLimitMinutes)} reached (${com.doomscroll.android.core.util.TimeUtils.formatMinutes(usedMinutes)} used)"
        }

        launchBlockedScreen(packageName, reason, isSleepDowntime)
        return true
    }

    private fun launchBlockedScreen(packageName: String, reason: String, isSleep: Boolean) {
        val intent = Intent(context, BlockedAppActivity::class.java).apply {
            // Essential flags for overlay enforcer
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedAppActivity.EXTRA_PACKAGE_NAME, packageName)
            putExtra(BlockedAppActivity.EXTRA_REASON, reason)
            putExtra(BlockedAppActivity.EXTRA_IS_SLEEP, isSleep)
        }
        context.startActivity(intent)
    }
}
