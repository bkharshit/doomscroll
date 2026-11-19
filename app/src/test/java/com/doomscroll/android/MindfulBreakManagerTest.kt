package com.doomscroll.android

import com.doomscroll.android.domain.limiter.MindfulBreakManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MindfulBreakManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @Test
    fun emergencyBreaks_rationedToMaxTwoPerDay() = runTest(testDispatcher) {
        val manager = MindfulBreakManager(store = null, scope = backgroundScope)
        val pkg = "com.instagram.android"

        // Initially 2 breaks remaining
        assertEquals(2, manager.getBreaksRemaining(pkg))
        assertTrue(manager.canTakeBreak(pkg))

        // Take first break
        val break1 = manager.requestEmergencyBreak(pkg, durationMinutes = 5)
        assertTrue(break1)
        assertEquals(1, manager.getBreaksRemaining(pkg))

        // Take second break
        // Second break of the day needs a typed intention
        assertFalse(manager.requestEmergencyBreak(pkg, durationMinutes = 5))
        val break2 = manager.requestEmergencyBreak(pkg, durationMinutes = 5, intention = "Reply to a DM")
        assertTrue(break2)
        assertEquals(0, manager.getBreaksRemaining(pkg))
        assertFalse(manager.canTakeBreak(pkg))

        // Third break must be rejected! (Fixes R9)
        val break3 = manager.requestEmergencyBreak(pkg, durationMinutes = 5, intention = "One more")
        assertFalse(break3)
        assertEquals(0, manager.getBreaksRemaining(pkg))

        manager.cancelBreak()
    }

    @Test
    fun strictLockdown_allowsZeroEmergencyBreaks() = runTest(testDispatcher) {
        val manager = MindfulBreakManager(store = null, scope = backgroundScope)
        val pkg = "com.instagram.android"

        assertEquals(0, manager.getBreaksRemaining(pkg, isStrictLockdown = true))
        assertFalse(manager.canTakeBreak(pkg, isStrictLockdown = true))

        val breakRequest = manager.requestEmergencyBreak(pkg, isStrictLockdown = true)
        assertFalse(breakRequest)

        manager.cancelBreak()
    }

    @Test
    fun breakIsActive_scopedToAllowedPackage() = runTest(testDispatcher) {
        val manager = MindfulBreakManager(store = null, scope = backgroundScope)
        val instagram = "com.instagram.android"
        val youtube = "com.google.android.youtube"

        manager.startBreak(durationMinutes = 5, targetPackage = instagram)

        assertTrue(manager.isBreakActiveForPackage(instagram))
        // YouTube should NOT be paused when break is for Instagram! (Fixes R10)
        assertFalse(manager.isBreakActiveForPackage(youtube))

        manager.cancelBreak()
    }

    @Test
    fun globalBreak_coversAllPackages() = runTest(testDispatcher) {
        val manager = MindfulBreakManager(store = null, scope = backgroundScope)
        val instagram = "com.instagram.android"
        val youtube = "com.google.android.youtube"

        // Global pause (targetPackage = null)
        manager.startBreak(durationMinutes = 15, targetPackage = null)

        assertTrue(manager.isBreakActiveForPackage(instagram))
        assertTrue(manager.isBreakActiveForPackage(youtube))

        manager.cancelBreak()
    }
}
