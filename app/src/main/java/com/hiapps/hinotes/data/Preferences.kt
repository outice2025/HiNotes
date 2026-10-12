package com.hiapps.hinotes.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hiapps.hinotes.ui.theme.AccentPalette
import com.hiapps.hinotes.ui.theme.AppFontScale
import com.hiapps.hinotes.ui.theme.AppFontWeight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

/**
 * Dark-theme preference.
 *
 * Out of the box this is [System]: a fresh install follows the device, so the app is dark on a
 * phone that is dark and light on one that is not. [On] and [Off] pin it either way from the
 * Appearance screen.
 */
enum class DarkModePreference(val key: String) {
    System("system"),
    On("on"),
    Off("off"),
    ;

    /** Resolves the preference against the current system setting. */
    fun isDark(systemInDark: Boolean): Boolean = when (this) {
        System -> systemInDark
        On -> true
        Off -> false
    }

    companion object {
        /** The out-of-the-box value: whatever the device is set to. */
        val Default = System

        fun fromKey(key: String?): DarkModePreference =
            entries.firstOrNull { it.key == key } ?: Default
    }
}

/**
 * Everything the user can configure, in one immutable snapshot.
 *
 * Defaults here are the app's out-of-the-box behaviour: wallpaper-derived dynamic colour is on
 * where the platform supports it (the Blue accent is the fallback), the theme follows the device,
 * Markdown is on, save-as-you-type is on, and the editor opens in edit mode.
 */
data class HiNotesSettings(
    val dynamicColor: Boolean = true,
    val accentPalette: AccentPalette = AccentPalette.Default,
    val darkMode: DarkModePreference = DarkModePreference.Default,
    val oledDark: Boolean = false,
    val appFontScale: AppFontScale = AppFontScale.Default,
    val appFontWeight: AppFontWeight = AppFontWeight.Regular,
    val markdownEnabled: Boolean = true,
    val autoSave: Boolean = true,
    val noteFontScale: AppFontScale = AppFontScale.Default,
    val noteFontWeight: AppFontWeight = AppFontWeight.Regular,
    val widgetFontScale: AppFontScale = AppFontScale.Default,
    val widgetFontWeight: AppFontWeight = AppFontWeight.Default,
    val biometricFace: Boolean = false,
    val screenCaptureDisabled: Boolean = false,
    val sortOrder: NoteSort = NoteSort.UpdatedDesc,
    val startInPreview: Boolean = false,
    val homeLayout: HomeLayout = HomeLayout.Default,
) {
    /** Number of settings that differ from their default, used to report import results. */
    fun nonDefaultCount(): Int {
        val defaults = DefaultSettings
        return SettingKey.entries.count { key -> key.read(this) != key.read(defaults) }
    }
}

/** Keys used by the settings export file, so backups stay readable across versions. */
enum class SettingKey(val storageKey: String) {
    DynamicColor("dynamic_color"),
    AccentPalette("accent_palette"),
    DarkMode("dark_mode"),
    OledDark("oled_dark"),
    AppFontScale("app_font_scale"),
    AppFontWeight("app_font_weight"),
    MarkdownEnabled("markdown_enabled"),
    AutoSave("auto_save"),
    NoteFontScale("note_font_scale"),
    NoteFontWeight("note_font_weight"),
    WidgetFontScale("widget_font_scale"),
    WidgetFontWeight("widget_font_weight"),
    BiometricFace("biometric_face"),
    ScreenCaptureDisabled("screen_capture_disabled"),
    SortOrder("sort_order"),
    StartInPreview("start_in_preview"),
    HomeLayout("home_layout"),
    ;

    fun read(settings: HiNotesSettings): String = when (this) {
        DynamicColor -> settings.dynamicColor.toString()
        AccentPalette -> settings.accentPalette.key
        DarkMode -> settings.darkMode.key
        OledDark -> settings.oledDark.toString()
        AppFontScale -> settings.appFontScale.name
        AppFontWeight -> settings.appFontWeight.name
        MarkdownEnabled -> settings.markdownEnabled.toString()
        AutoSave -> settings.autoSave.toString()
        NoteFontScale -> settings.noteFontScale.name
        NoteFontWeight -> settings.noteFontWeight.name
        WidgetFontScale -> settings.widgetFontScale.name
        WidgetFontWeight -> settings.widgetFontWeight.name
        BiometricFace -> settings.biometricFace.toString()
        ScreenCaptureDisabled -> settings.screenCaptureDisabled.toString()
        SortOrder -> settings.sortOrder.key
        StartInPreview -> settings.startInPreview.toString()
        HomeLayout -> settings.homeLayout.key
    }
}

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "hinotes_settings")

/** Reads and writes [HiNotesSettings] through DataStore. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val accentPalette = stringPreferencesKey("accent_palette")
        val darkMode = stringPreferencesKey("dark_mode")
        val oledDark = booleanPreferencesKey("oled_dark")
        val appFontScale = stringPreferencesKey("app_font_scale")
        val appFontWeight = stringPreferencesKey("app_font_weight")
        val markdownEnabled = booleanPreferencesKey("markdown_enabled")
        val autoSave = booleanPreferencesKey("auto_save")
        val noteFontScale = stringPreferencesKey("note_font_scale")
        val noteFontWeight = stringPreferencesKey("note_font_weight")
        val widgetFontScale = stringPreferencesKey("widget_font_scale")
        val widgetFontWeight = stringPreferencesKey("widget_font_weight")
        val biometricFace = booleanPreferencesKey("biometric_face")
        val screenCaptureDisabled = booleanPreferencesKey("screen_capture_disabled")
        val sortOrder = stringPreferencesKey("sort_order")
        val startInPreview = booleanPreferencesKey("start_in_preview")
        val homeLayout = stringPreferencesKey("home_layout")
    }

    val settings: Flow<HiNotesSettings> = context.settingsStore.data
        .catch { e ->
            // A corrupt or unreadable store must not take the app down; fall back to defaults.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            val defaults = HiNotesSettings()
            HiNotesSettings(
                dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
                accentPalette = prefs[Keys.accentPalette]?.let(AccentPalette::fromKey)
                    ?: defaults.accentPalette,
                darkMode = DarkModePreference.fromKey(prefs[Keys.darkMode]),
                oledDark = prefs[Keys.oledDark] ?: defaults.oledDark,
                appFontScale = prefs[Keys.appFontScale]?.let(AppFontScale::fromKey)
                    ?: defaults.appFontScale,
                appFontWeight = prefs[Keys.appFontWeight]?.let(AppFontWeight::fromKey)
                    ?: defaults.appFontWeight,
                markdownEnabled = prefs[Keys.markdownEnabled] ?: defaults.markdownEnabled,
                autoSave = prefs[Keys.autoSave] ?: defaults.autoSave,
                noteFontScale = prefs[Keys.noteFontScale]?.let(AppFontScale::fromKey)
                    ?: defaults.noteFontScale,
                noteFontWeight = prefs[Keys.noteFontWeight]?.let(AppFontWeight::fromKey)
                    ?: defaults.noteFontWeight,
                widgetFontScale = prefs[Keys.widgetFontScale]?.let(AppFontScale::fromKey)
                    ?: defaults.widgetFontScale,
                widgetFontWeight = prefs[Keys.widgetFontWeight]?.let(AppFontWeight::fromKey)
                    ?: defaults.widgetFontWeight,
                biometricFace = prefs[Keys.biometricFace] ?: defaults.biometricFace,
                screenCaptureDisabled = prefs[Keys.screenCaptureDisabled]
                    ?: defaults.screenCaptureDisabled,
                sortOrder = prefs[Keys.sortOrder]?.let(NoteSort::fromKey) ?: defaults.sortOrder,
                startInPreview = prefs[Keys.startInPreview] ?: defaults.startInPreview,
                homeLayout = prefs[Keys.homeLayout]?.let(HomeLayout::fromKey)
                    ?: defaults.homeLayout,
            )
        }

    suspend fun current(): HiNotesSettings = settings.first()

    /**
     * Reads the settings once, blocking until they are in hand.
     *
     * Called before anything is drawn, because the first frame needs to know what the theme is:
     * the flow above emits the stored values a moment after the app starts, so a composition that
     * begins before that shows the defaults - which, when the system is in light mode and this app
     * has been set to dark, is a light screen flashing in front of a dark app.
     *
     * Blocking the main thread is the whole point here and it is bounded: reading one small
     * preferences file once, before the first frame, while the app's own launch window is still on
     * screen. Everything after this reads the flow.
     */
    fun currentBlocking(): HiNotesSettings = runBlocking { current() }

    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamicColor] = value }

    suspend fun setAccentPalette(value: AccentPalette) =
        edit { it[Keys.accentPalette] = value.key }

    suspend fun setDarkMode(value: DarkModePreference) = edit { it[Keys.darkMode] = value.key }

    suspend fun setOledDark(value: Boolean) = edit { it[Keys.oledDark] = value }

    suspend fun setAppFontScale(value: AppFontScale) =
        edit { it[Keys.appFontScale] = value.name }

    suspend fun setAppFontWeight(value: AppFontWeight) =
        edit { it[Keys.appFontWeight] = value.name }

    suspend fun setMarkdownEnabled(value: Boolean) = edit { it[Keys.markdownEnabled] = value }

    suspend fun setAutoSave(value: Boolean) = edit { it[Keys.autoSave] = value }

    suspend fun setNoteFontScale(value: AppFontScale) =
        edit { it[Keys.noteFontScale] = value.name }

    suspend fun setNoteFontWeight(value: AppFontWeight) =
        edit { it[Keys.noteFontWeight] = value.name }

    suspend fun setWidgetFontScale(value: AppFontScale) =
        edit { it[Keys.widgetFontScale] = value.name }

    suspend fun setWidgetFontWeight(value: AppFontWeight) =
        edit { it[Keys.widgetFontWeight] = value.name }

    suspend fun setBiometricFace(value: Boolean) = edit { it[Keys.biometricFace] = value }

    suspend fun setScreenCaptureDisabled(value: Boolean) =
        edit { it[Keys.screenCaptureDisabled] = value }

    suspend fun setSortOrder(value: NoteSort) = edit { it[Keys.sortOrder] = value.key }

    suspend fun setStartInPreview(value: Boolean) = edit { it[Keys.startInPreview] = value }

    suspend fun setHomeLayout(value: HomeLayout) = edit { it[Keys.homeLayout] = value.key }

    suspend fun restoreDefaults() {
        context.settingsStore.edit { it.clear() }
    }

    /** Applies a full snapshot, used by settings import. */
    suspend fun replaceAll(settings: HiNotesSettings) {
        context.settingsStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.dynamicColor] = settings.dynamicColor
            prefs[Keys.accentPalette] = settings.accentPalette.key
            prefs[Keys.darkMode] = settings.darkMode.key
            prefs[Keys.oledDark] = settings.oledDark
            prefs[Keys.appFontScale] = settings.appFontScale.name
            prefs[Keys.appFontWeight] = settings.appFontWeight.name
            prefs[Keys.markdownEnabled] = settings.markdownEnabled
            prefs[Keys.autoSave] = settings.autoSave
            prefs[Keys.noteFontScale] = settings.noteFontScale.name
            prefs[Keys.noteFontWeight] = settings.noteFontWeight.name
            prefs[Keys.widgetFontScale] = settings.widgetFontScale.name
            prefs[Keys.widgetFontWeight] = settings.widgetFontWeight.name
            prefs[Keys.biometricFace] = settings.biometricFace
            prefs[Keys.screenCaptureDisabled] = settings.screenCaptureDisabled
            prefs[Keys.sortOrder] = settings.sortOrder.key
            prefs[Keys.startInPreview] = settings.startInPreview
            prefs[Keys.homeLayout] = settings.homeLayout.key
        }
    }

    /** Builds a snapshot from raw key/value pairs read out of an exported settings file. */
    fun fromPairs(pairs: Map<String, String>, fallback: HiNotesSettings): HiNotesSettings {
        fun bool(key: SettingKey, default: Boolean) =
            pairs[key.storageKey]?.toBooleanStrictOrNull() ?: default

        return HiNotesSettings(
            dynamicColor = bool(SettingKey.DynamicColor, fallback.dynamicColor),
            accentPalette = pairs[SettingKey.AccentPalette.storageKey]
                ?.let(AccentPalette::fromKey) ?: fallback.accentPalette,
            darkMode = pairs[SettingKey.DarkMode.storageKey]
                ?.let(DarkModePreference::fromKey) ?: fallback.darkMode,
            oledDark = bool(SettingKey.OledDark, fallback.oledDark),
            appFontScale = pairs[SettingKey.AppFontScale.storageKey]
                ?.let(AppFontScale::fromKey) ?: fallback.appFontScale,
            appFontWeight = pairs[SettingKey.AppFontWeight.storageKey]
                ?.let(AppFontWeight::fromKey) ?: fallback.appFontWeight,
            markdownEnabled = bool(SettingKey.MarkdownEnabled, fallback.markdownEnabled),
            autoSave = bool(SettingKey.AutoSave, fallback.autoSave),
            noteFontScale = pairs[SettingKey.NoteFontScale.storageKey]
                ?.let(AppFontScale::fromKey) ?: fallback.noteFontScale,
            noteFontWeight = pairs[SettingKey.NoteFontWeight.storageKey]
                ?.let(AppFontWeight::fromKey) ?: fallback.noteFontWeight,
            widgetFontScale = pairs[SettingKey.WidgetFontScale.storageKey]
                ?.let(AppFontScale::fromKey) ?: fallback.widgetFontScale,
            widgetFontWeight = pairs[SettingKey.WidgetFontWeight.storageKey]
                ?.let(AppFontWeight::fromKey) ?: fallback.widgetFontWeight,
            biometricFace = bool(SettingKey.BiometricFace, fallback.biometricFace),
            screenCaptureDisabled = bool(
                SettingKey.ScreenCaptureDisabled,
                fallback.screenCaptureDisabled,
            ),
            sortOrder = pairs[SettingKey.SortOrder.storageKey]
                ?.let(NoteSort::fromKey) ?: fallback.sortOrder,
            startInPreview = bool(SettingKey.StartInPreview, fallback.startInPreview),
            homeLayout = pairs[SettingKey.HomeLayout.storageKey]
                ?.let(HomeLayout::fromKey) ?: fallback.homeLayout,
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsStore.edit(block)
    }
}

/** Convenience for code that needs the out-of-the-box snapshot. */
internal val DefaultSettings: HiNotesSettings = HiNotesSettings()
