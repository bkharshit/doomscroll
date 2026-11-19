package com.doomscroll.android

import com.doomscroll.android.data.db.DailyBlockCount
import com.doomscroll.android.data.db.DailyUsage
import com.doomscroll.android.data.db.MetricsDao
import com.doomscroll.android.data.local.LegacyStats
import com.doomscroll.android.data.local.LegacyStatsSource
import com.doomscroll.android.data.repository.MetricsRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class MetricsRepositoryTest {

    private class FakeDao : MetricsDao {
        val rows = MutableStateFlow<Map<Pair<Long, String>, Int>>(emptyMap())
        override suspend fun ensureBlockRow(day: Long, ruleId: String) {
            if ((day to ruleId) !in rows.value) rows.value = rows.value + ((day to ruleId) to 0)
        }
        override suspend fun addToBlockRow(day: Long, ruleId: String, by: Int) {
            rows.value[day to ruleId]?.let { rows.value = rows.value + ((day to ruleId) to it + by) }
        }
        override fun blocksForDay(day: Long): Flow<List<DailyBlockCount>> =
            rows.map { m -> m.filterKeys { it.first == day }.map { DailyBlockCount(it.key.first, it.key.second, it.value) } }
        override fun totalBlocks(): Flow<Int> = rows.map { it.values.sum() }
        override fun blocksSince(fromDay: Long): Flow<List<DailyBlockCount>> = rows.map { emptyList() }
        override suspend fun saveUsage(rows: List<DailyUsage>) = Unit
        override suspend fun usageForDay(day: Long): List<DailyUsage> = emptyList()
    }

    private class FakeLegacy(var stats: LegacyStats?, private val offsetAfterImport: Int) : LegacyStatsSource {
        private val offset = MutableStateFlow(0)
        override val legacyAllTimeOffset: Flow<Int> = offset
        override suspend fun takeLegacyStatsForImport(): LegacyStats? {
            val s = stats ?: return null
            stats = null
            offset.value = offsetAfterImport
            return s
        }
    }

    private val day = LocalDate.of(2026, 11, 19).toEpochDay()

    @Test
    fun countsArePerRuleAndDay_andAllTimeIncludesLegacy() = runTest {
        val clock = MutableClock(Instant.parse("2026-11-19T10:00:00Z"))
        val legacy = FakeLegacy(LegacyStats(day.toInt(), mapOf("instagram.reels" to 4)), offsetAfterImport = 10)
        val repo = MetricsRepositoryImpl(FakeDao(), legacy, clock)

        repo.ensureDailyReset()
        repo.recordReelBlocked("youtube.shorts")
        repo.recordReelBlocked("instagram.reels")

        assertEquals(mapOf("instagram.reels" to 5, "youtube.shorts" to 1), repo.blockedTodayByRule.first())
        assertEquals(6, repo.reelsBlockedToday.first())
        assertEquals("10 legacy + 6 in Room", 16, repo.allTimeBlocked.first())
    }

    @Test
    fun staleLegacyCounts_areNotImportedAsToday() = runTest {
        val clock = MutableClock(Instant.parse("2026-11-19T10:00:00Z"))
        val legacy = FakeLegacy(LegacyStats((day - 1).toInt(), mapOf("instagram.reels" to 9)), offsetAfterImport = 9)
        val repo = MetricsRepositoryImpl(FakeDao(), legacy, clock)
        repo.ensureDailyReset()
        assertEquals(0, repo.reelsBlockedToday.first())
        assertEquals(9, repo.allTimeBlocked.first())
    }

    @Test
    fun todayRollsOverAtMidnight_withoutAnyWrite() = runTest {
        val clock = MutableClock(Instant.parse("2026-11-19T23:59:30Z"))
        val repo = MetricsRepositoryImpl(FakeDao(), FakeLegacy(null, 0), clock)
        repo.recordReelBlocked("instagram.reels")

        val seen = mutableListOf<Int>()
        val job = backgroundScope.launch { repo.reelsBlockedToday.toList(seen) }
        runCurrent()
        clock.advanceHours(1) // now past midnight
        advanceTimeBy(61_000); runCurrent()

        assertEquals(listOf(1, 0), seen)
        job.cancel()
    }
}
