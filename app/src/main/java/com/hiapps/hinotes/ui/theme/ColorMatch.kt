package com.hiapps.hinotes.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * A colour a surface can be painted with, and the colour that belongs on top of it.
 *
 * The two travel together because a Material role is only usable in pairs: `error` is a background
 * for `onError` and for nothing else, so a match that returned one without the other would leave
 * the caller guessing which content colour survives on it.
 */
internal data class ColorPair(val container: Color, val content: Color)

/**
 * The pair this scheme paints a filled button with that is closest to [reference].
 *
 * Roles are compared per channel in RGB. That is not a perceptual metric and does not need to be:
 * the pairs below sit far apart from one another - a mauve, a grey, a red, a near-white - so the
 * ranking they produce is the ranking an eye would give. Comparing the palette rather than naming a
 * role is also what keeps the choice the scheme's: the design palette answers with exactly the
 * reference red, a generated accent and a wallpaper-derived palette answer with their own red, and
 * the shipped light schemes are asserted to do so by `ThemeChecks`.
 *
 * Only the roles Material fills a button with are candidates. The surface ramp is deliberately left
 * out: a button painted in `surface` is a card, and an off-white would otherwise win a match that is
 * asking for a colour.
 */
internal fun ColorScheme.nearestPairTo(reference: Color): ColorPair =
    buttonPairs().minBy { it.container.distanceTo(reference) }

/** The scheme's button roles, each with the content colour that belongs on it. */
private fun ColorScheme.buttonPairs(): List<ColorPair> = listOf(
    ColorPair(primary, onPrimary),
    ColorPair(primaryContainer, onPrimaryContainer),
    ColorPair(secondary, onSecondary),
    ColorPair(secondaryContainer, onSecondaryContainer),
    ColorPair(tertiary, onTertiary),
    ColorPair(tertiaryContainer, onTertiaryContainer),
    ColorPair(error, onError),
    ColorPair(errorContainer, onErrorContainer),
    ColorPair(inverseSurface, inverseOnSurface),
)

/** Squared RGB distance: enough to rank colours that are this far apart. */
internal fun Color.distanceTo(other: Color): Float {
    val dr = red - other.red
    val dg = green - other.green
    val db = blue - other.blue
    return dr * dr + dg * dg + db * db
}
