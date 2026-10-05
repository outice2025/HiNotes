package com.hiapps.hinotes.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Resolves the active [ColorScheme].
 *
 * The appearance switches are independent, so they combine rather than override each other:
 * wallpaper-derived dynamic colour supplies the palette on Android 12+ when it is switched on,
 * otherwise the chosen accent palette does, and OLED dark then flattens that palette's surface
 * ramp to true black. Turning on OLED dark must not disable dynamic colour, and turning on
 * dynamic colour must not disable OLED dark.
 */
@Composable
private fun rememberColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    accent: AccentPalette,
    oledDark: Boolean,
): ColorScheme {
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return remember(darkTheme, dynamicColor, accent, oledDark, supportsDynamic) {
        val base = if (dynamicColor && supportsDynamic) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            accent.scheme(darkTheme)
        }
        // OLED dark is a dark-mode treatment: it only ever flattens a dark surface ramp.
        if (darkTheme && oledDark) base.toOled() else base
    }
}

/**
 * Flattens a scheme's surface ramp toward true black for OLED panels.
 *
 * Content colours (onSurface, primary, …) are untouched, so contrast is preserved; only the
 * container roles that would otherwise paint near-black pixels are pushed to black.
 */
internal fun ColorScheme.toOled(): ColorScheme = copy(
    surface = ColorTokens.Black,
    background = ColorTokens.Black,
    surfaceContainerLowest = ColorTokens.Black,
    surfaceContainerLow = ColorTokens.Oled.SurfaceContainerLow,
    surfaceContainer = ColorTokens.Oled.SurfaceContainer,
    surfaceContainerHigh = ColorTokens.Oled.SurfaceContainerHigh,
    surfaceContainerHighest = ColorTokens.Oled.SurfaceContainerHighest,
    surfaceVariant = ColorTokens.Oled.SurfaceContainerHighest,
)

/**
 * Applies the HiNotes Material 3 Expressive theme.
 *
 * Text uses the system font family (see `Typography.kt`); the icons are vector drawables rather
 * than an icon font.
 *
 * @param darkTheme resolved by the caller from the user preference + system setting.
 * @param dynamicColor use wallpaper-derived colours when the platform supports it.
 * @param accent the palette to paint with when dynamic colour is off.
 * @param oledDark flatten dark surfaces to true black.
 * @param fontScale app-wide type scale multiplier.
 * @param fontVariationWeight app-wide type weight.
 * @param contentScale scale applied to note content only (editor, preview, lists).
 * @param contentWeight weight applied to note content only.
 */
@Composable
fun HiNotesTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    accent: AccentPalette,
    oledDark: Boolean,
    fontScale: AppFontScale,
    fontVariationWeight: AppFontWeight,
    contentScale: AppFontScale,
    contentWeight: AppFontWeight,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = rememberColorScheme(context, darkTheme, dynamicColor, accent, oledDark)
    val typography = remember(fontScale, fontVariationWeight) {
        hinotesTypography(fontVariationWeight, fontScale.multiplier)
    }
    val motion = remember { MotionScheme.standard() }

    CompositionLocalProvider(
        LocalMotionScheme provides motion,
        LocalContentTypeScale provides contentScale,
        LocalContentTypeWeight provides contentWeight,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

/** Type scale applied to note content (editor body, previews, list snippets). */
val LocalContentTypeScale = staticCompositionLocalOf { AppFontScale.Default }

/** Type weight applied to note content. */
val LocalContentTypeWeight = staticCompositionLocalOf { AppFontWeight.Default }
