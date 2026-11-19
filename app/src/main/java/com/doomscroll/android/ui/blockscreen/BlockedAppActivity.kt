package com.doomscroll.android.ui.blockscreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

        val targetPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Social App"
        val reason = intent.getStringExtra(EXTRA_REASON) ?: "Daily Screen Time Limit Reached"
        val monitored = MonitoredApp.fromPackage(targetPkg)
        val displayName = monitored?.displayName ?: targetPkg

        setContent {
            DoomscrollTheme {
                BlockedScreen(
                    appName = displayName,
                    reason = reason,
                    onExitToHome = { exitToHome() },
                    onRequestBreak = {
                        app.breakManager.startBreak(
                            durationMinutes = 5,
                            targetPackage = targetPkg,
                            intention = "Emergency Mindful Break"
                        )
                        finish()
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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevent users from bypassing by just tapping Android back button
        exitToHome()
    }
}
