package com.doomscroll.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.DoomscrollApp
import com.doomscroll.android.ui.components.neoExtruded
import com.doomscroll.android.ui.dashboard.DashboardIntent
import com.doomscroll.android.ui.dashboard.DashboardScreen
import com.doomscroll.android.ui.dashboard.DashboardViewModel
import com.doomscroll.android.ui.limits.LimitsScreen
import com.doomscroll.android.ui.limits.LimitsViewModel
import com.doomscroll.android.ui.permissions.PermissionsAssistantScreen
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.DoomscrollTheme
import com.doomscroll.android.ui.theme.TactileBg
import com.doomscroll.android.ui.theme.TactileSurfaceLight
import com.doomscroll.android.ui.theme.TextTertiarySlate

sealed class Screen(val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("Dashboard", Icons.Default.Dashboard)
    data object Limits : Screen("Limits", Icons.Default.HourglassTop)
    data object Settings : Screen("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val app: DoomscrollApp
        get() = applicationContext as DoomscrollApp

    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModel.Factory(
            context = applicationContext,
            settingsRepository = app.settingsRepository,
            usageRepository = app.usageRepository,
            metricsRepository = app.metricsRepository,
            breakManager = app.breakManager,
            rulePackages = app.interceptionEngine.rulePackages
        )
    }

    private val limitsViewModel: LimitsViewModel by viewModels {
        LimitsViewModel.Factory(
            settingsRepository = app.settingsRepository,
            usageRepository = app.usageRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            DoomscrollTheme {
                MainAppScaffold(
                    dashboardViewModel = dashboardViewModel,
                    limitsViewModel = limitsViewModel
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        dashboardViewModel.handleIntent(DashboardIntent.RefreshPermissionsAndStats)
        limitsViewModel.loadUsage()
    }
}

@Composable
fun MainAppScaffold(
    dashboardViewModel: DashboardViewModel,
    limitsViewModel: LimitsViewModel
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(Screen.Dashboard, Screen.Limits, Screen.Settings)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TactileBg)
            .statusBarsPadding()
    ) {
        // Screen Content with fluid horizontal slide transition
        AnimatedContent(
            targetState = selectedTabIndex,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = tween(220)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() },
                                animationSpec = tween(180)
                            ) + fadeOut(animationSpec = tween(140))
                        )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.35f).toInt() },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(animationSpec = tween(220)))
                        .togetherWith(
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.35f).toInt() },
                                animationSpec = tween(180)
                            ) + fadeOut(animationSpec = tween(140))
                        )
                }
            },
            label = "tabScreenTransition"
        ) { targetIndex ->
            when (targetIndex) {
                0 -> DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToLimits = { selectedTabIndex = 1 }
                )
                1 -> LimitsScreen(
                    viewModel = limitsViewModel
                )
                2 -> PermissionsAssistantScreen()
            }
        }

        // Floating Neumorphic Bottom Navigation Bar with Bouncy Sliding Pill
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .neoExtruded(
                        cornerRadius = 26.dp,
                        elevation = 4.dp,
                        backgroundColor = Color(0xFFECF0F7)
                    )
                    .padding(7.dp)
            ) {
                val tabCount = tabs.size
                val itemWidth = maxWidth / tabCount

                // Animated sliding indicator offset with bouncy spring physics
                val indicatorOffset by animateDpAsState(
                    targetValue = itemWidth * selectedTabIndex,
                    animationSpec = spring(
                        dampingRatio = 0.58f, // Juicy bouncy overshoot!
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "tabIndicatorOffset"
                )

                // Dragging sliding extruded pill behind the active tab
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(itemWidth)
                        .height(56.dp)
                        .neoExtruded(
                            cornerRadius = 20.dp,
                            elevation = 3.dp,
                            backgroundColor = TactileSurfaceLight
                        )
                )

                // Interactive Tab items
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, screen ->
                        val isSelected = selectedTabIndex == index

                        val animatedTextColor by animateColorAsState(
                            targetValue = if (isSelected) CoralPrimary else TextTertiarySlate,
                            animationSpec = tween(220),
                            label = "tabTextColor"
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.06f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.55f),
                            label = "tabIconScale"
                        )

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { selectedTabIndex = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = animatedTextColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.height(1.5.dp))
                                Text(
                                    text = screen.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = animatedTextColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
