package com.doomscroll.android.data.repository

import com.doomscroll.android.data.db.MetricsDao
import com.doomscroll.android.data.local.LegacyStatsSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate

interface MetricsRepository {
    val reelsBlockedToday: Flow<Int>
    val allTimeBlocked: Flow<Int>
    /** Today's blocks keyed by interception rule id (e.g. "instagram.reels", "youtube.shorts"). */
    val blockedTodayByRule: Flow<Map<String, Int>>
    suspend fun recordReelBlocked(ruleId: String)
    /** One-time import of counts kept in DataStore before Room existed. Safe to call repeatedly. */
    suspend fun ensureDailyReset()
}

/**
 * Block counts live in Room, one row per (day, rule). The day is computed at increment time, so a
 * count can't land on the wrong day, and "today" re-queries when the date changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MetricsRepositoryImpl(
    private val dao: MetricsDao,
    private val legacy: LegacyStatsSource,
    private val clock: Clock = Clock.systemDefaultZone()
) : MetricsRepository {

    private val importLock = Mutex()

    private fun today(): Long = LocalDate.now(clock).toEpochDay()

    /** Emits today's epoch day now and again whenever the local date changes (checked each minute). */
    private val todayFlow: Flow<Long> = flow {
        while (true) {
            emit(today())
            delay(60_000L)
        }
    }.distinctUntilChanged()

    override val blockedTodayByRule: Flow<Map<String, Int>> =
        todayFlow.flatMapLatest { day -> dao.blocksForDay(day) }
            .map { rows -> rows.associate { it.ruleId to it.count } }

    override val reelsBlockedToday: Flow<Int> = blockedTodayByRule.map { it.values.sum() }

    /** Room total plus whatever was counted before the move to Room. */
    override val allTimeBlocked: Flow<Int> =
        combine(dao.totalBlocks(), legacy.legacyAllTimeOffset) { room, offset -> room + offset }

    override suspend fun recordReelBlocked(ruleId: String) {
        ensureDailyReset()
        dao.incrementBlock(today(), ruleId)
    }

    override suspend fun ensureDailyReset() = importLock.withLock {
        val snapshot = legacy.takeLegacyStatsForImport() ?: return@withLock
        if (snapshot.epochDay.toLong() == today()) {
            snapshot.todayByRule.forEach { (ruleId, count) -> dao.incrementBlock(today(), ruleId, count) }
        }
    }
}
