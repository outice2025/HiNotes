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
import androidx.compose.ui.graphics.Color
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
        // The lift is part of the app's dark look, so it is applied to a wallpaper-derived scheme
        // exactly as it is to a built-in one; OLED dark then flattens the whole ramp to black.
        val lifted = if (darkTheme) base.liftDarkSurfaces() else base
        if (darkTheme && oledDark) lifted.toOled() else lifted
    }
}

/**
 * How far each surface role is raised in dark mode, as the fraction of white mixed into it.
 *
 * Material 3's dark ramp starts at tone 6 (roughly #111318) and puts a card four tones above the
 * page. On a phone at low brightness that reads as almost pure black, which is what "the dark
 * background is too deep" means in practice. These fractions put the page on tone ~11 and a card
 * on tone ~19 - the ramp the style reference uses (#1A1B20 page, #2D3037 card) - while keeping
 * every role in the same order and leaving the light schemes untouched. They are deliberately
 * small: a surface is a large uniform field, so a few percent of white is a visible step.
 */
private const val LIFT_PAGE = 0.04f
private const val LIFT_LOWEST = 0.05f
private const val LIFT_LOW = 0.08f
private const val LIFT_CONTAINER = 0.04f
private const val LIFT_HIGH = 0.05f
private const val LIFT_HIGHEST = 0.03f

/**
 * Raises a dark scheme's surfaces off the tone-6 floor.
 *
 * Only the surface roles move: text, icons and accents keep the contrast Material 3 gave them,
 * and the *relative* order of the ramp is unchanged, so a card still sits above the page it is on
 * and the editor canvas still sits above the card. `surfaceVariant` is lifted with the rest so the
 * dividers and inactive tracks drawn from it do not stay behind.
 */
internal fun ColorScheme.liftDarkSurfaces(): ColorScheme = copy(
    surface = surface.lifted(LIFT_PAGE),
    background = background.lifted(LIFT_PAGE),
    surfaceContainerLowest = surfaceContainerLowest.lifted(LIFT_LOWEST),
    surfaceContainerLow = surfaceContainerLow.lifted(LIFT_LOW),
    surfaceContainer = surfaceContainer.lifted(LIFT_CONTAINER),
    surfaceContainerHigh = surfaceContainerHigh.lifted(LIFT_HIGH),
    surfaceContainerHighest = surfaceContainerHighest.lifted(LIFT_HIGHEST),
    surfaceVariant = surfaceVariant.lifted(LIFT_CONTAINER),
)

/**
 * [this] colour mixed [fraction] of the way toward white.
 *
 * Blending toward white raises a dark colour's tone without touching its hue, which is why it is
 * used here rather than a hand-picked replacement per palette: the same rule then holds for a
 * wallpaper-derived dark scheme, whose colours are not known until the app runs.
 */
private fun Color.lifted(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha,
)

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
        // No colour role can answer "is this theme dark?": the error roles swap places between
        // the modes, so a component that wants the same *effect* in both has to know which mode
        // it is in rather than which role it is reading.
        LocalDarkTheme provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content,
        )
    }
}

/** True while the app is painting its dark scheme. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Type scale applied to note content (editor body, previews, list snippets). */
val LocalContentTypeScale = staticCompositionLocalOf { AppFontScale.Default }

/** Type weight applied to note content. */
val LocalContentTypeWeight = staticCompositionLocalOf { AppFontWeight.Default }
