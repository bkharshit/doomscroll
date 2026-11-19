package com.doomscroll.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.core.model.AppLimit
import com.doomscroll.android.core.model.UsageStat
import com.doomscroll.android.core.util.TimeUtils
import com.doomscroll.android.ui.theme.AccentGreen
import com.doomscroll.android.ui.theme.BgDark
import com.doomscroll.android.ui.theme.CardBorder
import com.doomscroll.android.ui.theme.SurfaceDark
import com.doomscroll.android.ui.theme.SurfaceElevated
import com.doomscroll.android.ui.theme.TextMuted
import com.doomscroll.android.ui.theme.TextPrimary
import com.doomscroll.android.ui.theme.TextSecondary
import com.doomscroll.android.ui.theme.WarningCoral

@Composable
fun AppLimitRow(
    limit: AppLimit,
    usageStat: UsageStat?,
    onLimitChange: (Int) -> Unit,
    onToggleEnable: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceDark, RoundedCornerShape(18.dp))
            .border(1.dp, CardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = limit.monitoredApp.displayName.take(1),
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = limit.monitoredApp.displayName,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        )
                        val usedSecs = usageStat?.foregroundSecondsToday ?: 0L
                        Text(
                            text = "Used today: ${TimeUtils.formatDurationSeconds(usedSecs)}",
                            color = if (usageStat?.isLimitExceeded == true) WarningCoral else TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = limit.isAppBlockingEnabled,
                    onCheckedChange = onToggleEnable,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BgDark,
                        checkedTrackColor = AccentGreen,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = SurfaceElevated
                    )
                )
            }

            if (limit.isAppBlockingEnabled) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Limit",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = TimeUtils.formatMinutes(limit.dailyLimitMinutes),
                        color = AccentGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Slider(
                    value = limit.dailyLimitMinutes.toFloat(),
                    onValueChange = { onLimitChange(it.toInt()) },
                    valueRange = 5f..120f,
                    steps = 22, // 5 minute increments up to 120
                    colors = SliderDefaults.colors(
                        thumbColor = AccentGreen,
                        activeTrackColor = AccentGreen,
                        inactiveTrackColor = SurfaceElevated
                    )
                )

                // Progress Bar of today's usage vs limit
                usageStat?.let { stat ->
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { stat.progressPercent },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (stat.isLimitExceeded) WarningCoral else AccentGreen,
                        trackColor = SurfaceElevated
                    )
                }
            }
        }
    }
}
