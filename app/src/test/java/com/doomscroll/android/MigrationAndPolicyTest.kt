package com.doomscroll.android

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.doomscroll.android.core.model.LimitPolicy
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.data.local.PreferencesDataStore
import com.doomscroll.android.data.local.SeedMissingLimitsMigration
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationAndPolicyTest {

    @Test
    fun limitPolicy_clampsCorrectly() {
        assertEquals(5, LimitPolicy.clamp(1))
        assertEquals(5, LimitPolicy.clamp(5))
        assertEquals(45, LimitPolicy.clamp(45))
        assertEquals(300, LimitPolicy.clamp(300))
        assertEquals(300, LimitPolicy.clamp(600))
    }

    @Test
    fun migration_seedsDefaultsOnEmptyPreferences() = runTest {
        val initial = emptyPreferences()
        assertTrue(SeedMissingLimitsMigration.shouldMigrate(initial))

        val migrated = SeedMissingLimitsMigration.migrate(initial)

        assertEquals(1, migrated[PreferencesDataStore.KEY_SCHEMA_VERSION])
        for (app in MonitoredApp.entries) {
            val limit = migrated[PreferencesDataStore.limitKey(app.packageName)]
            assertEquals(app.defaultDailyLimitMinutes, limit)
        }
    }

    @Test
    fun migration_neverOverwritesExistingLimitsBelow60() = runTest {
        // User had set Instagram to 15m and TikTok to 20m before migration
        val initial = mutablePreferencesOf(
            PreferencesDataStore.limitKey(MonitoredApp.INSTAGRAM.packageName) to 15,
            PreferencesDataStore.limitKey(MonitoredApp.TIKTOK.packageName) to 20
        ).toPreferences()

        assertTrue(SeedMissingLimitsMigration.shouldMigrate(initial))
        val migrated = SeedMissingLimitsMigration.migrate(initial)

        // Existing values must NOT be raised to 60!
        assertEquals(15, migrated[PreferencesDataStore.limitKey(MonitoredApp.INSTAGRAM.packageName)])
        assertEquals(20, migrated[PreferencesDataStore.limitKey(MonitoredApp.TIKTOK.packageName)])
        // Missing values are seeded with their app defaults
        assertEquals(MonitoredApp.YOUTUBE.defaultDailyLimitMinutes, migrated[PreferencesDataStore.limitKey(MonitoredApp.YOUTUBE.packageName)])
        assertEquals(1, migrated[PreferencesDataStore.KEY_SCHEMA_VERSION])
    }

    @Test
    fun migration_isIdempotent() = runTest {
        val version1Prefs = mutablePreferencesOf(
            PreferencesDataStore.KEY_SCHEMA_VERSION to 1,
            PreferencesDataStore.limitKey(MonitoredApp.INSTAGRAM.packageName) to 25
        ).toPreferences()

        assertFalse(SeedMissingLimitsMigration.shouldMigrate(version1Prefs))
    }
}
