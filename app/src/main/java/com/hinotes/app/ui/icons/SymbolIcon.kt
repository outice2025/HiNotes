package com.hinotes.app.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hinotes.app.R

/**
 * Material Symbols Rounded, bundled as a subset font so the icon set is authentic, available
 * offline and identical on every device.
 *
 * The shipped font carries FILL=0 (outlined) at weight 400, which is the outlined Material
 * Symbols style the design calls for.
 */
val MaterialSymbolsRounded: FontFamily = FontFamily(
    Font(R.font.material_symbols_rounded, FontWeight.Normal),
)

/**
 * Draws a Material Symbols glyph.
 *
 * The glyph is rendered from the symbol font by codepoint (see [Symbols]) rather than from a
 * vector drawable, inside a square [size] frame that centres it. The frame and the glyph are
 * both square and the glyph fills its em box, so plain centring lands the ink on the frame's
 * centre - no baseline compensation is applied or wanted.
 */
@Composable
fun SymbolIcon(
    codepoint: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = String(Character.toChars(codepoint)),
            color = tint,
            fontFamily = MaterialSymbolsRounded,
            fontSize = size.value.sp,
            lineHeight = size.value.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
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
