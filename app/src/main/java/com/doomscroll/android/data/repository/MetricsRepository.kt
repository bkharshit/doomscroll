package com.doomscroll.android.data.repository

import com.doomscroll.android.data.local.PreferencesDataStore
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

interface MetricsRepository {
    val reelsBlockedToday: Flow<Int>
    val allTimeBlocked: Flow<Int>
    suspend fun recordReelBlocked()
    suspend fun ensureDailyReset()
}

class MetricsRepositoryImpl(
    private val dataStore: PreferencesDataStore
) : MetricsRepository {

    override val reelsBlockedToday: Flow<Int> = dataStore.reelsBlockedToday
    override val allTimeBlocked: Flow<Int> = dataStore.allTimeBlocked

    override suspend fun recordReelBlocked() {
        dataStore.incrementReelsBlocked()
    }

    override suspend fun ensureDailyReset() {
        val currentDayOfYear = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        dataStore.resetDailyStatsIfNewDay(currentDayOfYear)
    }
}
