package com.doomscroll.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.doomscroll.android.di.ApplicationScope
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import com.doomscroll.android.notification.BreakNotifier
import kotlinx.coroutines.CoroutineScope

@HiltAndroidApp
class DoomscrollApp : Application() {

    companion object {
        const val CHANNEL_BREAK_ID = "channel_mindful_breaks"
    }

    @Inject lateinit var breakNotifier: BreakNotifier
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope

    override fun onCreate() {
        super.onCreate() // Hilt injects here
        createNotificationChannels()
        breakNotifier.start(appScope)
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
