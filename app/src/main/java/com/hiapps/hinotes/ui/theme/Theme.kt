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
 * The two appearance switches are independent, so they combine rather than override each other:
 * wallpaper-derived dynamic colour supplies the palette on Android 12+, and OLED dark then
 * flattens that palette's surface ramp to true black. Turning on OLED dark must not disable
 * dynamic colour, and turning on dynamic colour must not disable OLED dark.
 */
@Composable
private fun rememberColorScheme(
    context: Context,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    oledDark: Boolean,
): ColorScheme {
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return remember(darkTheme, dynamicColor, oledDark, supportsDynamic) {
        val dynamicUsable = dynamicColor && supportsDynamic
        when {
            // Dynamic colour, flattened to black surfaces when OLED dark is on.
            dynamicUsable && darkTheme && oledDark -> dynamicDarkColorScheme(context).toOled()
            dynamicUsable && darkTheme -> dynamicDarkColorScheme(context)
            dynamicUsable -> dynamicLightColorScheme(context)

            darkTheme && oledDark -> MonoOledDarkColorScheme
            darkTheme -> MonoDarkColorScheme
            else -> MonoLightColorScheme
        }
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
 * Text uses the system font family (see `Typography.kt`); only the icon font is bundled.
 *
 * @param darkTheme resolved by the caller from the user preference + system setting.
 * @param dynamicColor use wallpaper-derived colours when the platform supports it.
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
    oledDark: Boolean,
    fontScale: AppFontScale,
    fontVariationWeight: AppFontWeight,
    contentScale: AppFontScale,
    contentWeight: AppFontWeight,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = rememberColorScheme(context, darkTheme, dynamicColor, oledDark)
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
