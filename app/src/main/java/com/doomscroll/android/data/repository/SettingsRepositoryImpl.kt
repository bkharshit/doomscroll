package com.doomscroll.android.data.repository

import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.data.local.PreferencesDataStore
import com.doomscroll.android.domain.limiter.ShieldPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SettingsRepositoryImpl(
    private val dataStore: PreferencesDataStore
) : SettingsRepository {

    override val isMasterShieldEnabled: Flow<Boolean> = dataStore.isMasterShieldEnabled
    override val isReelsInterceptionEnabled: Flow<Boolean> = dataStore.isReelsInterceptionEnabled
    override val isShortsInterceptionEnabled: Flow<Boolean> = dataStore.isShortsInterceptionEnabled
    override val isStrictLockdownEnabled: Flow<Boolean> = dataStore.isStrictLockdownEnabled
    override val shieldPausedUntilMillis: Flow<Long?> = dataStore.shieldPausedUntilMillis
    override val breakDurationMinutes: Flow<Int> = dataStore.breakDurationMinutes

    override fun getAppLimits(): Flow<List<AppLimit>> {
        val monitored = MonitoredApp.entries
        // Combine flows for all monitored apps
        val limitFlows = monitored.map { app ->
            combine(
                dataStore.getAppDailyLimitMinutes(app.packageName, app.defaultDailyLimitMinutes),
                dataStore.isAppBlockEnabled(app.packageName)
            ) { limit, isEnabled ->
                AppLimit(
                    monitoredApp = app,
                    isAppBlockingEnabled = isEnabled,
                    dailyLimitMinutes = limit,
                    isShortsInterceptionEnabled = app.supportsShortsInterception
                )
            }
        }
        return combine(limitFlows) { it.toList() }
    }

    override fun getLimitForPackage(packageName: String): Flow<Int> {
        val app = MonitoredApp.fromPackage(packageName)
        val defaultMinutes = app?.defaultDailyLimitMinutes ?: 30
        return dataStore.getAppDailyLimitMinutes(packageName, defaultMinutes)
    }

    override fun isAppBlockEnabled(packageName: String): Flow<Boolean> {
        return dataStore.isAppBlockEnabled(packageName)
    }

    override suspend fun setMasterShieldEnabled(enabled: Boolean) {
        val pausedUntil = if (enabled) null else System.currentTimeMillis() + ShieldPolicy.PAUSE_MILLIS
        dataStore.setMasterShieldEnabled(enabled, pausedUntil)
    }

    override suspend fun setReelsInterceptionEnabled(enabled: Boolean) {
        dataStore.setReelsInterceptionEnabled(enabled)
    }

    override suspend fun setShortsInterceptionEnabled(enabled: Boolean) {
        dataStore.setShortsInterceptionEnabled(enabled)
    }

    override suspend fun setStrictLockdownEnabled(enabled: Boolean) {
        dataStore.setStrictLockdownEnabled(enabled)
    }

    override suspend fun setDailyLimit(packageName: String, minutes: Int) {
        dataStore.setAppDailyLimitMinutes(packageName, minutes)
    }

    override suspend fun setAppBlockEnabled(packageName: String, enabled: Boolean) {
        dataStore.setAppBlockEnabled(packageName, enabled)
    }
}
