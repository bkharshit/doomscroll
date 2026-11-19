package com.doomscroll.android.domain.limiter

import com.doomscroll.android.core.model.BreakSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

/**
 * Rationed, persisted breaks.
 *
 * - Emergency breaks (from the block screen) cover one app, [DEFAULT_MAX_BREAKS_PER_APP] per app per day.
 * - A global pause (from the dashboard) covers every app, [MAX_GLOBAL_PAUSES_PER_DAY] per day, and needs a typed intention.
 * - Strict Lockdown allows neither.
 * - Allowances reset at local midnight, including while the process stays alive.
 */
class MindfulBreakManager(
    private val store: BreakStateStore? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val clock: Clock = Clock.systemDefaultZone()
) {
    companion object {
        const val DEFAULT_MAX_BREAKS_PER_APP = 2
        const val MAX_GLOBAL_PAUSES_PER_DAY = 1
        const val GLOBAL_KEY = "*"
    }

    private val lock = Any()

    private val _breakSession = MutableStateFlow(BreakSession())
    val breakSession: StateFlow<BreakSession> = _breakSession.asStateFlow()

    private val _breaksUsedToday = MutableStateFlow<Map<String, Int>>(emptyMap())
    /** Breaks used today per package, plus [GLOBAL_KEY] for dashboard pauses. */
    val breaksUsedToday: StateFlow<Map<String, Int>> = _breaksUsedToday.asStateFlow()

    private val _tickingSeconds = MutableStateFlow(0L)
    val tickingSeconds: StateFlow<Long> = _tickingSeconds.asStateFlow()

    private var usedDay: Long = today()
    private var tickerJob: Job? = null

    /** Latest encoded snapshot; a single collector writes it so saves never land out of order. */
    private val pendingSave = MutableStateFlow<String?>(null)

    init {
        store?.let { s ->
            scope.launch {
                restore(BreakStateCodec.decode(s.load()))
                persistState() // re-encode after merging restored data
                pendingSave.filterNotNull().collect { s.save(it) }
            }
        }
    }

    private fun today(): Long = LocalDate.now(clock).toEpochDay()
    private fun now(): Long = clock.millis()

    /** Resets today's allowances if the local date has changed. Safe to call any time. */
    fun refreshDay() {
        val changed = synchronized(lock) {
            val t = today()
            if (t != usedDay) {
                usedDay = t
                _breaksUsedToday.value = emptyMap()
                true
            } else false
        }
        if (changed) persistState()
    }

    fun getBreaksRemaining(packageName: String, isStrictLockdown: Boolean = false): Int {
        if (isStrictLockdown) return 0
        refreshDay()
        val used = _breaksUsedToday.value[packageName] ?: 0
        return (DEFAULT_MAX_BREAKS_PER_APP - used).coerceAtLeast(0)
    }

    fun canTakeBreak(packageName: String, isStrictLockdown: Boolean = false): Boolean =
        getBreaksRemaining(packageName, isStrictLockdown) > 0

    fun getGlobalPausesRemaining(isStrictLockdown: Boolean = false): Int {
        if (isStrictLockdown) return 0
        refreshDay()
        val used = _breaksUsedToday.value[GLOBAL_KEY] ?: 0
        return (MAX_GLOBAL_PAUSES_PER_DAY - used).coerceAtLeast(0)
    }

    fun requestEmergencyBreak(
        targetPackage: String,
        durationMinutes: Int = 5,
        isStrictLockdown: Boolean = false
    ): Boolean = spend(targetPackage, DEFAULT_MAX_BREAKS_PER_APP, isStrictLockdown) {
        startBreak(durationMinutes, targetPackage, "Emergency Mindful Break")
    }

    fun requestGlobalPause(
        durationMinutes: Int,
        intention: String,
        isStrictLockdown: Boolean = false
    ): Boolean {
        if (intention.isBlank()) return false
        return spend(GLOBAL_KEY, MAX_GLOBAL_PAUSES_PER_DAY, isStrictLockdown) {
            startBreak(durationMinutes, null, intention.trim())
        }
    }

    private inline fun spend(key: String, max: Int, isStrictLockdown: Boolean, start: () -> Unit): Boolean {
        if (isStrictLockdown) return false
        refreshDay()
        synchronized(lock) {
            val used = _breaksUsedToday.value[key] ?: 0
            if (used >= max) return false
            _breaksUsedToday.value = _breaksUsedToday.value + (key to used + 1)
        }
        start()
        return true
    }

    /** Starts a break without spending an allowance. Prefer [requestEmergencyBreak] / [requestGlobalPause]. */
    fun startBreak(
        durationMinutes: Int = 5,
        targetPackage: String? = null,
        intention: String? = null
    ) {
        val durationMillis = durationMinutes * 60 * 1000L
        val session = BreakSession(
            isActive = true,
            endTimeMillis = now() + durationMillis,
            allowedPackage = targetPackage,
            intention = intention
        )
        _breakSession.value = session
        _tickingSeconds.value = durationMillis / 1000L
        startTicker(session)
        persistState()
    }

    private fun startTicker(session: BreakSession) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (_breakSession.value == session) {
                val remaining = ((session.endTimeMillis - now()) / 1000L).coerceAtLeast(0L)
                _tickingSeconds.value = remaining
                if (remaining <= 0) {
                    endBreak()
                    break
                }
                delay(1000L)
            }
        }
    }

    fun cancelBreak() {
        tickerJob?.cancel()
        endBreak()
    }

    private fun endBreak() {
        _breakSession.value = BreakSession()
        _tickingSeconds.value = 0L
        persistState()
    }

    fun isBreakActiveForPackage(packageName: String): Boolean {
        val current = _breakSession.value
        if (!current.isActive) return false
        if (now() >= current.endTimeMillis) {
            cancelBreak()
            return false
        }
        return current.allowedPackage == null || current.allowedPackage == packageName
    }

    private fun persistState() {
        if (store == null) return
        val s = _breakSession.value
        pendingSave.value = BreakStateCodec.encode(
            BreakSnapshot(
                isActive = s.isActive,
                endTimeMillis = s.endTimeMillis,
                allowedPackage = s.allowedPackage,
                intention = s.intention,
                epochDay = synchronized(lock) { usedDay },
                used = _breaksUsedToday.value
            )
        )
    }

    private fun restore(saved: BreakSnapshot?) {
        if (saved == null) return
        synchronized(lock) {
            if (saved.epochDay == usedDay) {
                // Merge with anything spent before the restore finished; never hand back an allowance.
                val merged = (saved.used.keys + _breaksUsedToday.value.keys).associateWith { k ->
                    maxOf(saved.used[k] ?: 0, _breaksUsedToday.value[k] ?: 0)
                }
                _breaksUsedToday.value = merged
            }
        }
        if (!_breakSession.value.isActive && saved.isActive && saved.endTimeMillis > now()) {
            val session = BreakSession(true, saved.endTimeMillis, saved.allowedPackage, saved.intention)
            _breakSession.value = session
            startTicker(session)
        }
    }
}
