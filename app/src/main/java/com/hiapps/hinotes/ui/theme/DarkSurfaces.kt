package com.hiapps.hinotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

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
 * The two adjustments the app makes to a dark scheme's surface ramp.
 *
 * They live apart from the theme composable because they are plain arithmetic on a scheme rather
 * than anything a composition needs: the widget runs the same two steps to paint a card that the
 * app runs to paint itself, and what they come to is checked on a desktop JVM - see
 * tools/verify/ThemeChecks.kt.
 */

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
 * Flattens a scheme's surface ramp toward true black for OLED panels.
 *
 * Content colours (onSurface, primary, …) are untouched, so contrast is preserved; the surface
 * roles - the page, the container ramp and that ramp's own ends, `surfaceBright` and `surfaceDim` -
 * are pushed to the black a panel like that is set to. Leaving the ends behind was what let a
 * widget, which chooses from the whole ramp, come out lighter than the app's own surfaces on a
 * phone with OLED dark switched on.
 */
internal fun ColorScheme.toOled(): ColorScheme = copy(
    surface = ColorTokens.Black,
    background = ColorTokens.Black,
    surfaceBright = ColorTokens.Black,
    surfaceDim = ColorTokens.Black,
    surfaceContainerLowest = ColorTokens.Black,
    surfaceContainerLow = ColorTokens.Oled.SurfaceContainerLow,
    surfaceContainer = ColorTokens.Oled.SurfaceContainer,
    surfaceContainerHigh = ColorTokens.Oled.SurfaceContainerHigh,
    surfaceContainerHighest = ColorTokens.Oled.SurfaceContainerHighest,
    surfaceVariant = ColorTokens.Oled.SurfaceContainerHighest,
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
