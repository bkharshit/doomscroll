package com.doomscroll.android.data.repository

import com.doomscroll.android.data.local.PreferencesDataStore
import kotlinx.coroutines.flow.Flow

interface MetricsRepository {
    val reelsBlockedToday: Flow<Int>
    val allTimeBlocked: Flow<Int>
    /** Today's blocks keyed by interception rule id (e.g. "instagram.reels", "youtube.shorts"). */
    val blockedTodayByRule: Flow<Map<String, Int>>
    suspend fun recordReelBlocked(ruleId: String)
    suspend fun ensureDailyReset()
}

class MetricsRepositoryImpl(
    private val dataStore: PreferencesDataStore
) : MetricsRepository {

    override val reelsBlockedToday: Flow<Int> = dataStore.reelsBlockedToday
    override val allTimeBlocked: Flow<Int> = dataStore.allTimeBlocked
    override val blockedTodayByRule: Flow<Map<String, Int>> = dataStore.blockedTodayByRule

    override suspend fun recordReelBlocked(ruleId: String) {
        dataStore.incrementBlockedCount(ruleId)
    }

    override suspend fun ensureDailyReset() {
        dataStore.resetDailyStatsIfNewDay()
    }
}
