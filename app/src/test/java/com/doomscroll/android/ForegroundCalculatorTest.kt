package com.doomscroll.android

import com.doomscroll.android.core.util.ForegroundCalculator
import com.doomscroll.android.core.util.UsageEvt
import org.junit.Assert.assertEquals
import org.junit.Test

class ForegroundCalculatorTest {

    private val targetPkg = "com.instagram.android"
    private val otherPkg = "com.google.android.youtube"
    private val fromMidnight = 100_000L
    private val now = 200_000L

    @Test
    fun plainResumeAndPause_calculatesExactSeconds() {
        // App opened at t=120_000, closed at t=150_000 (30 seconds)
        val events = listOf(
            UsageEvt(targetPkg, "MainActivity", UsageEvt.TYPE_ACTIVITY_RESUMED, 120_000L),
            UsageEvt(targetPkg, "MainActivity", UsageEvt.TYPE_ACTIVITY_PAUSED, 150_000L)
        )

        val result = ForegroundCalculator.computeSeconds(events, fromMidnight, now, setOf(targetPkg))
        assertEquals(30L, result[targetPkg])
    }

    @Test
    fun sessionCrossingMidnight_onlyCountsAfterMidnight() {
        // App opened 20s before midnight (t=80_000), closed 40s after midnight (t=140_000)
        val events = listOf(
            UsageEvt(targetPkg, "MainActivity", UsageEvt.TYPE_ACTIVITY_RESUMED, 80_000L),
            UsageEvt(targetPkg, "MainActivity", UsageEvt.TYPE_ACTIVITY_PAUSED, 140_000L)
        )

        val result = ForegroundCalculator.computeSeconds(events, fromMidnight, now, setOf(targetPkg))
        // Should only count from midnight (100_000) to 140_000 = 40 seconds
        assertEquals(40L, result[targetPkg])
    }

    @Test
    fun screenTurningOff_closesOpenSessionImmediately() {
        // App opened at t=110_000, screen off at t=135_000 (25s)
        val events = listOf(
            UsageEvt(targetPkg, "MainActivity", UsageEvt.TYPE_ACTIVITY_RESUMED, 110_000L),
            UsageEvt("android", null, UsageEvt.TYPE_SCREEN_NON_INTERACTIVE, 135_000L)
        )

        val result = ForegroundCalculator.computeSeconds(events, fromMidnight, now, setOf(targetPkg))
        assertEquals(25L, result[targetPkg])
    }

    @Test
    fun overlappingActivities_keepsSessionOpenUntilBothClosed() {
        // Activity A starts at 110_000
        // Activity B starts at 120_000 (A is still visible/starting transition)
        // Activity A pauses at 125_000
        // Activity B pauses at 150_000
        // Total session: 110_000 to 150_000 = 40 seconds
        val events = listOf(
            UsageEvt(targetPkg, "ActivityA", UsageEvt.TYPE_ACTIVITY_RESUMED, 110_000L),
            UsageEvt(targetPkg, "ActivityB", UsageEvt.TYPE_ACTIVITY_RESUMED, 120_000L),
            UsageEvt(targetPkg, "ActivityA", UsageEvt.TYPE_ACTIVITY_PAUSED, 125_000L),
            UsageEvt(targetPkg, "ActivityB", UsageEvt.TYPE_ACTIVITY_PAUSED, 150_000L)
        )

        val result = ForegroundCalculator.computeSeconds(events, fromMidnight, now, setOf(targetPkg))
        assertEquals(40L, result[targetPkg])
    }

    @Test
    fun sessionStillActiveAtNow_countsLivePortion() {
        // App opened at t=170_000, no pause event before now (t=200_000)
        val events = listOf(
            UsageEvt(targetPkg, "MainActivity", UsageEvt.TYPE_ACTIVITY_RESUMED, 170_000L)
        )

        val result = ForegroundCalculator.computeSeconds(events, fromMidnight, now, setOf(targetPkg))
        assertEquals(30L, result[targetPkg])
    }

    @Test
    fun keyguardShown_closesOpenSession() {
        // App opened at t=120_000, keyguard locked at t=145_000 (25s)
        val events = listOf(
            UsageEvt(targetPkg, "MainActivity", UsageEvt.TYPE_ACTIVITY_RESUMED, 120_000L),
            UsageEvt("com.android.systemui", null, UsageEvt.TYPE_KEYGUARD_SHOWN, 145_000L)
        )

        val result = ForegroundCalculator.computeSeconds(events, fromMidnight, now, setOf(targetPkg))
        assertEquals(25L, result[targetPkg])
    }

    @Test
    fun otherPackage_doesNotAffectTargetPackage() {
        val events = listOf(
            UsageEvt(otherPkg, "YouTubeActivity", UsageEvt.TYPE_ACTIVITY_RESUMED, 110_000L),
            UsageEvt(otherPkg, "YouTubeActivity", UsageEvt.TYPE_ACTIVITY_PAUSED, 140_000L)
        )

        val result = ForegroundCalculator.computeSeconds(events, fromMidnight, now, setOf(targetPkg, otherPkg))
        assertEquals(0L, result[targetPkg])
        assertEquals(30L, result[otherPkg])
    }
}
