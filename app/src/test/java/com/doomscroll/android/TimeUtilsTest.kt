package com.doomscroll.android

import com.doomscroll.android.core.util.TimeUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun testFormatMinutes() {
        assertEquals("30m", TimeUtils.formatMinutes(30))
        assertEquals("1h", TimeUtils.formatMinutes(60))
        assertEquals("1h 15m", TimeUtils.formatMinutes(75))
        assertEquals("2h 30m", TimeUtils.formatMinutes(150))
    }

    @Test
    fun testFormatDurationSeconds() {
        assertEquals("00:45", TimeUtils.formatDurationSeconds(45))
        assertEquals("02:05", TimeUtils.formatDurationSeconds(125))
        assertEquals("60:00", TimeUtils.formatDurationSeconds(3600))
    }
}
