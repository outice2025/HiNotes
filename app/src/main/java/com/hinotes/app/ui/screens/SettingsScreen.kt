package com.hinotes.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hinotes.app.R
import com.hinotes.app.ui.components.AlertMessage
import com.hinotes.app.ui.components.SettingsRow
import com.hinotes.app.ui.components.SettingsScaffold
import com.hinotes.app.ui.components.SettingsSection
import com.hinotes.app.ui.components.SettingsSectionGap
import com.hinotes.app.ui.icons.Symbols

/**
 * The settings root.
 *
 * Sections follow the design: appearance/editor/language, then backup/unlock, then about.
 * "Language" opens Android's own per-app language picker on API 33+, which is the platform
 * requirement for an in-app language entry (in-app language packs were folded into that
 * system UI in Android 13).
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenEditorSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenUnlock: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    var showLanguageFallback by remember { mutableStateOf(false) }

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
                icon = Symbols.Language,
                headline = stringResource(R.string.settings_language),
                supporting = stringResource(R.string.settings_language_support),
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        // Android 13+ per-app language settings.
                        context.startActivity(
                            Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
                                .setData(android.net.Uri.fromParts("package", context.packageName, null))
                        )
                    } else {
                        showLanguageFallback = true
                    }
                },
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
                icon = Symbols.Password,
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

    if (showLanguageFallback) {
        AlertMessage(
            title = stringResource(R.string.settings_language),
            message = stringResource(R.string.settings_language_support),
            confirmLabel = stringResource(R.string.common_ok),
            onDismiss = { showLanguageFallback = false },
        )
    }
}

/** The trailing `chevron_right` glyph every navigable row carries. */
@Composable
internal fun Chevron() {
    com.hinotes.app.ui.icons.SymbolIcon(
        codepoint = Symbols.ChevronRight,
        contentDescription = null,
        size = 24.dp,
        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
