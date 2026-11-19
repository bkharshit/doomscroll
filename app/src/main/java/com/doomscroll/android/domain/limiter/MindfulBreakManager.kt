package com.doomscroll.android.domain.limiter

import android.os.Handler
import android.os.Looper
import com.doomscroll.android.core.model.BreakSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MindfulBreakManager {

    private val _breakSession = MutableStateFlow(BreakSession())
    val breakSession: StateFlow<BreakSession> = _breakSession.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var onExpiryCallback: (() -> Unit)? = null

    private val expiryRunnable = Runnable {
        val current = _breakSession.value
        _breakSession.value = BreakSession()
        onExpiryCallback?.invoke()
    }

    fun startBreak(
        durationMinutes: Int = 5,
        targetPackage: String? = null,
        intention: String? = null,
        onExpire: (() -> Unit)? = null
    ) {
        mainHandler.removeCallbacks(expiryRunnable)
        this.onExpiryCallback = onExpire

        val durationMillis = durationMinutes * 60 * 1000L
        val endTime = System.currentTimeMillis() + durationMillis

        _breakSession.value = BreakSession(
            isActive = true,
            endTimeMillis = endTime,
            allowedPackage = targetPackage,
            intention = intention
        )

        mainHandler.postDelayed(expiryRunnable, durationMillis)
    }

    fun cancelBreak() {
        mainHandler.removeCallbacks(expiryRunnable)
        _breakSession.value = BreakSession()
    }

    fun isBreakActiveForPackage(packageName: String): Boolean {
        val current = _breakSession.value
        if (!current.isActive) return false
        if (current.isExpired) {
            cancelBreak()
            return false
        }
        return current.allowedPackage == null || current.allowedPackage == packageName
    }
}
