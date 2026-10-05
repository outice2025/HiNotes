package com.hiapps.hinotes.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a Material Symbols Rounded glyph.
 *
 * The glyphs ship as vector drawables rather than as a font, which is a deliberate choice. An
 * icon font is subject to the platform's text layout, and this one made that visible: its
 * artwork fills only the middle 720 of a 960-unit em box, and its `hhea` metrics are asymmetric
 * (1056 ascent against a 96 descent), so glyphs rendered both undersized and low, and the exact
 * baseline placement could not be reproduced from the font tables. As vectors the artwork sits
 * exactly where the generator put it, identically on every API level.
 *
 * @param codepoint a `sym_*` drawable from [Symbols]. The parameter keeps its original name so
 *   call sites did not have to change when the font was replaced by vectors.
 */
@Composable
fun SymbolIcon(
    @DrawableRes codepoint: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = codepoint),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

/**
 * Glyph sizes for the Material 3 icon-button sizes, used so an icon scales with its button.
 *
 * XS 32dp / S 40dp / M 56dp / L 96dp / XL 136dp buttons carry 20/24/24/32/40dp glyphs.
 */
object SymbolSizes {
    val XSmall: Dp = 20.dp
    val Small: Dp = 24.dp
    val Medium: Dp = 24.dp
    val Large: Dp = 32.dp
    val XLarge: Dp = 40.dp
}
