package com.doomscroll.android.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.R
import com.doomscroll.android.core.util.TimeUtils
import com.doomscroll.android.ui.components.NeumorphicToggle
import com.doomscroll.android.ui.components.neoExtruded
import com.doomscroll.android.ui.components.neoExtrudedCircle
import com.doomscroll.android.ui.components.neoRecessed
import com.doomscroll.android.ui.components.neoRecessedCircle
import com.doomscroll.android.ui.theme.CoralFixed
import com.doomscroll.android.ui.theme.CoralFixedDim
import com.doomscroll.android.ui.theme.CoralGradientEnd
import com.doomscroll.android.ui.theme.CoralGradientStart
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.InfinityWhite
import com.doomscroll.android.ui.theme.MintFixed
import com.doomscroll.android.ui.theme.MintSecondary
import com.doomscroll.android.ui.theme.ObsidianDark
import com.doomscroll.android.ui.theme.ObsidianMuted
import com.doomscroll.android.ui.theme.TactileBg
import com.doomscroll.android.ui.theme.TactileGroove
import com.doomscroll.android.ui.theme.TactileRecessed
import com.doomscroll.android.ui.theme.TactileSurface
import com.doomscroll.android.ui.theme.TactileSurfaceContainer
import com.doomscroll.android.ui.theme.TactileSurfaceLight
import com.doomscroll.android.ui.theme.TactileSurfaceVariant
import com.doomscroll.android.ui.theme.TextDark
import com.doomscroll.android.ui.theme.TextLightOnDark
import com.doomscroll.android.ui.theme.TextMutedSlate
import com.doomscroll.android.ui.theme.TextSecondarySlate
import com.doomscroll.android.ui.theme.TextTertiarySlate

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToLimits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showPauseShieldDialog by remember { mutableStateOf(false) }
    var showStrictInfoDialog by remember { mutableStateOf(false) }
    val canPause = !state.isStrictLockdown && state.globalPausesRemaining > 0

    LaunchedEffect(Unit) {
        viewModel.handleIntent(DashboardIntent.RefreshPermissionsAndStats)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TactileBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // App Header
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_doomscroll_logo),
                            contentDescription = "Doomscroll Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "doomscroll",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        letterSpacing = (-0.5).sp
                    )
                }
            }
        }

        // Master Shield Controller Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neoExtruded(cornerRadius = 24.dp)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "DOOMSCROLL GUARD",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        letterSpacing = 1.5.sp
                    )

                    // Sunken well container
                    val isShieldActive = state.isMasterShieldActive && !state.breakSession.isActive

                    // Animated color transition between Green (ON) and Red (OFF)
                    val animatedColor by animateColorAsState(
                        targetValue = if (isShieldActive) MintSecondary else CoralPrimary,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
                        label = "shieldColorAnim"
                    )

                    // Expanding circular shockwave pulse when toggling states
                    val pulseAnim = remember { Animatable(0f) }
                    LaunchedEffect(isShieldActive) {
                        pulseAnim.snapTo(0f)
                        pulseAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
                        )
                    }

                    // Tactile press bounce animation
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val buttonScale by animateFloatAsState(
                        targetValue = if (isPressed) 0.90f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "powerButtonPressScale"
                    )

                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .neoRecessedCircle()
                            .padding(7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Expanding circular shockwave ripple animation
                        if (pulseAnim.value > 0.01f && pulseAnim.value < 0.99f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val p = pulseAnim.value
                                        scaleX = 0.8f + 0.35f * p
                                        scaleY = 0.8f + 0.35f * p
                                        alpha = (1f - p) * 0.85f
                                    }
                                    .clip(CircleShape)
                                    .border(
                                        width = 2.dp,
                                        color = animatedColor,
                                        shape = CircleShape
                                    )
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                animatedColor.copy(alpha = 0.35f * (1f - pulseAnim.value)),
                                                Color.Transparent
                                            )
                                        ),
                                        shape = CircleShape
                                    )
                            )
                        }

                        // Extruded interactive Power Button with strictly circular clipping
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = buttonScale
                                    scaleY = buttonScale
                                }
                                .neoExtrudedCircle(
                                    elevation = if (isShieldActive) 4.dp else 2.dp,
                                    backgroundColor = TactileSurface,
                                    borderColor = animatedColor.copy(alpha = 0.25f),
                                    borderWidth = 1.dp
                                )
                                .clip(CircleShape) // Eliminates the square ripple/shadow artifact entirely
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = ripple(
                                        bounded = true,
                                        radius = 38.dp,
                                        color = animatedColor
                                    )
                                ) {
                                    when {
                                        !state.isMasterShieldActive ->
                                            viewModel.handleIntent(DashboardIntent.ToggleMasterShield(true))
                                        state.isStrictLockdown -> showStrictInfoDialog = true
                                        else -> showPauseShieldDialog = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Inner subtle radial aura
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                animatedColor.copy(alpha = 0.12f),
                                                Color.Transparent
                                            )
                                        )
                                    )
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "Master Shield Toggle",
                                    tint = animatedColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                AnimatedContent(
                                    targetState = if (isShieldActive) "ON" else "OFF",
                                    transitionSpec = {
                                        (fadeIn(animationSpec = tween(220, delayMillis = 50)) +
                                                scaleIn(initialScale = 0.8f, animationSpec = tween(220)))
                                            .togetherWith(fadeOut(animationSpec = tween(150)))
                                    },
                                    label = "shieldTextTransition"
                                ) { targetText ->
                                    Text(
                                        text = targetText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = animatedColor,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Visual Delight & Tactile Stats Cards Grid (2 Columns)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Card 1: Reclaimed
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .neoExtruded(cornerRadius = 22.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECLAIMED",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMutedSlate,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .neoRecessedCircle(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timelapse,
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val displaySaved = TimeUtils.formatMinutes(state.totalTimeSavedMinutes)

                        Text(
                            text = displaySaved,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Estimate: 3 min per intercept",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MintSecondary
                        )
                    }
                }

                // Card 2: Intercepts
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .neoExtruded(cornerRadius = 22.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "INTERCEPTS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMutedSlate,
                                letterSpacing = 1.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .neoRecessedCircle(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DoNotDisturbOn,
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val displayIntercepts = "${state.reelsBlockedToday}"

                        Text(
                            text = displayIntercepts,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "Doomscroll loops cut",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CoralPrimary
                        )
                    }
                }
            }
        }

        // Mindful Conscious Pause Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neoExtruded(cornerRadius = 24.dp)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .neoExtrudedCircle(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Spa,
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Conscious Pause",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = "Need to check messages intentionally?",
                                    fontSize = 12.sp,
                                    color = TextMutedSlate
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .neoRecessed(cornerRadius = 14.dp)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = TextMutedSlate,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (state.breakSession.isActive) {
                                        val mins = state.breakRemainingSeconds / 60
                                        val secs = state.breakRemainingSeconds % 60
                                        String.format("%02d:%02d", mins, secs)
                                    } else {
                                        when {
                                            state.isStrictLockdown -> "Strict"
                                            else -> "${state.globalPausesRemaining} left today"
                                        }
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextMutedSlate
                                )
                            }
                        }
                    }

                    if (state.breakSession.isActive) {
                        // Active Break Banner & Cancel Button
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .neoRecessed(cornerRadius = 16.dp)
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Pause Active",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MintSecondary
                                )
                                Text(
                                    text = "${state.breakRemainingSeconds / 60}m ${state.breakRemainingSeconds % 60}s remaining",
                                    fontSize = 12.sp,
                                    color = TextMutedSlate
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(TactileSurfaceVariant)
                                    .clickable { viewModel.handleIntent(DashboardIntent.CancelBreak) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        tint = TextDark,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Resume",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = TextDark
                                    )
                                }
                            }
                        }
                    } else {
                        // Recessed Stepper Track & Action Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when {
                                    state.isStrictLockdown -> "Strict Lockdown is on: no pauses."
                                    state.globalPausesRemaining > 0 -> "Pauses every app. ${state.globalPausesRemaining} left today."
                                    else -> "You've used today's pause. It resets at midnight."
                                },
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = TextMutedSlate
                            )

                            // Coral Start Button
                            Box(
                                modifier = Modifier
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(23.dp))
                                    .background(
                                        if (canPause) {
                                            Brush.horizontalGradient(listOf(CoralGradientStart, CoralGradientEnd))
                                        } else {
                                            Brush.horizontalGradient(listOf(TactileSurfaceVariant, TactileSurfaceVariant))
                                        }
                                    )
                                    .clickable(enabled = canPause) {
                                        viewModel.handleIntent(DashboardIntent.ShowBreakDialog)
                                    }
                                    .padding(horizontal = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "Pause",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = InfinityWhite
                                    )
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = InfinityWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Surgical Short-Form Blockers List
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Blocker 1: Instagram Reels
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoExtruded(cornerRadius = 20.dp)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .neoRecessed(cornerRadius = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MovieFilter,
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Instagram Reels",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = "DMs & photo feed remain open",
                                    fontSize = 12.sp,
                                    color = TextMutedSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        NeumorphicToggle(
                            checked = state.isReelsInterceptionActive,
                            onCheckedChange = {
                                viewModel.handleIntent(DashboardIntent.ToggleReelsInterception(it))
                            }
                        )
                    }
                }

                // Blocker 2: YouTube Shorts
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoExtruded(cornerRadius = 20.dp)
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .neoRecessed(cornerRadius = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "YouTube Shorts",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark
                                )
                                Text(
                                    text = "Standard player & subscriptions on",
                                    fontSize = 12.sp,
                                    color = TextMutedSlate,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        NeumorphicToggle(
                            checked = state.isShortsInterceptionActive,
                            onCheckedChange = {
                                viewModel.handleIntent(DashboardIntent.ToggleShortsInterception(it))
                            }
                        )
                    }
                }
            }
        }

        // Extra padding at the bottom for floating nav bar so nothing is hidden
        item {
            Spacer(modifier = Modifier.height(160.dp))
        }
    }

    if (state.isBreakDialogVisible) {
        com.doomscroll.android.ui.components.MindfulBreakDialog(
            onDismiss = {
                viewModel.handleIntent(DashboardIntent.DismissBreakDialog)
            },
            onStartBreak = { minutes, intention ->
                viewModel.handleIntent(DashboardIntent.StartBreak(minutes, intention))
                viewModel.handleIntent(DashboardIntent.DismissBreakDialog)
            }
        )
    }

    if (showPauseShieldDialog) {
        com.doomscroll.android.ui.components.FrictionConfirmDialog(
            title = "Turn protection off?",
            message = "Reels, Shorts and daily limits stop being enforced until you turn protection back on. " +
                "If you only need a few minutes, use a Conscious Pause instead.",
            waitSeconds = 10,
            confirmLabel = "Turn off",
            onConfirm = {
                showPauseShieldDialog = false
                viewModel.handleIntent(DashboardIntent.ToggleMasterShield(false))
            },
            onDismiss = { showPauseShieldDialog = false }
        )
    }

    if (showStrictInfoDialog) {
        com.doomscroll.android.ui.components.InfoDialog(
            title = "Strict Lockdown is on",
            message = "Protection can't be turned off while Strict Lockdown is on. Turn Strict Lockdown off in Settings first (it takes a 60-second wait).",
            onDismiss = { showStrictInfoDialog = false }
        )
    }
}
