package com.hinotes.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.hinotes.app.R

/**
 * User-selectable type scale, in three steps.
 *
 * Levels are named by ordinal (small → large) rather than against a device default, so the
 * slider reads the same on every phone. The multiplier applies on top of the Material 3 scale.
 */
enum class AppFontScale(val multiplier: Float, val labelRes: Int) {
    Small(0.9f, R.string.level_small),
    Medium(1.0f, R.string.level_medium),
    Large(1.15f, R.string.level_large),
    ;

    companion object {
        val Default = Medium

        fun fromKey(key: String): AppFontScale =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: Default
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
 * the design calls for and keeps the APK free of a bundled text face. Only the icon font is
 * bundled, because Material Symbols has no system equivalent.
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
