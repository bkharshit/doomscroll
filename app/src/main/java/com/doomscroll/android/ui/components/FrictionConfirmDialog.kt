package com.doomscroll.android.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.doomscroll.android.ui.theme.CoralPrimary
import com.doomscroll.android.ui.theme.TextDark
import com.doomscroll.android.ui.theme.TextMutedSlate
import kotlinx.coroutines.delay

/**
 * A confirmation whose confirm button only unlocks after [waitSeconds]. Used wherever a single tap
 * would otherwise switch protection off. Dismissing resets the wait.
 */
@Composable
fun FrictionConfirmDialog(
    title: String,
    message: String,
    waitSeconds: Int,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var remaining by remember { mutableIntStateOf(waitSeconds) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            delay(1000L)
            remaining--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextDark) },
        text = { Text(message, color = TextMutedSlate, fontSize = 13.sp, lineHeight = 18.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = remaining == 0) {
                Text(
                    text = if (remaining > 0) "$confirmLabel (${remaining}s)" else confirmLabel,
                    fontWeight = FontWeight.Bold,
                    color = if (remaining > 0) TextMutedSlate else CoralPrimary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep protection on", color = TextDark)
            }
        }
    )
}

/** Plain explanation dialog with a single OK button. */
@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold, color = TextDark) },
        text = { Text(message, color = TextMutedSlate, fontSize = 13.sp, lineHeight = 18.sp) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK", fontWeight = FontWeight.Bold, color = CoralPrimary) }
        }
    )
}
