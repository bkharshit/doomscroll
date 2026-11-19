package com.doomscroll.android

import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.DayUsageData
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.core.model.UsageStat
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.data.repository.UsageRepository
import com.doomscroll.android.domain.limiter.BreakStateStore
import com.doomscroll.android.domain.limiter.Enforcer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(var instant: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant
    fun advanceHours(h: Long) { instant = instant.plusSeconds(h * 3600) }
}

class FakeSettingsRepository : SettingsRepository {
    val shield = MutableStateFlow(true)
    val strict = MutableStateFlow(false)
    val pausedUntil = MutableStateFlow<Long?>(null)
    var nowMillis: () -> Long = { 0L }
    val limits = MutableStateFlow(MonitoredApp.entries.associate { it.packageName to it.defaultDailyLimitMinutes })
    val blockEnabled = MutableStateFlow(MonitoredApp.entries.associate { it.packageName to true })

    override val isMasterShieldEnabled: Flow<Boolean> = shield
    override val isReelsInterceptionEnabled: Flow<Boolean> = MutableStateFlow(true)
    override val isShortsInterceptionEnabled: Flow<Boolean> = MutableStateFlow(true)
    override val isStrictLockdownEnabled: Flow<Boolean> = strict
    override val shieldPausedUntilMillis: Flow<Long?> = pausedUntil
    override val breakDurationMinutes: Flow<Int> = MutableStateFlow(5)
    override fun getAppLimits(): Flow<List<AppLimit>> = limits.map { m ->
        MonitoredApp.entries.map { AppLimit(it, blockEnabled.value[it.packageName] ?: true, m[it.packageName] ?: 0) }
    }
    override fun getLimitForPackage(packageName: String): Flow<Int> = limits.map { it[packageName] ?: 0 }
    override fun isAppBlockEnabled(packageName: String): Flow<Boolean> = blockEnabled.map { it[packageName] ?: true }
    override suspend fun setMasterShieldEnabled(enabled: Boolean) {
        pausedUntil.value = if (enabled) null else nowMillis() + com.doomscroll.android.domain.limiter.ShieldPolicy.PAUSE_MILLIS
        shield.value = enabled
    }
    override suspend fun setReelsInterceptionEnabled(enabled: Boolean) = Unit
    override suspend fun setShortsInterceptionEnabled(enabled: Boolean) = Unit
    override suspend fun setStrictLockdownEnabled(enabled: Boolean) { strict.value = enabled }
    override suspend fun setDailyLimit(packageName: String, minutes: Int) { limits.value = limits.value + (packageName to minutes) }
    override suspend fun setAppBlockEnabled(packageName: String, enabled: Boolean) {
        blockEnabled.value = blockEnabled.value + (packageName to enabled)
    }
}

/** Usage that grows with (virtual) time while the app is "in the foreground". */
class FakeUsageRepository(private val nowMillis: () -> Long) : UsageRepository {
    var baseSeconds = 0L
    var foregroundSinceMillis: Long? = null
    /** Simulates OS usage lagging behind reality by this many seconds. */
    var lagSeconds = 0L

    override suspend fun getTodayUsageSeconds(packageName: String): Long {
        val live = foregroundSinceMillis?.let { (nowMillis() - it) / 1000 } ?: 0L
        return (baseSeconds + live - lagSeconds).coerceAtLeast(0)
    }
    override suspend fun getUsageStatsForMonitoredApps(limits: Map<String, Int>, reelsBlockedMap: Map<String, Int>): List<UsageStat> = emptyList()
    override suspend fun getWeeklyUsageHistory(): List<DayUsageData> = emptyList()
}

class RecordingEnforcer : Enforcer {
    val blocks = mutableListOf<Pair<String, Long>>()
    var nowMillis: () -> Long = { 0L }
    override fun enforceLimit(packageName: String, usedMinutes: Int, dailyLimitMinutes: Int, isSleepDowntime: Boolean): Boolean {
        val over = usedMinutes >= dailyLimitMinutes
        if (over) blocks += packageName to nowMillis()
        return over
    }
}

class InMemoryBreakStore(var value: String? = null) : BreakStateStore {
    override suspend fun load(): String? = value
    override suspend fun save(encoded: String) { value = encoded }
}
