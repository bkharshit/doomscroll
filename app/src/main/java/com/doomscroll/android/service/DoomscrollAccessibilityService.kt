package com.doomscroll.android.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.doomscroll.android.data.repository.MetricsRepository
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.data.repository.UsageRepository
import com.doomscroll.android.domain.interceptor.InterceptionEngine
import com.doomscroll.android.domain.limiter.Enforcer
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.doomscroll.android.R
import com.doomscroll.android.domain.interceptor.Decision
import com.doomscroll.android.domain.interceptor.EvalThrottle
import com.doomscroll.android.domain.interceptor.Screen
import java.util.concurrent.atomic.AtomicBoolean
import com.doomscroll.android.domain.limiter.ShieldAutoResume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DoomscrollAccessibilityService : AccessibilityService() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var usageRepository: UsageRepository
    @Inject lateinit var metricsRepository: MetricsRepository
    @Inject lateinit var interceptionEngine: InterceptionEngine
    @Inject lateinit var breakManager: MindfulBreakManager
    @Inject lateinit var enforcer: Enforcer

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

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

    // Interception runs off the main thread (D5): events are coalesced per window into one evaluation.
    private val evalThread = HandlerThread("a11y-eval").apply { start() }
    private val evalHandler = Handler(evalThread.looper)
    private val evalRunnable = Runnable { evaluateDirtyPackage() }
    private val evalScheduled = AtomicBoolean(false)
    private val evalThrottle = EvalThrottle()
    @Volatile private var dirtyPackage: String? = null

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
        private const val RETRY_SLACK_MS = 50L
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "DoomscrollAccessibilityService onCreate")

        actionExecutor = ActionExecutor(this)

        sessionTracker = ForegroundSessionTracker(
            usageRepository = usageRepository,
            settingsRepository = settingsRepository,
            breakManager = breakManager,
            enforcer = enforcer,
            scope = serviceScope,
            log = { Log.d("ForegroundTracker", it) }
        )

        // A paused shield always comes back on by itself.
        ShieldAutoResume(settingsRepository, serviceScope).start()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenStateReceiver, filter)

        serviceScope.launch {
            settingsRepository.isMasterShieldEnabled.collect {
                isMasterShieldActive = it
                sessionTracker.resyncCurrentSession()
            }
        }
        serviceScope.launch {
            settingsRepository.isReelsInterceptionEnabled.collect { isReelsInterceptionActive = it }
        }
        serviceScope.launch {
            settingsRepository.isShortsInterceptionEnabled.collect { isShortsInterceptionActive = it }
        }
        // Limit or per-app block toggle changed: re-plan the current session.
        serviceScope.launch {
            settingsRepository.getAppLimits()
                .map { limits -> limits.map { Triple(it.monitoredApp.packageName, it.dailyLimitMinutes, it.isAppBlockingEnabled) } }
                .distinctUntilChanged()
                .collect { sessionTracker.resyncCurrentSession() }
        }
        // A break starting or ending: when it ends, the block must come back even if the user never leaves the app.
        serviceScope.launch {
            breakManager.breakSession
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
                                serviceScope.launch { settingsRepository.setMasterShieldEnabled(true) }
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
        val isWindowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

        // Foreground tracking: any window change (including our own block screen) may mean a new app.
        // Resolve from the active window instead of trusting the event's package.
        if (isWindowChange) requestForegroundResolve(FOREGROUND_RESOLVE_DELAY_MS)

        // Cheap gate on the main thread: only volatile reads, no IPC.
        if (packageName == applicationContext.packageName) return
        if (!isMasterShieldActive) return
        val rule = interceptionEngine.ruleFor(packageName) ?: return
        if (!isRuleEnabled(rule.id)) return
        if (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true) return
        if (breakManager.isBreakActiveForPackage(packageName)) return

        // Coalesce: one evaluation per window, on the eval thread. Window changes (tab switches,
        // a player opening) are evaluated straight away.
        dirtyPackage = packageName
        if (isWindowChange) {
            evalHandler.removeCallbacks(evalRunnable)
            evalScheduled.set(true)
            evalHandler.post(evalRunnable)
        } else if (evalScheduled.compareAndSet(false, true)) {
            evalHandler.postDelayed(evalRunnable, evalThrottle.windowMillis)
        }
    }

    private fun isRuleEnabled(ruleId: String): Boolean = when (ruleId) {
        "instagram.reels" -> isReelsInterceptionActive
        "youtube.shorts" -> isShortsInterceptionActive
        else -> true
    }

    /** Runs on the "a11y-eval" thread: snapshot once, evaluate, hand any action to the main thread. */
    private fun evaluateDirtyPackage() {
        evalScheduled.set(false)
        val pkg = dirtyPackage ?: return
        val root = rootInActiveWindow ?: return
        val pool = NodePool().apply { track(root) }
        val started = SystemClock.elapsedRealtime()
        try {
            if (root.packageName?.toString() != pkg) return
            val screen = Screen(pkg) { viewId ->
                root.findAccessibilityNodeInfosByViewId(viewId).orEmpty().map { A11yUiNode(pool.track(it), pool) }
            }
            val decision = interceptionEngine.evaluate(screen)
            when (decision) {
                is Decision.Act -> mainHandler.post { executeOnMain(pkg, decision) }
                // Still on short-form content right after acting: look again once the cooldown ends.
                is Decision.RetryAfter -> {
                    evalHandler.removeCallbacks(evalRunnable)
                    evalScheduled.set(true)
                    evalHandler.postDelayed(evalRunnable, decision.millis + RETRY_SLACK_MS)
                }
                Decision.Pass -> Unit
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating $pkg", e)
        } finally {
            pool.recycleAll()
            val took = SystemClock.elapsedRealtime() - started
            if (evalThrottle.record(took)) {
                Log.w(TAG, "SlowTree: evaluation took ${took}ms, coalescing window now ${evalThrottle.windowMillis}ms")
            }
        }
    }

    /** Actions need the live tree (it may have changed since the snapshot), so re-fetch it here. */
    private fun executeOnMain(pkg: String, decision: Decision.Act) {
        val root = rootInActiveWindow ?: return
        try {
            if (root.packageName?.toString() != pkg) return
            if (actionExecutor.execute(decision.action, root)) {
                Log.w(TAG, "INTERCEPTED: [$pkg] ${decision.reason} -> ${decision.action}")
                interceptionEngine.ruleFor(pkg)?.let { rule ->
                    serviceScope.launch { metricsRepository.recordReelBlocked(rule.id) }
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                root.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "DoomscrollAccessibilityService onInterrupt")
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(resolveForegroundRunnable)
        evalHandler.removeCallbacksAndMessages(null)
        evalThread.quitSafely()
        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
        serviceScope.cancel()
        Log.i(TAG, "DoomscrollAccessibilityService onDestroy")
    }
}
