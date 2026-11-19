package com.doomscroll.android.data.repository

import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.MonitoredApp
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isMasterShieldEnabled: Flow<Boolean>
    val isReelsInterceptionEnabled: Flow<Boolean>
    val isShortsInterceptionEnabled: Flow<Boolean>
    val isStrictLockdownEnabled: Flow<Boolean>
    /** When a paused shield switches itself back on (epoch millis), or null while it's on. */
    val shieldPausedUntilMillis: Flow<Long?>
    val breakDurationMinutes: Flow<Int>
    
    fun getAppLimits(): Flow<List<AppLimit>>
    fun getLimitForPackage(packageName: String): Flow<Int>
    fun isAppBlockEnabled(packageName: String): Flow<Boolean>

    /** Turning it off starts a [com.doomscroll.android.domain.limiter.ShieldPolicy.PAUSE_MINUTES] pause. */
    suspend fun setMasterShieldEnabled(enabled: Boolean)
    suspend fun setReelsInterceptionEnabled(enabled: Boolean)
    suspend fun setShortsInterceptionEnabled(enabled: Boolean)
    suspend fun setStrictLockdownEnabled(enabled: Boolean)
    suspend fun setDailyLimit(packageName: String, minutes: Int)
    suspend fun setAppBlockEnabled(packageName: String, enabled: Boolean)
}
