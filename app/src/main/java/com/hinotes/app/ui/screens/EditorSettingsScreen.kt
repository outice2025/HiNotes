package com.hinotes.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hinotes.app.R
import com.hinotes.app.ui.AppViewModel
import com.hinotes.app.ui.components.LevelSlider
import com.hinotes.app.ui.components.SettingsRow
import com.hinotes.app.ui.components.SettingsScaffold
import com.hinotes.app.ui.components.SettingsSection
import com.hinotes.app.ui.components.SettingsSectionGap
import com.hinotes.app.ui.components.SwitchRow
import com.hinotes.app.ui.icons.Symbols
import com.hinotes.app.ui.theme.AppFontScale
import com.hinotes.app.ui.theme.AppFontWeight

/** Autosave cadence, in seconds, quoted in the autosave row's supporting text. */
private const val AUTOSAVE_INTERVAL_SECONDS = 20

/**
 * Editor settings: Markdown, autosave, and the note content type scale and weight.
 *
 * The note type settings only affect note content (editor body, previews and list snippets),
 * which is why they sit alongside the app-wide settings on the Appearance screen rather than
 * replacing them. Both pairs are four-stop sliders.
 */
@Composable
fun EditorSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
                isLast = true,
            )
        }

        SettingsSectionGap()

        // Note typography: the same two sliders, applied to note content only.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.appearance_typography),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                LevelSlider(
                    levels = AppFontScale.entries,
                    selected = settings.noteFontScale.ordinal,
                    label = { stringResource(it.labelRes) },
                    onSelect = { index ->
                        viewModel.updateSettings {
                            setNoteFontScale(AppFontScale.entries[index])
                        }
                    },
                    title = stringResource(R.string.picker_font_size_note_title),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LevelSlider(
                    levels = AppFontWeight.entries,
                    selected = settings.noteFontWeight.ordinal,
                    label = { stringResource(it.labelRes) },
                    onSelect = { index ->
                        viewModel.updateSettings {
                            setNoteFontWeight(AppFontWeight.entries[index])
                        }
                    },
                    title = stringResource(R.string.picker_weight_note_title),
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = stringResource(R.string.picker_preview),
                        fontWeight = settings.noteFontWeight.weight,
                        fontSize = (16f * settings.noteFontScale.multiplier).sp,
                        lineHeight = (24f * settings.noteFontScale.multiplier).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}
