package com.doomscroll.android.notification

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.doomscroll.android.DoomscrollApp
import com.doomscroll.android.R
import com.doomscroll.android.core.model.BreakSession
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Low-importance ongoing notification while a break runs. It counts down with a system chronometer
 * (no per-second updates) and tapping it ends the break early.
 */
class BreakNotifier(
    private val context: Context,
    private val breakManager: MindfulBreakManager
) {
    companion object {
        private const val NOTIFICATION_ID = 1001

        fun hasPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    fun start(scope: CoroutineScope): Job = scope.launch {
        breakManager.breakSession.collect { session ->
            if (session.isActive) show(session) else NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        }
    }

    /** Re-posts the running break's notification, e.g. right after notification permission is granted. */
    fun refresh() {
        breakManager.breakSession.value.takeIf { it.isActive }?.let { show(it) }
    }

    private fun show(session: BreakSession) {
        if (!hasPermission(context)) return
        val endIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, EndBreakReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val scopeLabel = session.allowedPackage?.let { MonitoredApp.fromPackage(it)?.displayName ?: it } ?: "all apps"
        val endsAt = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(session.endTimeMillis))
        val notification = NotificationCompat.Builder(context, DoomscrollApp.CHANNEL_BREAK_ID)
            .setSmallIcon(R.drawable.ic_infinity)
            .setContentTitle(context.getString(R.string.break_active_title))
            .setContentText(context.getString(R.string.break_active_content, scopeLabel, endsAt))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setWhen(session.endTimeMillis)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setTimeoutAfter((session.endTimeMillis - System.currentTimeMillis()).coerceAtLeast(1_000L))
            .setContentIntent(endIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}

/** Tapping the break notification ends the break; enforcement resumes straight away. */
@AndroidEntryPoint
class EndBreakReceiver : BroadcastReceiver() {
    @Inject lateinit var breakManager: MindfulBreakManager

    override fun onReceive(context: Context, intent: Intent?) {
        breakManager.cancelBreak()
    }
}
