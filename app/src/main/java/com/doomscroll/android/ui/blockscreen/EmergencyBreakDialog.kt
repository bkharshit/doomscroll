package com.doomscroll.android.ui.blockscreen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.TextDark
import com.doomscroll.android.ui.theme.TextMutedSlate
import kotlinx.coroutines.delay

/**
 * Friction before an emergency break: a countdown that grows with each break taken today, and a
 * typed intention from the second break.
 */
@Composable
fun EmergencyBreakDialog(
    appName: String,
    waitSeconds: Int,
    requireIntention: Boolean,
    onConfirm: (intention: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(waitSeconds) }
    var intention by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000L)
            remaining--
        }
    }
    val ready = remaining == 0 && (!requireIntention || intention.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("5 minutes of $appName?", fontWeight = FontWeight.Bold, color = TextDark) },
        text = {
            Column {
                Text(
                    text = if (remaining > 0) {
                        "Take a breath. You can continue in $remaining s."
                    } else {
                        "If you still want it, the break starts now and ends in 5 minutes."
                    },
                    color = TextMutedSlate,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                if (requireIntention) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = intention,
                        onValueChange = { intention = it },
                        label = { Text("What will you do in these 5 minutes?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(intention.trim().ifEmpty { null }) }, enabled = ready) {
                Text(
                    text = if (remaining > 0) "Start break (${remaining}s)" else "Start break",
                    fontWeight = FontWeight.Bold,
                    color = if (ready) CoralPrimary else TextMutedSlate
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Never mind", color = TextDark) }
        }
    )
}
