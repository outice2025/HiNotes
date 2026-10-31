package com.hiapps.hinotes.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.AppLanguage

/**
 * The language picker.
 *
 * Every language the app is written in is listed beside "follow the device", and each option names
 * itself in its own script - so the list can be read whichever language is on screen at the time,
 * including one the user cannot read and wants to leave.
 *
 * A tap is the confirmation: the caller applies the language and closes the dialog in the same edit,
 * and the rebuilt activity comes back in the new language. An earlier version kept the choice in the
 * dialog until a confirm button applied it, because the rebuild used to start under the open dialog
 * and read as a flash; closing first removes the flash, so the extra step had nothing left to do.
 *
 * @param selected the language in force, shown as the list's selected row.
 * @param onConfirm what a tap does with the chosen language; the caller applies it and closes the
 *   dialog.
 * @param onDismiss what 取消 and a tap outside the dialog do.
 */
@Composable
fun LanguageDialog(
    selected: AppLanguage,
    onConfirm: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerDialog(
        title = stringResource(R.string.settings_language),
        onDismiss = onDismiss,
    ) {
        AppLanguage.entries.forEach { option ->
            PickerRow(
                selected = option == selected,
                label = stringResource(option.labelRes),
                onClick = { onConfirm(option) },
            )
        }
    }
}
