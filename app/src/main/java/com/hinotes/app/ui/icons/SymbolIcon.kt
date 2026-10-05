package com.hinotes.app.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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

// Two facts about this font drive the geometry below, and both are measurable.

/**
 * Material Symbols draws its artwork inside the middle 720 of a 960-unit em box, so the visible
 * glyph is 75% of the requested font size. Asking for a 24dp glyph therefore needs a 32dp font
 * size; without this the icon renders a quarter smaller than everything around it.
 *
 * Confirmed against a device screenshot: an uncompensated 24dp icon measured 47px of ink on a
 * 40dp badge (45%), where this model predicts 47.2px.
 */
private const val GLYPH_TO_EM = 720f / 960f

/**
 * How far below its frame the ink sits, as a fraction of the font size, when the text is centred
 * by line box.
 *
 * This font's `hhea` metrics are asymmetric - 1056 ascent against a 96 descent - and Android
 * lays the baseline out against those rather than against the em box, so a glyph centred by line
 * box lands low. The value is calibrated from a device screenshot (6.5px at a 24dp font size on
 * a 2.625 density screen) rather than derived, because the exact baseline placement could not be
 * reproduced from the font tables alone.
 */
private const val BASELINE_DROP = 0.1033f

/**
 * Draws a Material Symbols glyph, sized to [size] and centred in a [size] square.
 *
 * Two compensations keep the glyph on the frame's centre:
 *
 *  * the font size is scaled up by [GLYPH_TO_EM] so the *ink* measures [size], not the em box;
 *  * the text is lifted by [BASELINE_DROP] of the font size, undoing the low baseline.
 *
 * Both were confirmed against a device screenshot; see the constants above.
 */
@Composable
fun SymbolIcon(
    codepoint: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    val fontSize = size.value / GLYPH_TO_EM
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = String(Character.toChars(codepoint)),
            color = tint,
            fontFamily = MaterialSymbolsRounded,
            fontSize = fontSize.sp,
            lineHeight = fontSize.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.None,
                ),
            ),
            modifier = Modifier.graphicsLayer {
                // Negative lifts the ink back onto the centre line.
                translationY = -BASELINE_DROP * fontSize * density
            },
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
