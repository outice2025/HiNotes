package com.hiapps.hinotes.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.hiapps.hinotes.R

/**
 * User-selectable type scale, from 0.8x to 1.5x in tenths.
 *
 * Levels are named by the multiplier they apply rather than against a device default, so the slider
 * reads the same on every phone, and the dialog labels each stop with that multiplier. The range
 * covers both "smaller than the device's own type" and large print, and no two neighbouring stops
 * are far enough apart to make the choice feel coarse. The multiplier applies on top of the
 * Material 3 scale.
 */
enum class AppFontScale(val multiplier: Float, val labelRes: Int) {
    Step08(0.8f, R.string.level_scale_08),
    Step09(0.9f, R.string.level_scale_09),
    Step10(1.0f, R.string.level_scale_10),
    Step11(1.1f, R.string.level_scale_11),
    Step12(1.2f, R.string.level_scale_12),
    Step13(1.3f, R.string.level_scale_13),
    Step14(1.4f, R.string.level_scale_14),
    Step15(1.5f, R.string.level_scale_15),
    ;

    companion object {
        val Default = Step10

        /**
         * Reads a stored key.
         *
         * The three names 1.0.8 wrote - `Small`, `Medium`, `Large` - are still understood, at the
         * stop nearest the multiplier they stood for (0.9, 1.0 and 1.15). A settings file written
         * by an older version, or a backup taken from one, therefore keeps the size the user chose
         * instead of quietly falling back to the default.
         */
        fun fromKey(key: String): AppFontScale =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) }
                ?: when (key.lowercase()) {
                    "small" -> Step09
                    "medium" -> Step10
                    "large" -> Step12
                    else -> Default
                }
    }
}

/**
 * User-selectable type weight, in four steps.
 *
 * The values map onto the weights the system sans-serif actually ships, so every stop renders a
 * genuinely different weight instead of asking the font engine to synthesise one.
 */
enum class AppFontWeight(val weight: FontWeight, val labelRes: Int) {
    Light(FontWeight.Light, R.string.level_light),
    Regular(FontWeight.Normal, R.string.level_regular),
    Medium(FontWeight.Medium, R.string.level_medium_weight),
    SemiBold(FontWeight.SemiBold, R.string.level_semibold),
    ;

    companion object {
        val Default = Regular

        fun fromKey(key: String): AppFontWeight =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Default
    }
}

/**
 * Builds the Material 3 type scale at the requested weight and scale.
 *
 * The font family is deliberately left at the platform default: HiNotes uses the system font
 * (Roboto on AOSP, the vendor's font elsewhere) for both the app and the editor, which is what
 * the design calls for and keeps the APK free of any bundled typeface. Icons are vector drawables
 * rather than a font, so nothing about the app's appearance depends on a shipped font file.
 *
 * Line height is kept at the M3 ratio (~1.5x for body styles), satisfying the 1.3-1.5x the
 * design asks for.
 */
fun hinotesTypography(weight: AppFontWeight, scale: Float): Typography {
    val base = Typography()
    val lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    fun TextStyle.fit(): TextStyle = copy(
        fontWeight = weight.weight,
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = lineHeightStyle,
    )

    return Typography(
        displayLarge = base.displayLarge.fit(),
        displayMedium = base.displayMedium.fit(),
        displaySmall = base.displaySmall.fit(),
        headlineLarge = base.headlineLarge.fit(),
        headlineMedium = base.headlineMedium.fit(),
        headlineSmall = base.headlineSmall.fit(),
        titleLarge = base.titleLarge.fit(),
        titleMedium = base.titleMedium.fit(),
        titleSmall = base.titleSmall.fit(),
        bodyLarge = base.bodyLarge.fit(),
        bodyMedium = base.bodyMedium.fit(),
        bodySmall = base.bodySmall.fit(),
        labelLarge = base.labelLarge.fit(),
        labelMedium = base.labelMedium.fit(),
        labelSmall = base.labelSmall.fit(),
    )
}
