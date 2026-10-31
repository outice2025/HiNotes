package com.hiapps.hinotes.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.hiapps.hinotes.data.AppLanguage
import java.util.Locale

/**
 * The app language, mirrored outside the settings store.
 *
 * The chosen language lives in the settings like every other preference, but it has to be in hand
 * in `attachBaseContext`, before DataStore can be read and before the first string is resolved -
 * its flow emits a moment after the app starts, and an activity built with the device's language
 * would draw one whole screen in the wrong one. A small preferences file of its own is readable
 * there synchronously. `SettingsRepository` still owns the setting; the mirror only remembers it
 * for the next process start, and the two are written together by [apply].
 *
 * Android 13 and above also carry the choice to the platform, so the widget and anything else the
 * system builds for this app follow it without reading the mirror themselves. That is done directly
 * rather than through `AppCompatDelegate`, whose per-app language API arrived with AppCompat and
 * which this app does not depend on.
 */
object AppLocale {

    /** The mirror's file. Nothing else is kept in it. */
    private const val FILE = "hinotes_locale"

    private const val KEY = "language"

    private fun prefs(base: Context) =
        base.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The language remembered for this device, or "follow the system" before anything is chosen. */
    fun current(base: Context): AppLanguage =
        AppLanguage.fromKey(prefs(base).getString(KEY, null))

    /**
     * Records [language] and, from Android 13, hands it to the platform.
     *
     * The write is asynchronous, which is enough for both readers: the in-memory preferences are
     * updated before this returns, so an activity recreated straight afterwards reads the new value.
     */
    fun apply(base: Context, language: AppLanguage) {
        prefs(base).edit().putString(KEY, language.key).apply()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val manager = base.getSystemService(LocaleManager::class.java) ?: return
        // The empty list is what "follow the system" means to the platform.
        manager.applicationLocales =
            language.tag?.let { LocaleList(localeOf(it)) } ?: LocaleList.getEmptyLocaleList()
    }

    /**
     * [base] drawing itself in [AppLanguage], for an activity asking to be built in it.
     *
     * "Follow the system" is the absence of an override rather than a configuration of its own, so
     * that option returns [base] untouched and the platform keeps applying whatever locale rules the
     * device has.
     */
    fun localized(base: Context): Context {
        val tag = current(base).tag ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(localeOf(tag))
        return base.createConfigurationContext(configuration)
    }

    /**
     * The [Locale] for a BCP-47 [tag].
     *
     * The tag is parsed rather than constructed: `Locale("zh-CN")` reads its argument as a *language
     * subtag* and comes back as the language "zh-cn", which is not a language anything is written
     * in, and the screen would fall back to the device's own language instead of the one chosen.
     */
    private fun localeOf(tag: String): Locale = Locale.forLanguageTag(tag)
}
