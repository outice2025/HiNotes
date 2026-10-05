package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.theme.AccentPalette
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/**
 * The accent colour picker.
 *
 * Each option shows the accent's own primary colour as a swatch beside its name, so the choice
 * is made by looking at the colour rather than by reading a label - the same affordance Android's
 * wallpaper picker uses. The swatch is drawn from the light scheme in every case, which keeps the
 * seven options directly comparable whatever theme is currently on screen.
 *
 * Dynamic colour is deliberately not an option here: it is a switch of its own on the Appearance
 * page, and mixing a wallpaper-derived scheme into a list of fixed palettes would make the
 * selected entry ambiguous.
 */
@Composable
fun AccentColorDialog(
    options: List<AccentPalette>,
    selected: AccentPalette,
    onSelect: (AccentPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.picker_accent_title),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            ),
                        color = Color.Transparent,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = isSelected, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            AccentSwatch(option.swatch)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(option.labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
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

/** One accent's colour, shown as a disc with a hairline edge so pale colours stay visible. */
@Composable
private fun AccentSwatch(color: Color) {
    Surface(
        modifier = Modifier.size(28.dp),
        shape = CircleShape,
        color = color,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {}
}
