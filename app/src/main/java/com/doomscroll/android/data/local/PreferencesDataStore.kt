package com.doomscroll.android.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.doomscroll.android.core.model.MonitoredApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "doomscroll_prefs")

class PreferencesDataStore(private val context: Context) {

    companion object {
        val KEY_MASTER_SHIELD_ENABLED = booleanPreferencesKey("master_shield_enabled")
        val KEY_REELS_INTERCEPTION = booleanPreferencesKey("reels_interception")
        val KEY_SHORTS_INTERCEPTION = booleanPreferencesKey("shorts_interception")
        val KEY_BREAK_DURATION_MINUTES = intPreferencesKey("break_duration_minutes")
        
        // Downtime
        val KEY_DOWNTIME_ENABLED = booleanPreferencesKey("downtime_enabled")
        val KEY_DOWNTIME_START_HOUR = intPreferencesKey("downtime_start_hour")
        val KEY_DOWNTIME_START_MIN = intPreferencesKey("downtime_start_min")
        val KEY_DOWNTIME_END_HOUR = intPreferencesKey("downtime_end_hour")
        val KEY_DOWNTIME_END_MIN = intPreferencesKey("downtime_end_min")

        // Stats
        val KEY_REELS_BLOCKED_TODAY = intPreferencesKey("reels_blocked_today")
        val KEY_LAST_STATS_DAY = intPreferencesKey("last_stats_day")
        val KEY_ALL_TIME_BLOCKED = intPreferencesKey("all_time_blocked")

        fun limitKey(packageName: String) = intPreferencesKey("limit_$packageName")
        fun appBlockEnabledKey(packageName: String) = booleanPreferencesKey("block_enabled_$packageName")
    }

    val isMasterShieldEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_MASTER_SHIELD_ENABLED] ?: true
    }

    val isReelsInterceptionEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_REELS_INTERCEPTION] ?: true
    }

    val isShortsInterceptionEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_SHORTS_INTERCEPTION] ?: true
    }

    val breakDurationMinutes: Flow<Int> = context.dataStore.data.map {
        it[KEY_BREAK_DURATION_MINUTES] ?: 5
    }

    val reelsBlockedToday: Flow<Int> = context.dataStore.data.map {
        it[KEY_REELS_BLOCKED_TODAY] ?: 0
    }

    val allTimeBlocked: Flow<Int> = context.dataStore.data.map {
        it[KEY_ALL_TIME_BLOCKED] ?: 0
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

    suspend fun setMasterShieldEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MASTER_SHIELD_ENABLED] = enabled }
    }

    suspend fun setReelsInterceptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REELS_INTERCEPTION] = enabled }
    }

    suspend fun setShortsInterceptionEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHORTS_INTERCEPTION] = enabled }
    }

    suspend fun setAppDailyLimitMinutes(packageName: String, minutes: Int) {
        context.dataStore.edit { it[limitKey(packageName)] = minutes }
    }

    suspend fun setAppBlockEnabled(packageName: String, enabled: Boolean) {
        context.dataStore.edit { it[appBlockEnabledKey(packageName)] = enabled }
    }

    suspend fun incrementReelsBlocked() {
        context.dataStore.edit { prefs ->
            val currentToday = prefs[KEY_REELS_BLOCKED_TODAY] ?: 0
            val allTime = prefs[KEY_ALL_TIME_BLOCKED] ?: 0
            prefs[KEY_REELS_BLOCKED_TODAY] = currentToday + 1
            prefs[KEY_ALL_TIME_BLOCKED] = allTime + 1
        }
    }

    suspend fun resetDailyStatsIfNewDay(currentDayOfYear: Int) {
        context.dataStore.edit { prefs ->
            val lastDay = prefs[KEY_LAST_STATS_DAY] ?: -1
            if (lastDay != currentDayOfYear) {
                prefs[KEY_REELS_BLOCKED_TODAY] = 0
                prefs[KEY_LAST_STATS_DAY] = currentDayOfYear
            }
        }
    }

    suspend fun initializeDefaultLimits() {
        context.dataStore.edit { prefs ->
            for (app in MonitoredApp.entries) {
                val key = limitKey(app.packageName)
                if (prefs[key] == null || (prefs[key] ?: 0) < 60) {
                    prefs[key] = 60
                }
            }
        }
    }
}
