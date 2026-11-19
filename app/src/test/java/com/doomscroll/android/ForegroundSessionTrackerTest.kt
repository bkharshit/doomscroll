package com.doomscroll.android

import com.doomscroll.android.domain.limiter.MindfulBreakManager
import com.doomscroll.android.service.ForegroundSessionTracker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForegroundSessionTrackerTest {

    private val ig = "com.instagram.android"
    private val yt = "com.google.android.youtube"

    private class Rig(scope: TestScope) {
        val settings = FakeSettingsRepository()
        val usage = FakeUsageRepository { scope.testScheduler.currentTime }
        val enforcer = RecordingEnforcer().apply { nowMillis = { scope.testScheduler.currentTime } }
        val breaks = MindfulBreakManager(store = null, scope = scope.backgroundScope)
        val tracker = ForegroundSessionTracker(usage, settings, breaks, enforcer, scope.backgroundScope)

        fun enter(pkg: String, now: Long) {
            usage.foregroundSinceMillis = now
            tracker.onForegroundPackageChanged(pkg)
        }
    }

    @Test
    fun blocksMidSession_whenBudgetRunsOut() = runTest {
        val rig = Rig(this)
        rig.settings.limits.value = rig.settings.limits.value + (ig to 5)
        rig.usage.baseSeconds = 4 * 60 // 1 minute left

        rig.enter(ig, testScheduler.currentTime)
        runCurrent()
        advanceTimeBy(59_000); runCurrent()
        assertTrue("must not block early", rig.enforcer.blocks.isEmpty())

        advanceTimeBy(2_000); runCurrent()
        assertEquals(listOf(ig), rig.enforcer.blocks.map { it.first })
    }

    @Test
    fun blocksImmediately_whenAlreadyOverLimitOnEntry() = runTest {
        val rig = Rig(this)
        rig.settings.limits.value = rig.settings.limits.value + (ig to 5)
        rig.usage.baseSeconds = 6 * 60

        rig.enter(ig, testScheduler.currentTime)
        runCurrent()
        assertEquals(1, rig.enforcer.blocks.size)
    }

    @Test
    fun leavingTheApp_cancelsTheDeadline() = runTest {
        val rig = Rig(this)
        rig.settings.limits.value = rig.settings.limits.value + (ig to 5)
        rig.usage.baseSeconds = 4 * 60

        rig.enter(ig, testScheduler.currentTime)
        runCurrent()
        advanceTimeBy(30_000)
        rig.tracker.onForegroundPackageChanged("com.google.android.apps.nexuslauncher")
        rig.usage.foregroundSinceMillis = null
        advanceTimeBy(10 * 60_000); runCurrent()

        assertTrue(rig.enforcer.blocks.isEmpty())
        assertNull(rig.tracker.currentForegroundPkg)
    }

    @Test
    fun breakEnding_resumesEnforcementWithoutLeavingTheApp() = runTest {
        val rig = Rig(this)
        rig.settings.limits.value = rig.settings.limits.value + (ig to 5)
        rig.usage.baseSeconds = 6 * 60
        rig.breaks.requestEmergencyBreak(ig, durationMinutes = 5)

        rig.enter(ig, testScheduler.currentTime)
        runCurrent()
        assertTrue("no block during the break", rig.enforcer.blocks.isEmpty())

        // The service calls resyncCurrentSession() whenever the break state changes.
        rig.breaks.cancelBreak()
        rig.tracker.resyncCurrentSession()
        runCurrent()
        assertEquals(1, rig.enforcer.blocks.size)
    }

    @Test
    fun laggingUsage_isRecheckedUntilItReallyRunsOut() = runTest {
        val rig = Rig(this)
        rig.settings.limits.value = rig.settings.limits.value + (ig to 5)
        rig.usage.baseSeconds = 4 * 60

        rig.enter(ig, testScheduler.currentTime)
        runCurrent() // first read: 240 s used, deadline in 60 s
        rig.usage.lagSeconds = 20 // the OS then falls 20 s behind reality

        advanceTimeBy(61_000); runCurrent() // deadline reads 280 s: still under, so re-check later
        assertTrue(rig.enforcer.blocks.isEmpty())

        advanceTimeBy(20_000); runCurrent() // re-check 20 s later reads 300 s: block
        assertEquals(1, rig.enforcer.blocks.size)
    }

    @Test
    fun loweringTheLimit_reschedulesTheCurrentSession() = runTest {
        val rig = Rig(this)
        rig.settings.limits.value = rig.settings.limits.value + (ig to 60)
        rig.usage.baseSeconds = 4 * 60

        rig.enter(ig, testScheduler.currentTime)
        runCurrent()
        rig.settings.limits.value = rig.settings.limits.value + (ig to 5)
        rig.tracker.resyncCurrentSession()
        advanceTimeBy(61_000); runCurrent()

        assertEquals(1, rig.enforcer.blocks.size)
    }

    @Test
    fun shieldOrPerAppToggleOff_neverBlocks() = runTest {
        val rig = Rig(this)
        rig.settings.limits.value = rig.settings.limits.value + (ig to 5) + (yt to 5)
        rig.usage.baseSeconds = 10 * 60
        rig.settings.blockEnabled.value = rig.settings.blockEnabled.value + (ig to false)

        rig.enter(ig, testScheduler.currentTime)
        runCurrent()
        rig.settings.shield.value = false
        rig.enter(yt, testScheduler.currentTime)
        runCurrent()

        assertTrue(rig.enforcer.blocks.isEmpty())
    }
}
