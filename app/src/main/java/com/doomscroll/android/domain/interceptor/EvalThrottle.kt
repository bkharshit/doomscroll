package com.doomscroll.android.domain.interceptor

/**
 * Adaptive coalescing window for interception evaluations (D5).
 *
 * Content-change events are batched into one evaluation per [windowMillis]. If evaluations are slow
 * (over [slowThresholdMillis]) [slowStreakToBackOff] times in a row, the window doubles up to
 * [maxWindowMillis]; a run of fast evaluations brings it back down.
 */
class EvalThrottle(
    private val baseWindowMillis: Long = 150L,
    private val maxWindowMillis: Long = 600L,
    private val slowThresholdMillis: Long = 50L,
    private val slowStreakToBackOff: Int = 3,
    private val fastStreakToRecover: Int = 20
) {
    @Volatile
    var windowMillis: Long = baseWindowMillis
        private set
    private var slowStreak = 0
    private var fastStreak = 0

    /** Records how long an evaluation took; returns true if the window just grew (log a SlowTree). */
    @Synchronized
    fun record(durationMillis: Long): Boolean {
        if (durationMillis > slowThresholdMillis) {
            fastStreak = 0
            if (++slowStreak >= slowStreakToBackOff) {
                slowStreak = 0
                val grown = (windowMillis * 2).coerceAtMost(maxWindowMillis)
                val changed = grown != windowMillis
                windowMillis = grown
                return changed
            }
        } else {
            slowStreak = 0
            if (++fastStreak >= fastStreakToRecover && windowMillis > baseWindowMillis) {
                fastStreak = 0
                windowMillis = (windowMillis / 2).coerceAtLeast(baseWindowMillis)
            }
        }
        return false
    }
}
