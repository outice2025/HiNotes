package com.hiapps.hinotes.ui.theme

import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.hiapps.hinotes.R

/**
 * The accent colours the user can pick from.
 *
 * Each entry names a palette the app can paint itself with when wallpaper-derived dynamic colour
 * is off. Every palette is a full Material 3 light and dark scheme, so switching accent changes
 * the whole app consistently rather than just one colour: the surfaces, containers and outlines
 * move together the way they do under dynamic colour.
 *
 * [Blue] is the app's own accent - the one a fresh install opens with, the one the launcher icon
 * is painted in, and the one the window background matches. [LightPurple] is the palette the app
 * was originally specified with (see `ColorSchemes.kt`). The rest are generated from a single seed
 * colour by the Material 3 HCT algorithm - Tonal Spot for the chromatic accents, Monochrome for
 * the grey one - so each role carries the tone MD3 prescribes.
 *
 * Declaration order is the order the picker lists them in, so it is part of the UI.
 *
 * @param key the value written to settings and to a settings backup; never localised.
 * @param labelRes the accent's name, shown in the picker.
 */
enum class AccentPalette(val key: String, @param:StringRes val labelRes: Int) {
    Blue("blue", R.string.accent_blue),
    TeaGreen("tea_green", R.string.accent_tea_green),
    LightGray("light_gray", R.string.accent_light_gray),
    Red("red", R.string.accent_red),
    LightPurple("light_purple", R.string.accent_light_purple),
    Orange("orange", R.string.accent_orange),
    Yellow("yellow", R.string.accent_yellow),
    ;

    /** The scheme this accent paints with, before the OLED treatment is applied. */
    internal fun scheme(dark: Boolean): ColorScheme = when (this) {
        Blue -> if (dark) BlueDark else BlueLight
        TeaGreen -> if (dark) TeaGreenDark else TeaGreenLight
        LightGray -> if (dark) LightGrayDark else LightGrayLight
        Red -> if (dark) RedDark else RedLight
        LightPurple -> if (dark) LightPurpleDarkColorScheme else LightPurpleLightColorScheme
        Orange -> if (dark) OrangeDark else OrangeLight
        Yellow -> if (dark) YellowDark else YellowLight
    }

    /**
     * The colour of the picker's swatch: this accent's own primary.
     *
     * The light scheme supplies it, so every swatch is the same role and therefore directly
     * comparable, whatever the current theme is.
     */
    internal val swatch: Color get() = scheme(dark = false).primary

    companion object {
        /** The out-of-the-box accent: what a fresh install paints with. */
        val Default = Blue

        fun fromKey(key: String?): AccentPalette =
            entries.firstOrNull { it.key == key } ?: Default
    }
}
