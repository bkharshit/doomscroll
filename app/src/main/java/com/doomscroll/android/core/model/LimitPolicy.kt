package com.doomscroll.android.core.model

object LimitPolicy {
    const val MIN = 5
    const val MAX = 300
    const val STEP = 5

    fun clamp(minutes: Int): Int = minutes.coerceIn(MIN, MAX)
}
