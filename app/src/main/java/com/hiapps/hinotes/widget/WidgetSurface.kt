package com.hiapps.hinotes.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.hiapps.hinotes.ui.theme.distanceTo

/**
 * The dial colours a card's surface is matched to, one per mode.
 *
 * These are read off the clock widget the card is placed beside: a pale blue-white in light mode
 * and a desaturated slate in dark mode. They are what the card is asked to look like, so they are
 * held here rather than derived - nothing in the app or the scheme knows about that widget.
 */
private val DIAL_LIGHT = Color(0xFFEEF0FF)
private val DIAL_DARK = Color(0xFF2A3042)

/**
 * The surface a card is painted with: whichever of [scheme]'s surface roles sits nearest the dial.
 *
 * Naming the role rather than the colour keeps the choice the scheme's - a wallpaper-derived scheme
 * answers with its own nearest surface, exactly as the palette that generated it would. What it
 * answers for each palette the app ships is checked on a desktop JVM rather than assumed; see
 * tools/verify/ThemeChecks.kt.
 */
internal fun widgetSurface(scheme: ColorScheme, dark: Boolean): Color =
    scheme.surfaceRoles().minBy { it.distanceTo(if (dark) DIAL_DARK else DIAL_LIGHT) }

/**
 * The roles a card's surface may be painted with.
 *
 * `surfaceDim` and `surfaceBright` are the ends of the ramp the schemes define for a surface that
 * sits above or below the page, and `surfaceVariant` is the role older schemes carried where the
 * container ramp now sits: the app's own palettes give it a value one or two steps from the top of
 * that ramp rather than the same one, so it is a candidate in its own right and not a repeat of
 * `surfaceContainerHighest`.
 */
private fun ColorScheme.surfaceRoles(): List<Color> = listOf(
    surface,
    surfaceDim,
    surfaceBright,
    surfaceContainerLowest,
    surfaceContainerLow,
    surfaceContainer,
    surfaceContainerHigh,
    surfaceContainerHighest,
    surfaceVariant,
)
