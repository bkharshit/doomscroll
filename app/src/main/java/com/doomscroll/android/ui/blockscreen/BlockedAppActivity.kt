package com.doomscroll.android.ui.blockscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.doomscroll.android.DoomscrollApp
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.ui.theme.DoomscrollTheme

class BlockedAppActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_REASON = "extra_reason"
        const val EXTRA_IS_SLEEP = "extra_is_sleep"
    }

    private val app: DoomscrollApp
        get() = applicationContext as DoomscrollApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app.breakManager.refreshDay()

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
                val isStrict by app.settingsRepository.isStrictLockdownEnabled.collectAsState(initial = false)
                // Re-read on every change to today's allowance; getBreaksRemaining also handles midnight rollover.
                val usedMap by app.breakManager.breaksUsedToday.collectAsState()
                val remaining = remember(usedMap, isStrict) {
                    app.breakManager.getBreaksRemaining(targetPkg, isStrict)
                }
                val usedToday = remember(usedMap) { app.breakManager.breaksUsedToday(targetPkg) }

                BlockedScreen(
                    appName = displayName,
                    reason = reason,
                    breaksRemaining = remaining,
                    breaksUsedToday = usedToday,
                    isStrictLockdown = isStrict,
                    onExitToHome = { exitToHome() },
                    onRequestBreak = { intention ->
                        val granted = app.breakManager.requestEmergencyBreak(
                            targetPackage = targetPkg,
                            durationMinutes = 5,
                            isStrictLockdown = isStrict,
                            intention = intention
                        )
                        if (granted) {
                            finish()
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
