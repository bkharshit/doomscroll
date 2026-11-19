package com.doomscroll.android

import com.doomscroll.android.domain.limiter.ShieldAutoResume
import com.doomscroll.android.domain.limiter.ShieldPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ShieldAutoResumeTest {

    /** Clock that follows the test scheduler's virtual time. */
    private class SchedulerClock(private val now: () -> Long) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(now())
    }

    @Test
    fun pausedShield_turnsBackOnAfterTheHour() = runTest {
        val settings = FakeSettingsRepository().apply { nowMillis = { testScheduler.currentTime } }
        ShieldAutoResume(settings, backgroundScope, SchedulerClock { testScheduler.currentTime }).start()
        runCurrent()

        settings.setMasterShieldEnabled(false)
        runCurrent()
        assertEquals(ShieldPolicy.PAUSE_MILLIS, settings.pausedUntil.value)

        advanceTimeBy(ShieldPolicy.PAUSE_MILLIS - 1_000); runCurrent()
        assertFalse(settings.shield.value)

        advanceTimeBy(2_000); runCurrent()
        assertTrue(settings.shield.value)
        assertNull(settings.pausedUntil.value)
    }

    @Test
    fun turningBackOnEarly_cancelsTheTimer() = runTest {
        val settings = FakeSettingsRepository().apply { nowMillis = { testScheduler.currentTime } }
        ShieldAutoResume(settings, backgroundScope, SchedulerClock { testScheduler.currentTime }).start()
        settings.setMasterShieldEnabled(false); runCurrent()
        advanceTimeBy(10_000)
        settings.setMasterShieldEnabled(true); runCurrent()
        // Turn off again: a fresh hour from now, not the old deadline.
        settings.setMasterShieldEnabled(false); runCurrent()
        advanceTimeBy(ShieldPolicy.PAUSE_MILLIS - 5_000); runCurrent()
        assertFalse(settings.shield.value)
        advanceTimeBy(10_000); runCurrent()
        assertTrue(settings.shield.value)
    }

    @Test
    fun pauseWithoutDeadline_getsOneStampedAndResumes() = runTest {
        val settings = FakeSettingsRepository().apply { nowMillis = { testScheduler.currentTime } }
        settings.shield.value = false // legacy: off with no stored deadline
        ShieldAutoResume(settings, backgroundScope, SchedulerClock { testScheduler.currentTime }).start()
        runCurrent()
        assertEquals(ShieldPolicy.PAUSE_MILLIS, settings.pausedUntil.value)
        advanceTimeBy(ShieldPolicy.PAUSE_MILLIS + 1_000); runCurrent()
        assertTrue(settings.shield.value)
    }

    @Test
    fun expiredDeadline_resumesImmediately() = runTest {
        val settings = FakeSettingsRepository()
        settings.shield.value = false
        settings.pausedUntil.value = 0L
        advanceTimeBy(5_000)
        ShieldAutoResume(settings, backgroundScope, SchedulerClock { testScheduler.currentTime }).start()
        runCurrent()
        assertTrue(settings.shield.value)
    }
}
