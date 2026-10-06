package com.hiapps.hinotes.ui.screens

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.DarkModePreference
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.AccentColorDialog
import com.hiapps.hinotes.ui.components.AlertMessage
import com.hiapps.hinotes.ui.components.FontSettingsDialog
import com.hiapps.hinotes.ui.components.SettingsRow
import com.hiapps.hinotes.ui.components.SettingsScaffold
import com.hiapps.hinotes.ui.components.SettingsSection
import com.hiapps.hinotes.ui.components.SettingsSectionGap
import com.hiapps.hinotes.ui.components.SwitchRow
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.AccentPalette

/**
 * Appearance settings: dynamic colour, dark mode, OLED dark, accent colour, and typography.
 *
 * Font size and weight sit behind one row that opens a floating dialog holding both sliders,
 * the same way the accent colour is edited, so the page stays a compact list of rows.
 */
@Composable
fun AppearanceSettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val systemDark =
        (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    var showAccent by remember { mutableStateOf(false) }
    var showFont by remember { mutableStateOf(false) }
    var showDynamicUnavailable by remember { mutableStateOf(false) }

    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val darkActive = settings.darkMode.isDark(systemDark)

    SettingsScaffold(
        title = stringResource(R.string.appearance_title),
        onBack = onBack,
    ) {
        SettingsSection {
            SwitchRow(
                icon = Symbols.Colors,
                headline = stringResource(R.string.appearance_dynamic),
                supporting = stringResource(R.string.appearance_dynamic_support),
                checked = settings.dynamicColor && supportsDynamic,
                enabled = supportsDynamic,
                onCheckedChange = { checked ->
                    if (supportsDynamic) {
                        viewModel.updateSettings { setDynamicColor(checked) }
                    } else {
                        showDynamicUnavailable = true
                    }
                },
                isFirst = true,
                isLast = false,
            )
            // Directly under dynamic colour, because the two answer the same question - where the
            // colours come from - and it uses the same control as the rows around it rather than
            // a switch of its own in a group of its own.
            SwitchRow(
                icon = Symbols.BrightnessMedium,
                headline = stringResource(R.string.appearance_follow_system),
                supporting = stringResource(R.string.appearance_follow_system_support),
                checked = settings.darkMode == DarkModePreference.System,
                onCheckedChange = { follow ->
                    viewModel.updateSettings {
                        setDarkMode(
                            if (follow) {
                                DarkModePreference.System
                            } else if (systemDark) {
                                DarkModePreference.On
                            } else {
                                DarkModePreference.Off
                            },
                        )
                    }
                },
                isFirst = false,
                isLast = false,
            )
            SwitchRow(
                icon = Symbols.DarkMode,
                headline = stringResource(R.string.appearance_dark),
                supporting = stringResource(R.string.appearance_dark_support),
                checked = darkActive,
                onCheckedChange = { checked ->
                    viewModel.updateSettings {
                        setDarkMode(
                            if (checked) DarkModePreference.On else DarkModePreference.Off,
                        )
                    }
                },
                isFirst = false,
                isLast = false,
            )
            SwitchRow(
                icon = Symbols.Contrast,
                headline = stringResource(R.string.appearance_oled),
                supporting = stringResource(R.string.appearance_oled_support),
                checked = settings.oledDark,
                onCheckedChange = { checked ->
                    viewModel.updateSettings { setOledDark(checked) }
                },
                isFirst = false,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Palette,
                headline = stringResource(R.string.appearance_color),
                // Reports which palette is actually painting the app: the wallpaper's while
                // dynamic colour is on, otherwise the chosen accent.
                supporting = if (settings.dynamicColor && supportsDynamic) {
                    stringResource(R.string.appearance_dynamic)
                } else {
                    stringResource(settings.accentPalette.labelRes)
                },
                onClick = { showAccent = true },
                trailing = { Chevron() },
                isFirst = false,
                isLast = true,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.FormatSize,
                headline = stringResource(R.string.appearance_font_size),
                supporting = stringResource(R.string.appearance_font_size_support),
                onClick = { showFont = true },
                trailing = { Chevron() },
            )
        }
    }

    if (showFont) {
        FontSettingsDialog(
            title = stringResource(R.string.appearance_typography),
            sizeTitle = stringResource(R.string.picker_font_size_title),
            weightTitle = stringResource(R.string.picker_weight_title),
            scale = settings.appFontScale,
            weight = settings.appFontWeight,
            onScaleChange = { scale -> viewModel.updateSettings { setAppFontScale(scale) } },
            onWeightChange = { weight -> viewModel.updateSettings { setAppFontWeight(weight) } },
            onDismiss = { showFont = false },
        )
    }

    if (showAccent) {
        AccentColorDialog(
            options = AccentPalette.entries,
            selected = settings.accentPalette,
            onSelect = { palette ->
                // Choosing a fixed palette is a decision to stop taking colours from the
                // wallpaper, so dynamic colour is switched off in the same edit - otherwise the
                // pick would appear to do nothing.
                viewModel.updateSettings {
                    setAccentPalette(palette)
                    setDynamicColor(false)
                }
            },
            onDismiss = { showAccent = false },
        )
    }

    if (showDynamicUnavailable) {
        AlertMessage(
            title = stringResource(R.string.appearance_dynamic),
            message = stringResource(R.string.appearance_dynamic_unavailable),
            confirmLabel = stringResource(R.string.common_ok),
            onDismiss = { showDynamicUnavailable = false },
        )
    }
}
