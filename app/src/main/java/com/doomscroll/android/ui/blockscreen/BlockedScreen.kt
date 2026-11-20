package com.doomscroll.android.ui.blockscreen

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.doomscroll.android.domain.limiter.BreakPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.R
import com.doomscroll.android.ui.components.InstalledAppIcon
import com.doomscroll.android.ui.components.neoExtruded
import com.doomscroll.android.ui.components.neoExtrudedCircle
import com.doomscroll.android.ui.components.neoRecessed
import com.doomscroll.android.ui.theme.CoralFixed
import com.doomscroll.android.ui.theme.CoralGradientEnd
import com.doomscroll.android.ui.theme.CoralGradientStart
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.InfinityWhite
import com.doomscroll.android.ui.theme.MintFixed
import com.doomscroll.android.ui.theme.MintSecondary
import com.doomscroll.android.ui.theme.MintSecondaryDark
import com.doomscroll.android.ui.theme.ObsidianDark
import com.doomscroll.android.ui.theme.TactileBg
import com.doomscroll.android.ui.theme.TactileGroove
import com.doomscroll.android.ui.theme.TactileRecessed
import com.doomscroll.android.ui.theme.TactileSurface
import com.doomscroll.android.ui.theme.TextDark
import com.doomscroll.android.ui.theme.TextMutedSlate
import com.doomscroll.android.ui.theme.TextSecondarySlate

@Composable
fun BlockedScreen(
    appName: String,
    packageName: String?,
    reason: String,
    breaksRemaining: Int = 2,
    breaksUsedToday: Int = 0,
    isStrictLockdown: Boolean = false,
    onExitToHome: () -> Unit,
    onRequestBreak: (intention: String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBreakDialog by remember { mutableStateOf(false) }
    if (showBreakDialog) {
        EmergencyBreakDialog(
            appName = appName,
            waitSeconds = BreakPolicy.waitSecondsFor(breaksUsedToday),
            requireIntention = BreakPolicy.requiresIntention(breaksUsedToday),
            onConfirm = { intention ->
                showBreakDialog = false
                onRequestBreak(intention)
            },
            onDismiss = { showBreakDialog = false }
        )
    }

    // Gentle breathing pulse animation for the mindful aura
    val infiniteTransition = rememberInfiniteTransition(label = "gentle_breath")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TactileBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Tactile Brand Badge with Recessed Infinity Mark & Breathing Aura
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(110.dp)
            ) {
                // Breathing glow ring
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .scale(breathScale)
                        .background(MintSecondary.copy(alpha = 0.15f), RoundedCornerShape(34.dp))
                )

                // Outer extruded pillow
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .neoExtruded(cornerRadius = 28.dp, elevation = 6.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Recessed tactile inner core
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_doomscroll_logo),
                            contentDescription = "Doomscroll Logo",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = "Take a breath.",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "You've broken out of the infinite scroll loop.",
                fontSize = 14.sp,
                color = TextMutedSlate,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Extruded Tactile Quota Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neoExtruded(cornerRadius = 28.dp, elevation = 6.dp)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // App Header within Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // App Icon Neo Badge
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .neoExtruded(cornerRadius = 14.dp, elevation = 3.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                InstalledAppIcon(packageName = packageName, size = 32.dp) {
                                    when {
                                        appName.contains("Instagram", ignoreCase = true) -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(
                                                                Color(0xFFF09433),
                                                                Color(0xFFDC2743),
                                                                Color(0xFFBC1888)
                                                            )
                                                        ),
                                                        RoundedCornerShape(10.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CameraAlt,
                                                    contentDescription = null,
                                                    tint = InfinityWhite,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        appName.contains("YouTube", ignoreCase = true) -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        Color(0xFFFF0000),
                                                        RoundedCornerShape(10.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    tint = InfinityWhite,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        else -> {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(
                                                        ObsidianDark,
                                                        RoundedCornerShape(10.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Security,
                                                    contentDescription = null,
                                                    tint = InfinityWhite,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = appName,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(CoralFixed)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LOCKED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralPrimary
                                        )
                                    }
                                }
                                Text(
                                    text = reason,
                                    fontSize = 12.sp,
                                    color = TextMutedSlate
                                )
                            }
                        }

                        // Limit indicator badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CoralFixed)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "100% Quota",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralPrimary
                            )
                        }
                    }

                    // Neomorphic Recessed Dual-toned Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .neoRecessed(cornerRadius = 7.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(MintSecondary, Color(0xFFFBBF24), CoralPrimary)
                                    )
                                )
                        )
                    }

                    // Tactile Metric Badges Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Intercepts stat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .neoRecessed(cornerRadius = 14.dp)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .neoExtrudedCircle(elevation = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MintSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "INTERCEPTS",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMutedSlate
                                    )
                                    Text(
                                        text = "Cut loop cleanly",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }
                            }
                        }

                        // Time Saved stat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .neoRecessed(cornerRadius = 14.dp)
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .neoExtrudedCircle(elevation = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = CoralPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "TIME SAVED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextMutedSlate
                                    )
                                    Text(
                                        text = "Reclaimed focus",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Primary Action Button: Return to Home Screen (Embossed vibrant Green button)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(MintSecondary, MintSecondaryDark)
                        )
                    )
                    .clickable { onExitToHome() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = InfinityWhite,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Return to Home Screen",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = InfinityWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary Mindful Action: Limited emergency break with friction
            val canBreak = breaksRemaining > 0 && !isStrictLockdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .neoExtruded(cornerRadius = 18.dp, elevation = if (canBreak) 3.dp else 1.dp)
                    .clickable(enabled = canBreak) { showBreakDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = if (canBreak) TextMutedSlate else Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = when {
                            isStrictLockdown -> "Strict Lockdown: No breaks allowed"
                            canBreak -> "I need 5m intentionally ($breaksRemaining left today)"
                            else -> "No emergency breaks left today"
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canBreak) TextDark else TextMutedSlate
                    )
                }
            }
        }
    }
}
