package com.hiapps.hinotes.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.FontSettingsDialog
import com.hiapps.hinotes.ui.components.SettingsRow
import com.hiapps.hinotes.ui.components.SettingsScaffold
import com.hiapps.hinotes.ui.components.SettingsSection
import com.hiapps.hinotes.ui.components.SettingsSectionGap
import com.hiapps.hinotes.ui.components.SwitchRow
import com.hiapps.hinotes.ui.icons.Symbols

/** Autosave cadence, in seconds, quoted in the autosave row's supporting text. */
private const val AUTOSAVE_INTERVAL_SECONDS = 20

/**
 * Editor settings: Markdown, autosave, and the note content typography.
 *
 * The note type settings only affect note content - the editor body, previews and list previews -
 * which is why they sit here as well as on the Appearance screen. Like the app typography, they
 * open a floating dialog rather than living inline.
 */
@Composable
fun EditorSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showFont by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.editor_settings_title),
        onBack = onBack,
    ) {
        SettingsSection {
            SwitchRow(
                icon = Symbols.Sell,
                headline = stringResource(R.string.editor_settings_markdown),
                supporting = stringResource(R.string.editor_settings_markdown_support),
                checked = settings.markdownEnabled,
                onCheckedChange = { checked ->
                    viewModel.updateSettings { setMarkdownEnabled(checked) }
                },
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SwitchRow(
                icon = Symbols.Save,
                headline = stringResource(R.string.editor_settings_autosave),
                supporting = stringResource(
                    R.string.editor_settings_autosave_support,
                    AUTOSAVE_INTERVAL_SECONDS,
                ),
                checked = settings.autoSave,
                onCheckedChange = { checked ->
                    viewModel.updateSettings { setAutoSave(checked) }
                },
                isFirst = true,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.TextFields,
                headline = stringResource(R.string.editor_settings_font_size),
                supporting = stringResource(R.string.editor_settings_font_size_support),
                onClick = { showFont = true },
                trailing = { Chevron() },
                isFirst = false,
                isLast = true,
            )
        }
    }

    if (showFont) {
        FontSettingsDialog(
            title = stringResource(R.string.appearance_typography),
            sizeTitle = stringResource(R.string.picker_font_size_note_title),
            weightTitle = stringResource(R.string.picker_weight_note_title),
            scale = settings.noteFontScale,
            weight = settings.noteFontWeight,
            onScaleChange = { scale -> viewModel.updateSettings { setNoteFontScale(scale) } },
            onWeightChange = { weight -> viewModel.updateSettings { setNoteFontWeight(weight) } },
            onDismiss = { showFont = false },
        )
    }
}
