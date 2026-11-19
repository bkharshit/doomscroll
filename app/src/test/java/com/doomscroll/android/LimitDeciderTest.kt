package com.doomscroll.android

import com.doomscroll.android.domain.limiter.LimitDecider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitDeciderTest {

    @Test
    fun calculateRemainingSeconds_returnsExactRemainingTime() {
        // Limit: 30 minutes (1800s). Used: 600s. Remaining: 1200s.
        val remaining = LimitDecider.calculateRemainingSeconds(
            usedSeconds = 600L,
            limitMinutes = 30,
            isBlockEnabled = true
        )
        assertEquals(1200L, remaining)
    }

    @Test
    fun calculateRemainingSeconds_returnsZeroWhenExceeded() {
        // Limit: 30 minutes (1800s). Used: 2000s. Remaining: 0s.
        val remaining = LimitDecider.calculateRemainingSeconds(
            usedSeconds = 2000L,
            limitMinutes = 30,
            isBlockEnabled = true
        )
        assertEquals(0L, remaining)
    }

    @Test
    fun calculateRemainingSeconds_returnsNullWhenDisabled() {
        val remainingWhenBlockDisabled = LimitDecider.calculateRemainingSeconds(
            usedSeconds = 600L,
            limitMinutes = 30,
            isBlockEnabled = false
        )
        assertNull(remainingWhenBlockDisabled)

        val remainingWhenLimitZero = LimitDecider.calculateRemainingSeconds(
            usedSeconds = 600L,
            limitMinutes = 0,
            isBlockEnabled = true
        )
        assertNull(remainingWhenLimitZero)
    }

    @Test
    fun isOverLimit_detectsWhenQuotaExceeded() {
        assertFalse(LimitDecider.isOverLimit(usedSeconds = 1799L, limitMinutes = 30, isBlockEnabled = true))
        assertTrue(LimitDecider.isOverLimit(usedSeconds = 1800L, limitMinutes = 30, isBlockEnabled = true))
        assertTrue(LimitDecider.isOverLimit(usedSeconds = 2500L, limitMinutes = 30, isBlockEnabled = true))
        // If disabled, never over limit
        assertFalse(LimitDecider.isOverLimit(usedSeconds = 2500L, limitMinutes = 30, isBlockEnabled = false))
    }
}
