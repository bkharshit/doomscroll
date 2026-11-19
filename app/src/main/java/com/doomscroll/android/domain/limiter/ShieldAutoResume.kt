package com.doomscroll.android.domain.limiter

import com.doomscroll.android.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Clock

object ShieldPolicy {
    /** Turning protection off is a pause, not a permanent switch: it comes back on after this long. */
    const val PAUSE_MINUTES = 60L
    const val PAUSE_MILLIS = PAUSE_MINUTES * 60_000L
}

/**
 * Switches the master shield back on when its pause runs out. Survives process death because the
 * deadline is stored ([SettingsRepository.shieldPausedUntilMillis]); a pause with no stored deadline
 * (turned off before this existed) gets one stamped now.
 */
class ShieldAutoResume(
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(): Job = scope.launch {
        combine(settings.isMasterShieldEnabled, settings.shieldPausedUntilMillis) { on, until -> on to until }
            .distinctUntilChanged()
            .collectLatest { (on, until) ->
                if (on) return@collectLatest
                if (until == null) {
                    settings.setMasterShieldEnabled(false) // stamps a deadline, which re-emits
                    return@collectLatest
                }
                delay((until - clock.millis()).coerceAtLeast(0L))
                settings.setMasterShieldEnabled(true)
            }
    }
}
