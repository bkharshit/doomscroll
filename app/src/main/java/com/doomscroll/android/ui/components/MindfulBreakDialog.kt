package com.doomscroll.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.InfinityWhite
import com.doomscroll.android.ui.theme.MintSecondary
import com.doomscroll.android.ui.theme.TactileBorder
import com.doomscroll.android.ui.theme.TactileGroove
import com.doomscroll.android.ui.theme.TactileRecessed
import com.doomscroll.android.ui.theme.TactileSurface
import com.doomscroll.android.ui.theme.TextDark
import com.doomscroll.android.ui.theme.TextMutedSlate
import com.doomscroll.android.ui.theme.TextSecondarySlate

@Composable
fun MindfulBreakDialog(
    onDismiss: () -> Unit,
    onStartBreak: (durationMinutes: Int, intention: String) -> Unit
) {
    var selectedMinutes by remember { mutableIntStateOf(5) }
    var intention by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .neoExtruded(cornerRadius = 24.dp)
                .padding(24.dp)
        ) {
            Text(
                text = "Take a Mindful Break",
                color = TextDark,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pausing the shield allows you to check what you need intentionally. The shield will automatically re-lock when time is up.",
                color = TextMutedSlate,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Select Duration",
                color = TextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(3, 5, 10).forEach { mins ->
                    val isSelected = selectedMinutes == mins
                    Button(
                        onClick = { selectedMinutes = mins },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) CoralPrimary else TactileRecessed,
                            contentColor = if (isSelected) InfinityWhite else TextDark
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${mins}m",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "What is your intention?",
                color = TextDark,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
                value = intention,
                onValueChange = { intention = it },
                placeholder = { Text("e.g. Reply to mom, check event post", color = TextMutedSlate, fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CoralPrimary,
                    unfocusedBorderColor = TactileGroove,
                    cursorColor = CoralPrimary,
                    focusedTextColor = TextDark,
                    unfocusedTextColor = TextDark
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Cancel", color = TextSecondarySlate)
                }
                Button(
                    onClick = {
                        onStartBreak(selectedMinutes, intention.trim())
                        onDismiss()
                    },
                    // A typed intention is the friction: no intention, no pause.
                    enabled = intention.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Start Break", color = InfinityWhite, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
