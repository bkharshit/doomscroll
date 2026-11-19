package com.doomscroll.android.service

import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.data.repository.UsageRepository
import com.doomscroll.android.domain.limiter.Enforcer
import com.doomscroll.android.domain.limiter.LimitDecider
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tracks which monitored app is in the foreground and blocks it when its daily budget runs out,
 * including while the user stays in the app.
 *
 * The caller must pass the *resolved* foreground package (the active application window), not the
 * package of whatever window event arrived: launcher animations, system UI and the keyboard all emit
 * window events while the user is still in the app.
 *
 * Call [resyncCurrentSession] whenever anything that affects the decision changes (shield, limits,
 * per-app block toggle, a break starting or ending).
 *
 * No Android dependencies, so it runs in JVM tests with a TestScope.
 */
class ForegroundSessionTracker(
    private val usageRepository: UsageRepository,
    private val settingsRepository: SettingsRepository,
    private val breakManager: MindfulBreakManager,
    private val enforcer: Enforcer,
    private val scope: CoroutineScope,
    private val log: (String) -> Unit = {}
) {
    companion object {
        /** Floor between re-checks after the first deadline, so a bad usage reading can't spin. */
        const val MIN_RECHECK_SECONDS = 15L
    }

    private val lock = Any()

    @Volatile
    var currentForegroundPkg: String? = null
        private set
    private var deadlineJob: Job? = null

    fun onForegroundPackageChanged(packageName: String?) = synchronized(lock) {
        val monitored = packageName?.takeIf { MonitoredApp.fromPackage(it) != null }
        if (monitored == null) {
            if (currentForegroundPkg != null) {
                log("Left monitored app: $currentForegroundPkg (now ${packageName ?: "none"})")
                currentForegroundPkg = null
                cancelDeadline()
            }
            return@synchronized
        }
        if (currentForegroundPkg == monitored && deadlineJob?.isActive == true) return@synchronized
        currentForegroundPkg = monitored
        schedule(monitored)
    }

    fun onScreenOff() = onForegroundPackageChanged(null)

    fun resyncCurrentSession() = synchronized(lock) {
        currentForegroundPkg?.let { schedule(it) }
    }

    private fun cancelDeadline() {
        deadlineJob?.cancel()
        deadlineJob = null
    }

    private fun schedule(packageName: String) {
        cancelDeadline()
        deadlineJob = scope.launch { runSession(packageName) }
    }

    private suspend fun runSession(packageName: String) {
        var firstCheck = true
        while (currentForegroundPkg == packageName) {
            if (!settingsRepository.isMasterShieldEnabled.first()) return
            // A break ending calls resyncCurrentSession(), which restarts this loop.
            if (breakManager.isBreakActiveForPackage(packageName)) return
            if (!settingsRepository.isAppBlockEnabled(packageName).first()) return
            val limitMinutes = settingsRepository.getLimitForPackage(packageName).first()
            if (limitMinutes <= 0) return

            val usedSeconds = usageRepository.getTodayUsageSeconds(packageName)
            if (LimitDecider.isOverLimit(usedSeconds, limitMinutes)) {
                log("Over limit in $packageName: ${usedSeconds / 60}m >= ${limitMinutes}m, blocking")
                enforcer.enforceLimit(packageName, (usedSeconds / 60).toInt(), limitMinutes)
                return
            }

            val remaining = LimitDecider.calculateRemainingSeconds(usedSeconds, limitMinutes) ?: return
            val wait = if (firstCheck) remaining.coerceAtLeast(1L) else remaining.coerceAtLeast(MIN_RECHECK_SECONDS)
            log("Next limit check for $packageName in ${wait}s")
            firstCheck = false
            delay(wait * 1000L)
        }
    }
}
