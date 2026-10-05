package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.theme.AppFontScale
import com.hiapps.hinotes.ui.theme.AppFontWeight
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/**
 * The font dialog opened from the Appearance and Editor settings rows.
 *
 * Both settings are four-stop sliders inside one dialog, which is the same shape the accent
 * colour uses: the row stays a plain list item, and its value is edited in a floating panel
 * rather than inline. That keeps the settings page itself compact while still giving the sliders
 * room to breathe.
 *
 * @param sizeTitle/weightTitle separate labels so the same dialog can drive either the app's
 *   typography or the note content's.
 */
@Composable
fun FontSettingsDialog(
    title: String,
    sizeTitle: String,
    weightTitle: String,
    scale: AppFontScale,
    weight: AppFontWeight,
    onScaleChange: (AppFontScale) -> Unit,
    onWeightChange: (AppFontWeight) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                LevelSlider(
                    levels = AppFontScale.entries,
                    selected = scale.ordinal,
                    label = { stringResource(it.labelRes) },
                    onSelect = { onScaleChange(AppFontScale.entries[it]) },
                    title = sizeTitle,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LevelSlider(
                    levels = AppFontWeight.entries,
                    selected = weight.ordinal,
                    label = { stringResource(it.labelRes) },
                    onSelect = { onWeightChange(AppFontWeight.entries[it]) },
                    title = weightTitle,
                )

                // Live preview at the chosen size and weight.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = stringResource(R.string.picker_preview),
                        fontWeight = weight.weight,
                        fontSize = (16f * scale.multiplier).sp,
                        lineHeight = (24f * scale.multiplier).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) }
        },
        shape = RoundedCornerShape(HiNotesCorners.Dialog),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
