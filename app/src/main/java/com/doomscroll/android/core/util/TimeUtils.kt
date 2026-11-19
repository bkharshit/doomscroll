package com.doomscroll.android.core.util

import java.util.Calendar

object TimeUtils {

    fun formatMinutes(minutes: Int): String {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return when {
            hours > 0 && remainingMinutes > 0 -> "${hours}h ${remainingMinutes}m"
            hours > 0 -> "${hours}h"
            else -> "${remainingMinutes}m"
        }
    }

    fun formatDurationSeconds(seconds: Long): String {
        val mins = (seconds / 60).toInt()
        val secs = (seconds % 60).toInt()
        return String.format("%02d:%02d", mins, secs)
    }

    fun getStartOfTodayMillis(): Long {
        return java.time.LocalDate.now()
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }
}
