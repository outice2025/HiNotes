package com.hinotes.app.ui.screens

import android.os.Build
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hinotes.app.R
import com.hinotes.app.data.DarkModePreference
import com.hinotes.app.ui.AppViewModel
import com.hinotes.app.ui.components.AlertMessage
import com.hinotes.app.ui.components.LevelSlider
import com.hinotes.app.ui.components.SettingsRow
import com.hinotes.app.ui.components.SettingsScaffold
import com.hinotes.app.ui.components.SettingsSection
import com.hinotes.app.ui.components.SettingsSectionGap
import com.hinotes.app.ui.components.SingleChoiceDialog
import com.hinotes.app.ui.components.SwitchRow
import com.hinotes.app.ui.icons.Symbols
import com.hinotes.app.ui.theme.AppFontScale
import com.hinotes.app.ui.theme.AppFontWeight

/**
 * Appearance settings: dynamic colour, dark mode, OLED dark, accent colour, and the app's type
 * scale and weight.
 *
 * Size and weight are four-stop sliders rather than dialogs, so both are adjustable in place and
 * the effect on the surrounding UI is visible while dragging.
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
                supporting = stringResource(R.string.appearance_color_support),
                onClick = { showAccent = true },
                trailing = { Chevron() },
                isFirst = false,
                isLast = true,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.DarkMode,
                headline = stringResource(R.string.appearance_follow_system),
                supporting = stringResource(R.string.appearance_follow_system_support),
                onClick = {
                    viewModel.updateSettings {
                        setDarkMode(
                            if (settings.darkMode == DarkModePreference.System) {
                                if (systemDark) DarkModePreference.On else DarkModePreference.Off
                            } else {
                                DarkModePreference.System
                            },
                        )
                    }
                },
                trailing = {
                    androidx.compose.material3.Switch(
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
                    )
                },
            )
        }

        SettingsSectionGap()

        // Typography: two four-stop sliders.
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
                    selected = settings.appFontScale.ordinal,
                    label = { stringResource(it.labelRes) },
                    onSelect = { index ->
                        viewModel.updateSettings {
                            setAppFontScale(AppFontScale.entries[index])
                        }
                    },
                    title = stringResource(R.string.picker_font_size_title),
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                LevelSlider(
                    levels = AppFontWeight.entries,
                    selected = settings.appFontWeight.ordinal,
                    label = { stringResource(it.labelRes) },
                    onSelect = { index ->
                        viewModel.updateSettings {
                            setAppFontWeight(AppFontWeight.entries[index])
                        }
                    },
                    title = stringResource(R.string.picker_weight_title),
                )

                // Live preview at the chosen size and weight.
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Text(
                        text = stringResource(R.string.picker_preview),
                        fontWeight = settings.appFontWeight.weight,
                        fontSize = (16f * settings.appFontScale.multiplier).sp,
                        lineHeight = (24f * settings.appFontScale.multiplier).sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }

    if (showAccent) {
        SingleChoiceDialog(
            title = stringResource(R.string.picker_accent_title),
            options = listOf(false, true),
            selected = settings.dynamicColor,
            label = { dynamic ->
                if (dynamic) {
                    stringResource(R.string.appearance_dynamic)
                } else {
                    stringResource(R.string.picker_accent_mono)
                }
            },
            onSelect = { dynamic ->
                if (dynamic && !supportsDynamic) {
                    showDynamicUnavailable = true
                } else {
                    viewModel.updateSettings { setDynamicColor(dynamic) }
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
