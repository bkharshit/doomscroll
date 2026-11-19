package com.doomscroll.android.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.doomscroll.android.DoomscrollApp
import com.doomscroll.android.core.model.MonitoredApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DoomscrollAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val app: DoomscrollApp
        get() = applicationContext as DoomscrollApp

    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private val keyguardManager by lazy {
        getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    // In-memory hot state for zero-latency synchronous evaluation
    @Volatile private var isMasterShieldActive: Boolean = true
    @Volatile private var isReelsInterceptionActive: Boolean = true
    @Volatile private var isShortsInterceptionActive: Boolean = true

    companion object {
        private const val TAG = "DoomscrollA11y"
        var isServiceRunning: Boolean = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DoomscrollAccessibilityService onCreate")

        serviceScope.launch {
            app.settingsRepository.isMasterShieldEnabled.collect {
                isMasterShieldActive = it
                Log.d(TAG, "Master shield updated: $it")
            }
        }
        serviceScope.launch {
            app.settingsRepository.isReelsInterceptionEnabled.collect {
                isReelsInterceptionActive = it
                Log.d(TAG, "Reels interception updated: $it")
            }
        }
        serviceScope.launch {
            app.settingsRepository.isShortsInterceptionEnabled.collect {
                isShortsInterceptionActive = it
                Log.d(TAG, "Shorts interception updated: $it")
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "DoomscrollAccessibilityService connected and ACTIVE")

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                accessibilityButtonController.registerAccessibilityButtonCallback(
                    object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                        override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController?) {
                            val newState = !isMasterShieldActive
                            serviceScope.launch {
                                app.settingsRepository.setMasterShieldEnabled(newState)
                            }
                            val msg = if (newState) {
                                "🛡️ Doomscroll: Shield ACTIVE (Reels & Shorts Blocked)"
                            } else {
                                "⏸️ Doomscroll: Shield PAUSED"
                            }
                            android.widget.Toast.makeText(applicationContext, msg, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "AccessibilityButtonController setup: ${e.message}")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // 1. Never intercept self or system launcher
        if (packageName == applicationContext.packageName) return

        // 2. Screen state check: Skip if screen is off or keyguard is locked
        if (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true) {
            return
        }

        // 3. Temporary Mindful Break check ("Post Mode")
        if (app.breakManager.isBreakActiveForPackage(packageName)) {
            return
        }

        // 4. Surgical Short-Form Video Interception (synchronous, sub-50ms)
        if (isMasterShieldActive && app.interceptionEngine.shouldProcessPackage(packageName)) {
            val isEnabledForApp = when (packageName) {
                "com.instagram.android" -> isReelsInterceptionActive
                "com.google.android.youtube" -> isShortsInterceptionActive
                else -> true
            }

            if (isEnabledForApp) {
                val root = rootInActiveWindow
                if (root != null) {
                    try {
                        val result = app.interceptionEngine.processEvent(
                            packageName = packageName,
                            root = root,
                            service = this@DoomscrollAccessibilityService,
                            allowSentReelsInChat = true
                        )

                        if (result.wasIntercepted) {
                            Log.w(TAG, "🚨 INTERCEPTED: [${packageName}] Reason: ${result.reason} -> Action: ${result.actionTaken}")
                            serviceScope.launch {
                                app.metricsRepository.recordReelBlocked()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error evaluating accessibility event for $packageName", e)
                    } finally {
                        @Suppress("DEPRECATION")
                        root.recycle()
                    }
                }
            }
        }

        // 5. Daily App Usage Quota Enforcement
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            checkAndEnforceAppLimit(packageName)
        }
    }

    private fun checkAndEnforceAppLimit(packageName: String) {
        if (!isMasterShieldActive) return
        if (app.breakManager.isBreakActiveForPackage(packageName)) return
        val monitored = MonitoredApp.fromPackage(packageName) ?: return

        serviceScope.launch {
            val isBlockEnabled = app.settingsRepository.isAppBlockEnabled(packageName).first()
            if (!isBlockEnabled) return@launch

            val limitMinutes = app.settingsRepository.getLimitForPackage(packageName).first()
            if (limitMinutes <= 0) return@launch

            val usedSeconds = app.usageRepository.getTodayUsageSeconds(packageName)
            val usedMinutes = (usedSeconds / 60).toInt()

            val triggered = app.usageLimitEnforcer.enforceLimit(
                packageName = packageName,
                usedMinutes = usedMinutes,
                dailyLimitMinutes = limitMinutes
            )
            if (triggered) {
                Log.w(TAG, "🔒 QUOTA EXCEEDED for $packageName: used=${usedMinutes}m, limit=${limitMinutes}m")
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "DoomscrollAccessibilityService onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        serviceScope.cancel()
        Log.i(TAG, "DoomscrollAccessibilityService onDestroy")
    }
}
