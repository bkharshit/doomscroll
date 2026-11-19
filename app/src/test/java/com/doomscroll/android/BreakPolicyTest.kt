package com.doomscroll.android

import com.doomscroll.android.domain.limiter.BreakSnapshot
import com.doomscroll.android.domain.limiter.BreakStateCodec
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BreakPolicyTest {

    private val ig = "com.instagram.android"
    private fun clockAt(iso: String) = MutableClock(Instant.parse(iso))

    @Test
    fun allowances_resetAtMidnight_whileProcessStaysAlive() = runTest {
        val clock = clockAt("2026-11-19T23:00:00Z")
        val m = MindfulBreakManager(store = null, scope = backgroundScope, clock = clock)
        assertTrue(m.requestEmergencyBreak(ig))
        m.cancelBreak()
        assertTrue(m.requestEmergencyBreak(ig))
        m.cancelBreak()
        assertEquals(0, m.getBreaksRemaining(ig))

        clock.advanceHours(2) // 01:00 next day
        assertEquals(MindfulBreakManager.DEFAULT_MAX_BREAKS_PER_APP, m.getBreaksRemaining(ig))
        assertTrue(m.requestEmergencyBreak(ig))
    }

    @Test
    fun persistedCounts_areStampedWithTheirOwnDay_notToday() = runTest {
        val clock = clockAt("2026-11-19T23:30:00Z")
        val store = InMemoryBreakStore()
        val m = MindfulBreakManager(store = store, scope = backgroundScope, clock = clock)
        runCurrent()
        m.requestEmergencyBreak(ig); m.cancelBreak()
        m.requestEmergencyBreak(ig); m.cancelBreak()
        runCurrent()

        clock.advanceHours(1) // past midnight
        m.refreshDay()
        runCurrent()

        val saved = BreakStateCodec.decode(store.value)!!
        assertEquals(LocalDate.of(2026, 11, 20).toEpochDay(), saved.epochDay)
        assertTrue("yesterday's counts must not carry into today", saved.used.isEmpty())

        // A fresh process today sees the full allowance.
        val restarted = MindfulBreakManager(store = store, scope = backgroundScope, clock = clock)
        runCurrent()
        assertEquals(2, restarted.getBreaksRemaining(ig))
    }

    @Test
    fun restoredCounts_fromToday_areHonoured_andStaleOnesIgnored() = runTest {
        val clock = clockAt("2026-11-19T12:00:00Z")
        val today = LocalDate.of(2026, 11, 19).toEpochDay()
        val todayStore = InMemoryBreakStore(BreakStateCodec.encode(BreakSnapshot(false, 0, null, null, today, mapOf(ig to 2, "*" to 1))))
        val m = MindfulBreakManager(store = todayStore, scope = backgroundScope, clock = clock)
        runCurrent()
        assertEquals(0, m.getBreaksRemaining(ig))
        assertEquals(0, m.getGlobalPausesRemaining())

        val staleStore = InMemoryBreakStore(BreakStateCodec.encode(BreakSnapshot(false, 0, null, null, today - 1, mapOf(ig to 2))))
        val m2 = MindfulBreakManager(store = staleStore, scope = backgroundScope, clock = clock)
        runCurrent()
        assertEquals(2, m2.getBreaksRemaining(ig))
    }

    @Test
    fun globalPause_isRationed_needsIntention_andRefusedInStrictMode() = runTest {
        val m = MindfulBreakManager(store = null, scope = backgroundScope, clock = clockAt("2026-11-19T12:00:00Z"))
        assertFalse("blank intention", m.requestGlobalPause(5, "   "))
        assertFalse("strict lockdown", m.requestGlobalPause(5, "Reply to mom", isStrictLockdown = true))
        assertEquals(0, m.getGlobalPausesRemaining(isStrictLockdown = true))

        assertTrue(m.requestGlobalPause(5, "Reply to mom"))
        assertTrue(m.isBreakActiveForPackage(ig))
        m.cancelBreak()
        assertFalse("one per day", m.requestGlobalPause(5, "Again"))
        // Emergency allowance is separate from the global pause.
        assertEquals(2, m.getBreaksRemaining(ig))
    }

    @Test
    fun codec_roundTrips_includingAwkwardText() {
        val s = BreakSnapshot(true, 1234L, ig, "check: event, post = now\nnewline", 20411L, mapOf(ig to 1, "*" to 1))
        assertEquals(s, BreakStateCodec.decode(BreakStateCodec.encode(s)))
        assertNull(BreakStateCodec.decode("garbage"))
        assertNull(BreakStateCodec.decode(null))
    }
}
