package com.doomscroll.android.core.model

data class DayUsageData(
    val shortLabel: String,
    val fullName: String,
    val durationText: String,
    val fillRatio: Float,
    val vsAvgText: String,
    val isBetterThanAvg: Boolean
)
