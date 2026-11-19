package com.doomscroll.android.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TactileColorScheme = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = InfinityWhite,
    primaryContainer = CoralFixed,
    onPrimaryContainer = CoralDark,
    secondary = MintSecondary,
    onSecondary = InfinityWhite,
    secondaryContainer = MintFixed,
    onSecondaryContainer = MintSecondaryDark,
    tertiary = TextMutedSlate,
    background = TactileBg,
    onBackground = TextDark,
    surface = TactileSurface,
    onSurface = TextDark,
    surfaceVariant = TactileSurfaceVariant,
    onSurfaceVariant = TextSecondarySlate,
    error = CoralDark,
    onError = InfinityWhite
)

@Composable
fun DoomscrollTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = TactileColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = true
            controller.isAppearanceLightNavigationBars = true
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
