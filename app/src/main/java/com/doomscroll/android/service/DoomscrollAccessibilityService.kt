package com.doomscroll.android.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.doomscroll.android.DoomscrollApp
import com.doomscroll.android.R
import com.doomscroll.android.domain.interceptor.Decision
import com.doomscroll.android.domain.interceptor.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DoomscrollAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val app: DoomscrollApp
        get() = applicationContext as DoomscrollApp

    private val powerManager by lazy {
        getSystemService(Context.POWER_SERVICE) as? PowerManager
    }

    private val keyguardManager by lazy {
        getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    private lateinit var actionExecutor: ActionExecutor
    private lateinit var sessionTracker: ForegroundSessionTracker

    // In-memory hot state for zero-latency synchronous evaluation
    @Volatile private var isMasterShieldActive: Boolean = true
    @Volatile private var isReelsInterceptionActive: Boolean = true
    @Volatile private var isShortsInterceptionActive: Boolean = true

    private val resolveForegroundRunnable = Runnable { resolveAndUpdateForeground() }

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    mainHandler.removeCallbacks(resolveForegroundRunnable)
                    sessionTracker.onScreenOff()
                }
                // Screen-off cleared the tracked app, so look again at what's on screen after unlock.
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> requestForegroundResolve(FOREGROUND_RESOLVE_DELAY_MS)
            }
        }
    }

    companion object {
        private const val TAG = "DoomscrollA11y"
        /** Lets window transitions settle before asking which app window is active. */
        private const val FOREGROUND_RESOLVE_DELAY_MS = 300L
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DoomscrollAccessibilityService onCreate")

        actionExecutor = ActionExecutor(this)

        sessionTracker = ForegroundSessionTracker(
            usageRepository = app.usageRepository,
            settingsRepository = app.settingsRepository,
            breakManager = app.breakManager,
            enforcer = app.usageLimitEnforcer,
            scope = serviceScope,
            log = { Log.d("ForegroundTracker", it) }
        )

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)

        serviceScope.launch {
            app.settingsRepository.isMasterShieldEnabled.collect {
                isMasterShieldActive = it
                sessionTracker.resyncCurrentSession()
            }
        }
        serviceScope.launch {
            app.settingsRepository.isReelsInterceptionEnabled.collect { isReelsInterceptionActive = it }
        }
        serviceScope.launch {
            app.settingsRepository.isShortsInterceptionEnabled.collect { isShortsInterceptionActive = it }
        }
        // Limit or per-app block toggle changed: re-plan the current session.
        serviceScope.launch {
            app.settingsRepository.getAppLimits()
                .map { limits -> limits.map { Triple(it.monitoredApp.packageName, it.dailyLimitMinutes, it.isAppBlockingEnabled) } }
                .distinctUntilChanged()
                .collect { sessionTracker.resyncCurrentSession() }
        }
        // A break starting or ending: when it ends, the block must come back even if the user never leaves the app.
        serviceScope.launch {
            app.breakManager.breakSession
                .map { it.isActive to it.allowedPackage }
                .distinctUntilChanged()
                .collect { sessionTracker.resyncCurrentSession() }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "DoomscrollAccessibilityService connected and ACTIVE")
        requestForegroundResolve(0L)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                accessibilityButtonController.registerAccessibilityButtonCallback(
                    object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
                        override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController?) {
                            // One-way: the button can switch protection on, but pausing goes through the
                            // app, where it costs a wait (and is refused under Strict Lockdown).
                            val msg = if (isMasterShieldActive) {
                                getString(R.string.toast_shield_pause_in_app)
                            } else {
                                serviceScope.launch { app.settingsRepository.setMasterShieldEnabled(true) }
                                getString(R.string.toast_shield_active)
                            }
                            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "AccessibilityButtonController setup: ${e.message}")
            }
        }
    }

    private fun requestForegroundResolve(delayMs: Long) {
        mainHandler.removeCallbacks(resolveForegroundRunnable)
        mainHandler.postDelayed(resolveForegroundRunnable, delayMs)
    }

    private fun resolveAndUpdateForeground() {
        if (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true) return
        // Unknown (e.g. notification shade over the app): keep the current session rather than ending it.
        val pkg = activeApplicationPackage() ?: return
        sessionTracker.onForegroundPackageChanged(pkg)
    }

    /** Package of the active application window, ignoring system UI, the keyboard and overlays. */
    private fun activeApplicationPackage(): String? {
        val all = try {
            windows
        } catch (e: Exception) {
            return null
        }
        val appWindow = all.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
            ?: all.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
        val root = appWindow?.root
        val pkg = root?.packageName?.toString()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            @Suppress("DEPRECATION")
            root?.recycle()
            @Suppress("DEPRECATION")
            all.forEach { it.recycle() }
        }
        return pkg
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // Foreground tracking: any window change (including our own block screen) may mean a new app.
        // Resolve from the active window instead of trusting the event's package.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            requestForegroundResolve(FOREGROUND_RESOLVE_DELAY_MS)
        }

        // 1. Never intercept self
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
                        val uiNode = A11yUiNode(root)
                        val screen = Screen(packageName, uiNode)
                        val decision = app.interceptionEngine.evaluate(screen)

                        if (decision is Decision.Act) {
                            val executed = actionExecutor.execute(decision.action, root)
                            if (executed) {
                                Log.w(TAG, "🚨 INTERCEPTED: [$packageName] Reason: ${decision.reason} -> Action: ${decision.action}")
                                app.interceptionEngine.ruleFor(packageName)?.let { rule ->
                                    serviceScope.launch { app.metricsRepository.recordReelBlocked(rule.id) }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error evaluating accessibility event for $packageName", e)
                    } finally {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            @Suppress("DEPRECATION")
                            root.recycle()
                        }
                    }
                }
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "DoomscrollAccessibilityService onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(resolveForegroundRunnable)
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        serviceScope.cancel()
        Log.i(TAG, "DoomscrollAccessibilityService onDestroy")
    }
}
