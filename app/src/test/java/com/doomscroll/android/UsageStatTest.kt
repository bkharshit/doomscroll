package com.doomscroll.android

import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.core.model.UsageStat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatTest {

    @Test
    fun testUsageMinutesCalculation() {
        val stat = UsageStat(
            monitoredApp = MonitoredApp.INSTAGRAM,
            foregroundSecondsToday = 1800, // 30 mins
            dailyLimitMinutes = 30,
            reelsBlockedCount = 5
        )
        assertEquals(30, stat.usageMinutes)
        assertEquals(1.0f, stat.progressPercent, 0.01f)
        assertTrue(stat.isLimitExceeded)
    }

    @Test
    fun testUnderLimitCalculation() {
        val stat = UsageStat(
            monitoredApp = MonitoredApp.YOUTUBE,
            foregroundSecondsToday = 600, // 10 mins
            dailyLimitMinutes = 45,
            reelsBlockedCount = 2
        )
        assertEquals(10, stat.usageMinutes)
        assertEquals(10f / 45f, stat.progressPercent, 0.01f)
        assertFalse(stat.isLimitExceeded)
    }
}
