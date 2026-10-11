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
import com.hiapps.hinotes.ui.icons.Symbols

/**
 * Widget settings: the type the home screen card is drawn in.
 *
 * A widget is painted by the launcher rather than by this app, so its type is a setting of its own
 * rather than a copy of the note type - the card is read at a glance, at arm's length, on whatever
 * wallpaper is behind it. The picker is the same one the app's own typography and the note type
 * use, so the three read alike.
 */
@Composable
fun WidgetSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showFont by remember { mutableStateOf(false) }

    SettingsScaffold(
        title = stringResource(R.string.widget_title),
        onBack = onBack,
    ) {
        SettingsSection {
            SettingsRow(
                icon = Symbols.FormatSize,
                headline = stringResource(R.string.widget_settings_font),
                supporting = stringResource(R.string.widget_settings_font_support),
                onClick = { showFont = true },
                trailing = { Chevron() },
            )
        }
    }

    if (showFont) {
        FontSettingsDialog(
            title = stringResource(R.string.widget_title),
            sizeTitle = stringResource(R.string.picker_font_size_widget_title),
            weightTitle = stringResource(R.string.picker_weight_widget_title),
            scale = settings.widgetFontScale,
            weight = settings.widgetFontWeight,
            onScaleChange = { scale -> viewModel.updateSettings { setWidgetFontScale(scale) } },
            onWeightChange = { weight -> viewModel.updateSettings { setWidgetFontWeight(weight) } },
            onDismiss = { showFont = false },
        )
    }
}
