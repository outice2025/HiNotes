package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.R
import java.text.DateFormat
import java.util.Date

/**
 * The note's properties, raised from the bottom edge of the screen.
 *
 * Reports what the design asks for and nothing else: how much text the note holds, when it was
 * created and when it was last written to.
 *
 * @param wordCount characters of text in the body, title and punctuation excluded.
 * @param createdAt/updatedAt epoch milliseconds, or null while the note is still being created.
 */
@Composable
fun PropertiesSheet(
    wordCount: Int,
    createdAt: Long?,
    updatedAt: Long?,
    onDismiss: () -> Unit,
) {
    // Locale-aware and context-free: the platform's own medium date and short time, so the sheet
    // reads the same way the system settings screens do.
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    fun stamp(millis: Long?): String =
        millis?.let { formatter.format(Date(it)) }.orEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.editor_props_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            PropertyRow(
                label = stringResource(R.string.editor_props_words),
                value = wordCount.toString(),
                hint = stringResource(R.string.editor_props_words_hint),
            )
            PropertyRow(
                label = stringResource(R.string.editor_props_created),
                value = stamp(createdAt),
            )
            PropertyRow(
                label = stringResource(R.string.editor_props_updated),
                value = stamp(updatedAt),
            )
        }
    }
}

/** One label/value line of the properties sheet. */
@Composable
private fun PropertyRow(label: String, value: String, hint: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
