package com.doomscroll.android.data.repository

import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.MonitoredApp
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val isMasterShieldEnabled: Flow<Boolean>
    val isReelsInterceptionEnabled: Flow<Boolean>
    val isShortsInterceptionEnabled: Flow<Boolean>
    val breakDurationMinutes: Flow<Int>
    
    fun getAppLimits(): Flow<List<AppLimit>>
    fun getLimitForPackage(packageName: String): Flow<Int>
    fun isAppBlockEnabled(packageName: String): Flow<Boolean>

    suspend fun setMasterShieldEnabled(enabled: Boolean)
    suspend fun setReelsInterceptionEnabled(enabled: Boolean)
    suspend fun setShortsInterceptionEnabled(enabled: Boolean)
    suspend fun setDailyLimit(packageName: String, minutes: Int)
    suspend fun setAppBlockEnabled(packageName: String, enabled: Boolean)
}
