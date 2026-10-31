package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/** A simple information dialog with one acknowledgement button. */
@Composable
fun AlertMessage(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(confirmLabel) }
        },
        shape = RoundedCornerShape(HiNotesCorners.GroupOuter),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
