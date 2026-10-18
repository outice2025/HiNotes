package com.hiapps.hinotes.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.LanguageDialog
import com.hiapps.hinotes.ui.components.SettingsRow
import com.hiapps.hinotes.ui.components.SettingsScaffold
import com.hiapps.hinotes.ui.components.SettingsSection
import com.hiapps.hinotes.ui.components.SettingsSectionGap
import com.hiapps.hinotes.ui.icons.Symbols

/**
 * The settings root.
 *
 * Sections follow the design: appearance/editor/widget/language, then backup/privacy, then about.
 * "Language" picks from the languages the app is written in without leaving it, the way the accent
 * colour is picked: the choice is one of the app's own settings, so it is made beside the rest of
 * them rather than in a system screen.
 */
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenEditorSettings: () -> Unit,
    onOpenWidgetSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenUnlock: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showLanguage by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.settings_title),
        onBack = onBack,
    ) {
        SettingsSection {
            SettingsRow(
                icon = Symbols.Palette,
                headline = stringResource(R.string.settings_appearance),
                supporting = stringResource(R.string.settings_appearance_support),
                onClick = onOpenAppearance,
                trailing = { Chevron() },
                isFirst = true,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Edit,
                headline = stringResource(R.string.settings_editor),
                supporting = stringResource(R.string.settings_editor_support),
                onClick = onOpenEditorSettings,
                trailing = { Chevron() },
                isFirst = false,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Style,
                headline = stringResource(R.string.settings_widget),
                supporting = stringResource(R.string.settings_widget_support),
                onClick = onOpenWidgetSettings,
                trailing = { Chevron() },
                isFirst = false,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Language,
                headline = stringResource(R.string.settings_language),
                supporting = stringResource(R.string.settings_language_support),
                onClick = { showLanguage = true },
                trailing = { Chevron() },
                isFirst = false,
                isLast = true,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.Archive,
                headline = stringResource(R.string.settings_backup),
                supporting = stringResource(R.string.settings_backup_support),
                onClick = onOpenBackup,
                trailing = { Chevron() },
                isFirst = true,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Security,
                headline = stringResource(R.string.settings_unlock),
                supporting = stringResource(R.string.settings_unlock_support),
                onClick = onOpenUnlock,
                trailing = { Chevron() },
                isFirst = false,
                isLast = true,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.Info,
                headline = stringResource(R.string.settings_about),
                supporting = stringResource(R.string.settings_about_support),
                onClick = onOpenAbout,
                trailing = { Chevron() },
            )
        }
    }

    if (showLanguage) {
        // The dialog closes on the tap that picks a language: the change is applied at once and
        // the screen behind it is rebuilt in the new language, so there is nothing left to confirm.
        LanguageDialog(
            selected = settings.language,
            onSelect = { language ->
                viewModel.updateSettings { setLanguage(language) }
                showLanguage = false
            },
            onDismiss = { showLanguage = false },
        )
    }
}

/** The trailing `chevron_right` glyph every navigable row carries. */
@Composable
internal fun Chevron() {
    com.hiapps.hinotes.ui.icons.SymbolIcon(
        codepoint = Symbols.ChevronRight,
        contentDescription = null,
        size = 24.dp,
        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
