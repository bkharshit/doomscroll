package com.doomscroll.android.ui.blockscreen

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.doomscroll.android.notification.BreakNotifier
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.doomscroll.android.data.repository.SettingsRepository
import com.doomscroll.android.domain.limiter.MindfulBreakManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.ui.theme.DoomscrollTheme

@AndroidEntryPoint
class BlockedAppActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var breakManager: MindfulBreakManager
    @Inject lateinit var breakNotifier: BreakNotifier

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_IS_SLEEP = "extra_is_sleep"
    }


    // Ask once for notification permission (API 33+) so the break countdown can show; leave either way.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) breakNotifier.refresh() // the break started before permission existed
            finish()
        }

    private fun finishAfterBreakStarted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !BreakNotifier.hasPermission(this)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        breakManager.refreshDay()

        val targetPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Social App"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Daily Screen Time Limit Reached"
        val monitored = MonitoredApp.fromPackage(targetPkg)
        val displayName = monitored?.displayName ?: targetPkg

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHome()
            }
        })

        setContent {
            DoomscrollTheme {
                val isStrict by settingsRepository.isStrictLockdownEnabled.collectAsState(initial = false)
                // Re-read on every change to today's allowance; getBreaksRemaining also handles midnight rollover.
                val usedMap by breakManager.breaksUsedToday.collectAsState()
                val remaining = remember(usedMap, isStrict) {
                    breakManager.getBreaksRemaining(targetPkg, isStrict)
                }
                val usedToday = remember(usedMap) { breakManager.breaksUsedToday(targetPkg) }

                BlockedScreen(
                    appName = displayName,
                    reason = reason,
                    breaksRemaining = remaining,
                    breaksUsedToday = usedToday,
                    isStrictLockdown = isStrict,
                    onExitToHome = { exitToHome() },
                    onRequestBreak = { intention ->
                        val granted = breakManager.requestEmergencyBreak(
                            targetPackage = targetPkg,
                            durationMinutes = 5,
                            isStrictLockdown = isStrict,
                            intention = intention
                        )
                        if (granted) {
                            finishAfterBreakStarted()
                        }
                    }
                )
            }
        }
    }

    private fun exitToHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
