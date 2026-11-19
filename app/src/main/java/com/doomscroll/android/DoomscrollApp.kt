package com.doomscroll.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.doomscroll.android.data.db.DoomscrollDatabase
import com.doomscroll.android.data.local.BreakDataStore
import com.doomscroll.android.notification.BreakNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.doomscroll.android.data.local.PreferencesDataStore
import com.doomscroll.android.data.repository.MetricsRepository
import com.doomscroll.android.data.repository.MetricsRepositoryImpl
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.data.repository.SettingsRepositoryImpl
import com.doomscroll.android.data.repository.UsageRepository
import com.doomscroll.android.data.repository.UsageRepositoryImpl
import com.doomscroll.android.domain.interceptor.InterceptionEngine
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import com.doomscroll.android.domain.limiter.UsageLimitEnforcer

class DoomscrollApp : Application() {

    companion object {
        const val CHANNEL_BREAK_ID = "channel_mindful_breaks"
        lateinit var instance: DoomscrollApp
            private set
    }

    lateinit var preferencesDataStore: PreferencesDataStore
        private set
    lateinit var settingsRepository: SettingsRepository
        private set
    lateinit var usageRepository: UsageRepository
        private set
    lateinit var metricsRepository: MetricsRepository
        private set
    lateinit var interceptionEngine: InterceptionEngine
        private set
    lateinit var usageLimitEnforcer: UsageLimitEnforcer
        private set
    lateinit var breakManager: MindfulBreakManager
        private set

    /** Process-wide scope for app-level observers (lives as long as the process). */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferencesDataStore = PreferencesDataStore(this)
        settingsRepository = SettingsRepositoryImpl(preferencesDataStore)
        val database = DoomscrollDatabase.create(this)
        usageRepository = UsageRepositoryImpl(this, database.metricsDao())
        metricsRepository = MetricsRepositoryImpl(database.metricsDao(), preferencesDataStore)
        interceptionEngine = InterceptionEngine()
        usageLimitEnforcer = UsageLimitEnforcer(this)
        breakManager = MindfulBreakManager(store = BreakDataStore(this))

        createNotificationChannels()
        BreakNotifier(this, breakManager).start(appScope)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BREAK_ID,
                getString(R.string.break_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.break_notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
