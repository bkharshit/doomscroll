package com.doomscroll.android.data.local

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doomscroll.android.core.model.LimitPolicy
import com.doomscroll.android.core.model.MonitoredApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "doomscroll_prefs",
    produceMigrations = { listOf(SeedMissingLimitsMigration, MoveBreakStateOutMigration) }
)

object SeedMissingLimitsMigration : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        return (currentData[PreferencesDataStore.KEY_SCHEMA_VERSION] ?: 0) < 1
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        return currentData.toMutablePreferences().apply {
            MonitoredApp.entries.forEach { app ->
                val key = PreferencesDataStore.limitKey(app.packageName)
                if (this[key] == null) {
                    this[key] = app.defaultDailyLimitMinutes
                }
            }
            this[PreferencesDataStore.KEY_SCHEMA_VERSION] = 1
        }.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}

/**
 * v1 -> v2: break state now lives in its own DataStore file ([breakDataStore]) so backups can exclude it.
 * Drop the old key from the main preferences; an in-flight break is not worth carrying over.
 */
object MoveBreakStateOutMigration : DataMigration<Preferences> {
    private val LEGACY_KEY_BREAK_STATE = stringPreferencesKey("break_state_json")

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        (currentData[PreferencesDataStore.KEY_SCHEMA_VERSION] ?: 0) < 2

    override suspend fun migrate(currentData: Preferences): Preferences =
        currentData.toMutablePreferences().apply {
            remove(LEGACY_KEY_BREAK_STATE)
            this[PreferencesDataStore.KEY_SCHEMA_VERSION] = 2
        }.toPreferences()

    override suspend fun cleanUp() = Unit
}

/** Block counts recorded in DataStore before metrics moved to Room. */
interface LegacyStatsSource {
    val legacyAllTimeOffset: Flow<Int>
    suspend fun takeLegacyStatsForImport(): LegacyStats?
}

data class LegacyStats(val epochDay: Int, val todayByRule: Map<String, Int>)

class PreferencesDataStore(private val context: Context) : LegacyStatsSource {

    companion object {
        val KEY_SCHEMA_VERSION = intPreferencesKey("schema_version")
        val KEY_MASTER_SHIELD_ENABLED = booleanPreferencesKey("master_shield_enabled")
        val KEY_REELS_INTERCEPTION = booleanPreferencesKey("reels_interception")
        val KEY_SHORTS_INTERCEPTION = booleanPreferencesKey("shorts_interception")
        val KEY_BREAK_DURATION_MINUTES = intPreferencesKey("break_duration_minutes")
        val KEY_STRICT_LOCKDOWN = booleanPreferencesKey("strict_lockdown")
        val KEY_SHIELD_PAUSED_UNTIL = longPreferencesKey("shield_paused_until")

        // Downtime
        val KEY_DOWNTIME_ENABLED = booleanPreferencesKey("downtime_enabled")
        val KEY_DOWNTIME_START_HOUR = intPreferencesKey("downtime_start_hour")
        val KEY_DOWNTIME_START_MIN = intPreferencesKey("downtime_start_min")
        val KEY_DOWNTIME_END_HOUR = intPreferencesKey("downtime_end_hour")
        val KEY_DOWNTIME_END_MIN = intPreferencesKey("downtime_end_min")

        // Stats & Rollover
        val KEY_STATS_EPOCH_DAY = intPreferencesKey("stats_epoch_day")
        val KEY_ALL_TIME_BLOCKED = intPreferencesKey("all_time_blocked")
        val KEY_STATS_IMPORTED = booleanPreferencesKey("stats_imported_to_room")
        val KEY_LEGACY_ALL_TIME_OFFSET = intPreferencesKey("legacy_all_time_offset")
        private const val BLOCKED_TODAY_PREFIX = "blocked_today_"

        private fun todayEpoch(): Int = java.time.LocalDate.now().toEpochDay().toInt()

        fun limitKey(packageName: String) = intPreferencesKey("limit_$packageName")
        fun appBlockEnabledKey(packageName: String) = booleanPreferencesKey("block_enabled_$packageName")
    }

    val isMasterShieldEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_MASTER_SHIELD_ENABLED] ?: true
    }

    val shieldPausedUntilMillis: Flow<Long?> = context.dataStore.data.map {
        if (it[KEY_MASTER_SHIELD_ENABLED] == false) it[KEY_SHIELD_PAUSED_UNTIL] else null
    }

    val isReelsInterceptionEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_REELS_INTERCEPTION] ?: true
    }

    val isShortsInterceptionEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SHORTS_INTERCEPTION] ?: true
    }

    val isStrictLockdownEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_STRICT_LOCKDOWN] ?: false
    }

    val breakDurationMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_BREAK_DURATION_MINUTES] ?: 5
    }

    /**
     * Blocks counted before metrics moved to Room, minus today's (which are imported into Room).
     * Zero until [takeLegacyStatsForImport] has run.
     */
    override val legacyAllTimeOffset: Flow<Int> = context.dataStore.data.map {
        if (it[KEY_STATS_IMPORTED] == true) it[KEY_LEGACY_ALL_TIME_OFFSET] ?: 0 else 0
    }

    fun getAppDailyLimitMinutes(packageName: String, defaultMinutes: Int): Flow<Int> {
        return context.dataStore.data.map {
            it[limitKey(packageName)] ?: defaultMinutes
        }
    }

    fun isAppBlockEnabled(packageName: String): Flow<Boolean> {
        return context.dataStore.data.map {
            it[appBlockEnabledKey(packageName)] ?: true
        }
    }

    suspend fun setMasterShieldEnabled(enabled: Boolean, pausedUntilMillis: Long?) {
        context.dataStore.edit {
            it[KEY_MASTER_SHIELD_ENABLED] = enabled
            if (pausedUntilMillis == null) it.remove(KEY_SHIELD_PAUSED_UNTIL) else it[KEY_SHIELD_PAUSED_UNTIL] = pausedUntilMillis
        }
    }

    suspend fun setReelsInterceptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REELS_INTERCEPTION] = enabled }
    }

    suspend fun setShortsInterceptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHORTS_INTERCEPTION] = enabled }
    }

    suspend fun setStrictLockdownEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STRICT_LOCKDOWN] = enabled }
    }

    suspend fun setAppDailyLimitMinutes(packageName: String, minutes: Int) {
        context.dataStore.edit { it[limitKey(packageName)] = LimitPolicy.clamp(minutes) }
    }

    suspend fun setAppBlockEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { it[appBlockEnabledKey(packageName)] = enabled }
    }

    /**
     * Returns the pre-Room block counts once (null on later calls) and freezes the all-time offset,
     * excluding today's counts because the caller imports those into Room. Atomic with the flag.
     */
    override suspend fun takeLegacyStatsForImport(): LegacyStats? {
        var result: LegacyStats? = null
        context.dataStore.edit { prefs ->
            if (prefs[KEY_STATS_IMPORTED] == true) return@edit
            val epoch = prefs[KEY_STATS_EPOCH_DAY] ?: -1
            val todayByRule = if (epoch == todayEpoch()) {
                prefs.asMap().entries
                    .filter { it.key.name.startsWith(BLOCKED_TODAY_PREFIX) }
                    .associate { it.key.name.removePrefix(BLOCKED_TODAY_PREFIX) to (it.value as? Int ?: 0) }
            } else emptyMap()
            val allTime = prefs[KEY_ALL_TIME_BLOCKED] ?: 0
            prefs[KEY_LEGACY_ALL_TIME_OFFSET] = (allTime - todayByRule.values.sum()).coerceAtLeast(0)
            prefs[KEY_STATS_IMPORTED] = true
            result = LegacyStats(epoch, todayByRule)
        }
        return result
    }
}
