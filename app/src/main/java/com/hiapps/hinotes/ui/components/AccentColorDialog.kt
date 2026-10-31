package com.hiapps.hinotes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.theme.AccentPalette

/**
 * The accent colour picker.
 *
 * Each option is its name alone. The list repaints the app as soon as a row is tapped, so the choice
 * is made by looking at the screen behind the dialog rather than at a swatch on the row. The dialog
 * stays open after a tap - unlike the language list, which closes on its only tap - because changing
 * the accent does not rebuild the activity, so several accents can be tried one after another.
 *
 * Dynamic colour is deliberately not an option here: it is a switch of its own on the Appearance
 * page, and mixing a wallpaper-derived scheme into a list of fixed palettes would make the selected
 * entry ambiguous. That is also why [selected] may be null: with the wallpaper painting the app, no
 * fixed palette is the one in force, and not one of the list's rows may say otherwise. The list then
 * reads as a set of choices with nothing chosen, which is exactly what the state is.
 *
 * @param selected the palette in force, or null when dynamic colour is on.
 * @param onSelect what a tap does with the chosen palette; the caller applies it and leaves the
 *   dialog open.
 * @param onDismiss what 取消 and a tap outside the dialog do.
 */
@Composable
fun AccentColorDialog(
    options: List<AccentPalette>,
    selected: AccentPalette?,
    onSelect: (AccentPalette) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerDialog(
        title = stringResource(R.string.picker_accent_title),
        onDismiss = onDismiss,
    ) {
        options.forEach { option ->
            PickerRow(
                selected = option == selected,
                label = stringResource(option.labelRes),
                onClick = { onSelect(option) },
            )
        }
    }
}
