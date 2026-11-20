package com.doomscroll.android.ui.limits

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.R
import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.LimitPolicy
import com.doomscroll.android.core.model.MonitoredApp
import com.doomscroll.android.core.model.UsageStat
import com.doomscroll.android.ui.components.InstalledAppIcon
import com.doomscroll.android.ui.components.NeumorphicToggle
import com.doomscroll.android.core.util.TimeUtils
import com.doomscroll.android.ui.components.TactileProgressSlider
import com.doomscroll.android.ui.components.neoExtruded
import com.doomscroll.android.ui.components.neoExtrudedCircle
import com.doomscroll.android.ui.components.neoRecessed
import com.doomscroll.android.ui.components.neoRecessedCircle
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
import com.doomscroll.android.core.model.DayUsageData
import com.doomscroll.android.ui.theme.TactileSurfaceLight
import com.doomscroll.android.ui.theme.TactileSurfaceVariant
import com.doomscroll.android.ui.theme.TextDark
import com.doomscroll.android.ui.theme.TextMutedSlate
import com.doomscroll.android.ui.theme.TextSecondarySlate

@Composable
fun LimitsScreen(
    viewModel: LimitsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    // Aggregate total usage across monitored apps
    val totalUsageMinutes = state.usageStats.values.sumOf { it.usageMinutes }
    val displayTotalUsage = if (totalUsageMinutes > 0) TimeUtils.formatMinutes(totalUsageMinutes) else "0m"

    // No history (still loading, or no usage access): empty placeholder bars, never invented numbers.
    val days = state.weeklyUsage.ifEmpty {
        val today = java.time.LocalDate.now()
        val locale = java.util.Locale.getDefault()
        (6 downTo 0).map { daysAgo ->
            val dow = today.minusDays(daysAgo.toLong()).dayOfWeek
            val full = dow.getDisplayName(java.time.format.TextStyle.FULL, locale)
            DayUsageData(
                shortLabel = dow.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                fullName = if (daysAgo == 0) "$full (Today)" else full,
                durationText = "—",
                fillRatio = 0.04f,
                vsAvgText = "No data",
                isBetterThanAvg = true
            )
        }
    }

    var selectedDayIndex by remember { mutableIntStateOf(6) }
    val clampedIndex = selectedDayIndex.coerceIn(0, (days.size - 1).coerceAtLeast(0))
    val selectedDay = days.getOrElse(clampedIndex) { days.last() }

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

        // Section Title
        item {
            Text(
                text = "Screen Time",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextDark,
                letterSpacing = (-0.5).sp
            )
        }

        // 7-Day Screen Time Tactile Chart Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neoExtruded(cornerRadius = 24.dp)
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = if (clampedIndex == days.lastIndex) "Total Daily Usage" else "Usage on ${selectedDay.fullName}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextMutedSlate
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    text = selectedDay.durationText,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    letterSpacing = (-0.5).sp
                                )
                                Box(
                                    modifier = Modifier
                                        .neoRecessed(cornerRadius = 10.dp)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = selectedDay.vsAvgText,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedDay.isBetterThanAvg) MintSecondary else CoralPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Tactile Pillar Bar Graph (Interactive Click for Each Day)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(144.dp)
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        days.forEachIndexed { index, day ->
                            val isSelected = clampedIndex == index

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        selectedDayIndex = index
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(16.dp)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    if (isSelected) {
                                        Text(
                                            text = day.durationText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = CoralPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                // Pillar Track
                                Box(
                                    modifier = Modifier
                                        .width(26.dp)
                                        .height((day.fillRatio * 85).dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) {
                                                Brush.verticalGradient(
                                                    listOf(CoralGradientEnd, CoralGradientStart)
                                                )
                                            } else {
                                                Brush.verticalGradient(
                                                    listOf(TactileSurfaceVariant, TactileGroove)
                                                )
                                            }
                                        )
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = day.shortLabel,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CoralPrimary else TextMutedSlate
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Header: Active App Quotas
        val displayedAppLimits = state.appLimits.filter { it.monitoredApp != MonitoredApp.TIKTOK }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Active App Quotas",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .neoRecessedCircle(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${if (displayedAppLimits.isNotEmpty()) displayedAppLimits.size else 3}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMutedSlate
                        )
                    }
                }

                Text(
                    text = "Resets at midnight",
                    fontSize = 11.sp,
                    color = TextMutedSlate
                )
            }
        }

        // App Quotas Cards
        if (displayedAppLimits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .neoRecessed(cornerRadius = 16.dp)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Loading configured limits...",
                        fontSize = 13.sp,
                        color = TextMutedSlate
                    )
                }
            }
        } else {
            items(displayedAppLimits) { appLimit ->
                val stat = state.usageStats[appLimit.monitoredApp.packageName]
                val used = stat?.usageMinutes ?: 0
                val limit = appLimit.dailyLimitMinutes

                AppQuotaCard(
                    appName = appLimit.monitoredApp.displayName,
                    packageName = appLimit.monitoredApp.packageName,
                    category = appLimit.monitoredApp.packageName,
                    usedMinutes = used,
                    limitMinutes = limit,
                    isBlocked = appLimit.isAppBlockingEnabled,
                    accentColor = if (used >= limit) CoralPrimary else MintSecondary,
                    onLimitChange = { newMinutes ->
                        viewModel.updateLimit(appLimit.monitoredApp.packageName, newMinutes)
                    },
                    onToggleBlock = { enabled ->
                        viewModel.toggleAppBlock(appLimit.monitoredApp.packageName, enabled)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(160.dp))
        }
    }
}

@Composable
fun AppQuotaCard(
    appName: String,
    packageName: String,
    category: String,
    usedMinutes: Int,
    limitMinutes: Int,
    isBlocked: Boolean,
    accentColor: Color,
    onLimitChange: (Int) -> Unit,
    onToggleBlock: (Boolean) -> Unit = {}
) {
    val progress = if (limitMinutes > 0) {
        (usedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val isOverLimit = usedMinutes >= limitMinutes && limitMinutes > 0
    val percentage = (progress * 100).toInt()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neoExtruded(cornerRadius = 22.dp)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // App Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // App Icon Disc
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .neoExtrudedCircle(elevation = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InstalledAppIcon(packageName = packageName, size = 30.dp) {
                            when {
                                appName.contains("Instagram", ignoreCase = true) -> {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFFF09433),
                                                        Color(0xFFDC2743),
                                                        Color(0xFFBC1888)
                                                    )
                                                ),
                                                CircleShape
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
                                            .size(30.dp)
                                            .background(Color(0xFFFF0000), CircleShape),
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
                                appName.contains("Reddit", ignoreCase = true) -> {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(Color(0xFFFF4500), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Forum,
                                            contentDescription = null,
                                            tint = InfinityWhite,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(ObsidianDark, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = appName.take(1).uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            color = InfinityWhite,
                                            fontSize = 14.sp
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
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )

                            // Status badge
                            val badgeColor = when {
                                isOverLimit -> CoralPrimary
                                percentage > 75 -> CoralPrimary
                                else -> MintSecondary
                            }
                            val badgeText = when {
                                isOverLimit -> "Limit Met"
                                percentage < 30 -> "Healthy"
                                else -> "$percentage%"
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isOverLimit) CoralFixed else MintFixed
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOverLimit) CoralPrimary else MintSecondaryDark
                                )
                            }
                        }

                        Text(
                            text = "Used ${TimeUtils.formatMinutes(usedMinutes)} of ${TimeUtils.formatMinutes(limitMinutes)}",
                            fontSize = 12.sp,
                            color = TextMutedSlate
                        )
                    }
                }

                // Toggle per-app block (Fixes R12)
                NeumorphicToggle(
                    checked = isBlocked,
                    onCheckedChange = { onToggleBlock(it) }
                )
            }

            // Slider Section (Configures Limit Ceiling from 5m up to 5 hours, fixes R15 & R22)
            val minLimit = LimitPolicy.MIN
            val maxLimit = LimitPolicy.MAX
            var draftMinutes by remember(limitMinutes) { mutableIntStateOf(limitMinutes) }
            val sliderRatio = ((draftMinutes - minLimit).toFloat() / (maxLimit - minLimit).toFloat()).coerceIn(0f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Ceiling",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextMutedSlate
                    )
                    Text(
                        text = TimeUtils.formatMinutes(draftMinutes),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }

                TactileProgressSlider(
                    value = sliderRatio,
                    onValueChange = { newRatio ->
                        val rawMins = minLimit + (newRatio * (maxLimit - minLimit)).toInt()
                        val snapped = ((rawMins + 2) / LimitPolicy.STEP * LimitPolicy.STEP).coerceIn(minLimit, maxLimit)
                        draftMinutes = snapped
                    },
                    onValueChangeFinished = {
                        onLimitChange(draftMinutes)
                    },
                    fillBrush = Brush.horizontalGradient(
                        if (isOverLimit) {
                            listOf(CoralPrimary, CoralGradientEnd)
                        } else {
                            listOf(MintSecondary, CoralPrimary)
                        }
                    ),
                    knobDotColor = accentColor
                )

                // Presets spanning up to 5 hours
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(LimitPolicy.MIN, 60, 120, 180, LimitPolicy.MAX).forEach { mins ->
                        val isPresetSelected = draftMinutes == mins
                        Text(
                            text = when (mins) {
                                LimitPolicy.MIN -> "${LimitPolicy.MIN}m"
                                60 -> "1h"
                                120 -> "2h"
                                180 -> "3h"
                                300 -> "5h"
                                else -> TimeUtils.formatMinutes(mins)
                            },
                            fontSize = 11.sp,
                            fontWeight = if (isPresetSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isPresetSelected) CoralPrimary else TextMutedSlate,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    draftMinutes = mins
                                    onLimitChange(mins)
                                }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
