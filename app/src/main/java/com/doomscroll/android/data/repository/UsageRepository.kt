package com.doomscroll.android.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.doomscroll.android.core.model.DayUsageData
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.core.model.UsageStat
import com.doomscroll.android.core.util.ForegroundCalculator
import com.doomscroll.android.core.util.TimeUtils
import com.doomscroll.android.core.util.UsageEvt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

interface UsageRepository {
    suspend fun getTodayUsageSeconds(packageName: String): Long
    suspend fun getUsageStatsForMonitoredApps(limits: Map<String, Int>, reelsBlockedMap: Map<String, Int>): List<UsageStat>
    suspend fun getWeeklyUsageHistory(): List<DayUsageData>
}

class UsageRepositoryImpl(
    private val context: Context
) : UsageRepository {

    private val usageStatsManager: UsageStatsManager? by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    }

    override suspend fun getTodayUsageSeconds(packageName: String): Long = withContext(Dispatchers.IO) {
        val usageMap = getTodayForegroundSecondsMap(setOf(packageName))
        usageMap[packageName] ?: 0L
    }

    override suspend fun getUsageStatsForMonitoredApps(
        limits: Map<String, Int>,
        reelsBlockedMap: Map<String, Int>
    ): List<UsageStat> = withContext(Dispatchers.IO) {
        val allPackages = MonitoredApp.entries.map { it.packageName }.toSet()
        val usageMap = getTodayForegroundSecondsMap(allPackages)

        MonitoredApp.entries.map { app ->
            val foregroundSeconds = usageMap[app.packageName] ?: 0L
            val dailyLimit = limits[app.packageName] ?: app.defaultDailyLimitMinutes
            val blockedCount = reelsBlockedMap[app.packageName] ?: 0

            UsageStat(
                monitoredApp = app,
                foregroundSecondsToday = foregroundSeconds,
                dailyLimitMinutes = dailyLimit,
                reelsBlockedCount = blockedCount
            )
        }
    }

    /**
     * Accurately calculates today's foreground seconds matching Android System Settings (Digital Wellbeing).
     * Uses the device's authoritative raw UsageEvents stream processed through ForegroundCalculator.
     */
    private fun getTodayForegroundSecondsMap(targetPackages: Set<String>): Map<String, Long> {
        val usm = usageStatsManager ?: return emptyMap()
        val startTime = TimeUtils.getStartOfTodayMillis()
        val endTime = System.currentTimeMillis()
        val lookbackMillis = 3 * 3600 * 1000L // 3 hours lookback to catch sessions open at midnight

        return try {
            val usageEvents = usm.queryEvents(startTime - lookbackMillis, endTime)
            val eventsList = mutableListOf<UsageEvt>()
            val event = UsageEvents.Event()

            while (usageEvents != null && usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                eventsList.add(
                    UsageEvt(
                        packageName = event.packageName ?: "",
                        className = event.className,
                        eventType = event.eventType,
                        timestampMillis = event.timeStamp
                    )
                )
            }

            if (eventsList.isNotEmpty()) {
                ForegroundCalculator.computeSeconds(
                    events = eventsList,
                    fromMillis = startTime,
                    nowMillis = endTime,
                    packages = targetPackages
                )
            } else {
                // Fallback to queryUsageStats if no events returned
                fallbackQueryUsageStats(usm, startTime, endTime, targetPackages)
            }
        } catch (e: Exception) {
            Log.e("UsageRepository", "Failed to query usage events, falling back", e)
            fallbackQueryUsageStats(usm, startTime, endTime, targetPackages)
        }
    }

    private fun fallbackQueryUsageStats(
        usm: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        targetPackages: Set<String>
    ): Map<String, Long> {
        return try {
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            if (stats.isNullOrEmpty()) {
                emptyMap()
            } else {
                targetPackages.associateWith { pkg ->
                    val matching = stats.filter { it.packageName == pkg }
                    val best = matching.filter { it.lastTimeUsed >= startTime }.maxByOrNull { it.lastTimeStamp }
                        ?: matching.maxByOrNull { it.lastTimeStamp }
                    (best?.totalTimeInForeground ?: 0L) / 1000L
                }
            }
        } catch (e: Exception) {
            Log.e("UsageRepository", "Fallback queryUsageStats failed", e)
            emptyMap()
        }
    }

    /**
     * Queries real daily usage stats from Android's database for each of the past 7 days.
     * Uses java.time for accurate timezone and DST handling.
     */
    override suspend fun getWeeklyUsageHistory(): List<DayUsageData> = withContext(Dispatchers.IO) {
        val usm = usageStatsManager ?: return@withContext emptyList()
        val allMonitoredPackages = MonitoredApp.entries.map { it.packageName }.toSet()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        data class RawDay(
            val shortLabel: String,
            val fullName: String,
            val totalSeconds: Long,
            val isToday: Boolean
        )

        val rawDays = mutableListOf<RawDay>()

        // Precompute today's accurate seconds from queryEvents
        val todayMap = getTodayForegroundSecondsMap(allMonitoredPackages)
        val todayTotalSeconds = todayMap.values.sum()

        // Query each day from 6 days ago up to today
        for (daysAgo in 6 downTo 0) {
            val date = today.minusDays(daysAgo.toLong())
            val short = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
            val fullDayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val full = if (daysAgo == 0) "$fullDayName (Today)" else fullDayName

            val daySeconds = if (daysAgo == 0) {
                todayTotalSeconds
            } else {
                val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                var pastSeconds = 0L
                try {
                    val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
                    if (!stats.isNullOrEmpty()) {
                        for (pkg in allMonitoredPackages) {
                            val matching = stats.filter { it.packageName == pkg }
                            val best = matching.filter { it.lastTimeUsed in dayStart..dayEnd }.maxByOrNull { it.lastTimeStamp }
                                ?: matching.filter { it.lastTimeUsed >= dayStart }.maxByOrNull { it.lastTimeStamp }
                                ?: matching.maxByOrNull { it.lastTimeStamp }
                            pastSeconds += (best?.totalTimeInForeground ?: 0L) / 1000L
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UsageRepository", "Error querying stats for day -$daysAgo", e)
                }
                pastSeconds
            }

            rawDays.add(RawDay(short, full, daySeconds, daysAgo == 0))
        }

        // Calculate average and peak for proportional pillar height rendering
        val nonZeroDays = rawDays.filter { it.totalSeconds > 0 }
        val avgSeconds = if (nonZeroDays.isNotEmpty()) nonZeroDays.map { it.totalSeconds }.average().toLong() else 0L
        val maxSeconds = rawDays.maxOfOrNull { it.totalSeconds } ?: 1L
        val safeMax = maxSeconds.coerceAtLeast(3600L) // scale against at least 1 hour

        rawDays.map { raw ->
            val mins = (raw.totalSeconds / 60).toInt()
            val durText = TimeUtils.formatMinutes(mins)
            val ratio = (raw.totalSeconds.toFloat() / safeMax.toFloat()).coerceIn(0.18f, 1f)

            val (vsAvgText, isBetter) = if (avgSeconds > 0) {
                val diffPercent = (((raw.totalSeconds - avgSeconds).toDouble() / avgSeconds.toDouble()) * 100).toInt()
                if (diffPercent <= 0) {
                    Pair("−${Math.abs(diffPercent)}% vs avg", true)
                } else {
                    Pair("+${diffPercent}% vs avg", false)
                }
            } else {
                Pair("Avg baseline", true)
            }

            DayUsageData(
                shortLabel = raw.shortLabel,
                fullName = raw.fullName,
                durationText = durText,
                fillRatio = ratio,
                vsAvgText = vsAvgText,
                isBetterThanAvg = isBetter
            )
        }
    }
}
