package com.doomscroll.android.ui.permissions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.doomscroll.android.DoomscrollApp
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.R
import com.doomscroll.android.core.util.PermissionHelper
import com.doomscroll.android.ui.components.NeumorphicToggle
import com.doomscroll.android.ui.components.neoExtruded
import com.doomscroll.android.ui.components.neoExtrudedCircle
import com.doomscroll.android.ui.components.neoRecessed
import com.doomscroll.android.ui.theme.CoralFixed
import com.doomscroll.android.ui.theme.CoralFixedDim
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.InfinityWhite
import com.doomscroll.android.ui.theme.MintFixed
import com.doomscroll.android.ui.theme.MintSecondary
import com.doomscroll.android.ui.theme.MintSecondaryDark
import com.doomscroll.android.ui.theme.ObsidianDark
import com.doomscroll.android.ui.theme.ObsidianMuted
import com.doomscroll.android.ui.theme.TactileBg
import com.doomscroll.android.ui.theme.TactileGroove
import com.doomscroll.android.ui.theme.TactileSurfaceVariant
import com.doomscroll.android.ui.theme.TextDark
import com.doomscroll.android.ui.theme.TextLightOnDark
import com.doomscroll.android.ui.theme.TextMutedSlate
import com.doomscroll.android.ui.theme.TextSecondarySlate

@Composable
fun PermissionsAssistantScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasAccessibility by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var hasUsage by remember { mutableStateOf(PermissionHelper.isUsageAccessGranted(context)) }
    var hasBattery by remember { mutableStateOf(PermissionHelper.isBatteryOptimizationIgnored(context)) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var showStrictOffDialog by remember { mutableStateOf(false) }

    val isStrictLockdownEnabled by DoomscrollApp.instance.settingsRepository.isStrictLockdownEnabled.collectAsState(initial = false)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccessibility = PermissionHelper.isAccessibilityServiceEnabled(context)
                hasUsage = PermissionHelper.isUsageAccessGranted(context)
                hasBattery = PermissionHelper.isBatteryOptimizationIgnored(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TactileBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
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

        // Section Headline
        item {
            Text(
                text = "Shield Permissions",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                letterSpacing = (-0.5).sp
            )
        }

        // Privacy-First Shield Banner (Recessed Soft Well)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neoRecessed(cornerRadius = 20.dp)
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .neoExtrudedCircle(elevation = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = MintSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "100% On-Device Isolation",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = TextMutedSlate,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Zero telemetry. No keystrokes, screen recordings, or browsing metadata ever leave your secure hardware sandbox.",
                            fontSize = 12.sp,
                            color = TextMutedSlate,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // Permission Card 1: Accessibility
        item {
            TactilePermissionCard(
                stepNumber = "1",
                title = "Accessibility Service",
                description = "Detects Reels & Shorts navigation tabs in real-time to return to safe feeds.",
                tag = "INTERCEPTION HOOK",
                icon = Icons.Default.Security,
                isGranted = hasAccessibility,
                activeLabel = "Active",
                inactiveLabel = "Enable in Settings",
                onAction = {
                    if (!hasAccessibility) {
                        showAccessibilityDisclosure = true
                    } else {
                        context.startActivity(PermissionHelper.getAccessibilitySettingsIntent())
                    }
                }
            )
        }

        // Permission Card 2: Usage Access
        item {
            TactilePermissionCard(
                stepNumber = "2",
                title = "Usage Access",
                description = "Calculates precise daily app usage and enforces your configured quotas.",
                tag = "METRICS ENGINE",
                icon = Icons.Default.QueryStats,
                isGranted = hasUsage,
                activeLabel = "Granted",
                inactiveLabel = "Grant Access",
                onAction = {
                    context.startActivity(PermissionHelper.getUsageAccessSettingsIntent())
                }
            )
        }

        // Permission Card 3: Battery Saver Exemption
        // (No overlay card: the block screen is a normal activity started by the accessibility service,
        // and SYSTEM_ALERT_WINDOW is no longer declared, so it could never be granted.)
        item {
            TactilePermissionCard(
                stepNumber = "3",
                title = "Battery Saver Exemption",
                description = "Prevents OEM power managers from killing shield persistence in sleep state.",
                tag = "PERSISTENCE LOOP",
                icon = Icons.Default.BatteryChargingFull,
                isGranted = hasBattery,
                activeLabel = "Exempted",
                inactiveLabel = "Request Exemption",
                onAction = {
                    context.startActivity(PermissionHelper.getBatteryOptimizationSettingsIntent(context))
                }
            )
        }

        // Deep Obsidian Strict Lockdown Mode Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianDark)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ObsidianMuted),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockClock,
                                contentDescription = null,
                                tint = CoralFixed,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Strict Lockdown Mode",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = InfinityWhite
                            )
                            Text(
                                text = "Require 60s deliberate pause to disable",
                                fontSize = 11.sp,
                                color = TextLightOnDark.copy(alpha = 0.7f)
                            )
                        }
                    }

                    NeumorphicToggle(
                        checked = isStrictLockdownEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                scope.launch {
                                    DoomscrollApp.instance.settingsRepository.setStrictLockdownEnabled(true)
                                }
                            } else {
                                // Turning it off costs a 60 s wait, as the card promises.
                                showStrictOffDialog = true
                            }
                        }
                    )
                }
            }
        }

        // Section: Upcoming Features
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Upcoming Features",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextDark,
                    letterSpacing = (-0.3).sp
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CoralFixed)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Labs",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = CoralPrimary
                    )
                }
            }
        }

        // Circadian Defense Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(ObsidianDark)
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(ObsidianMuted),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = CoralFixedDim,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Circadian Defense",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InfinityWhite
                                )
                                Text(
                                    text = "Scheduled Bedtime Friction",
                                    fontSize = 11.sp,
                                    color = TextLightOnDark.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianMuted)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "10:30 PM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextLightOnDark
                            )
                        }
                    }

                    Text(
                        text = "Night-time friction level will turn screens to monochrome and enforce a 5-breath unlock delay.",
                        fontSize = 12.sp,
                        color = TextLightOnDark.copy(alpha = 0.8f),
                        lineHeight = 18.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(InfinityWhite.copy(alpha = 0.1f))
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "In Development",
                            fontSize = 11.sp,
                            color = TextLightOnDark.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Coming Soon",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MintFixed
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(160.dp))
        }
    }

    if (showStrictOffDialog) {
        com.doomscroll.android.ui.components.FrictionConfirmDialog(
            title = "Turn off Strict Lockdown?",
            message = "Strict Lockdown blocks emergency breaks, dashboard pauses and turning protection off. " +
                "Wait 60 seconds and decide if you still want it off.",
            waitSeconds = 60,
            confirmLabel = "Turn off",
            onConfirm = {
                showStrictOffDialog = false
                scope.launch {
                    DoomscrollApp.instance.settingsRepository.setStrictLockdownEnabled(false)
                }
            },
            onDismiss = { showStrictOffDialog = false }
        )
    }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = {
                Text(
                    text = "Accessibility Service Disclosure",
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            },
            text = {
                Text(
                    text = "Doomscroll uses Android's AccessibilityService API exclusively for self-control enforcement:\n\n" +
                            "• Real-time detection: Detects when short-form video feeds (Reels & Shorts) are open in Instagram and YouTube.\n" +
                            "• Surgical Exit: Automates a single tap on the Home or Back button to help you exit doomscrolling loops.\n\n" +
                            "Privacy Guarantee: No personal data, messages, keystrokes, audio, or account information is ever recorded, stored, or transmitted. All logic runs 100% locally on your device.",
                    color = TextMutedSlate,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAccessibilityDisclosure = false
                        context.startActivity(PermissionHelper.getAccessibilitySettingsIntent())
                    }
                ) {
                    Text("Accept & Continue", fontWeight = FontWeight.Bold, color = CoralPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) {
                    Text("Decline", color = TextMutedSlate)
                }
            }
        )
    }
}

@Composable
fun TactilePermissionCard(
    stepNumber: String,
    title: String,
    description: String,
    tag: String,
    icon: ImageVector,
    isGranted: Boolean,
    activeLabel: String,
    inactiveLabel: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neoExtruded(cornerRadius = 22.dp)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .neoRecessed(cornerRadius = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$stepNumber. $title",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = description,
                        fontSize = 12.sp,
                        color = TextMutedSlate,
                        lineHeight = 17.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(TactileGroove)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tag,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMutedSlate,
                    letterSpacing = 1.sp
                )

                if (isGranted) {
                    Box(
                        modifier = Modifier
                            .neoExtruded(cornerRadius = 16.dp, elevation = 2.dp)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .background(MintSecondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = InfinityWhite,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            Text(
                                text = activeLabel,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintSecondary
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(CoralPrimary)
                            .clickable { onAction() }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = inactiveLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = InfinityWhite
                        )
                    }
                }
            }
        }
    }
}
