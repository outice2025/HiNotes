package com.hiapps.hinotes.widget

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import androidx.annotation.LayoutRes
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.isSpecified
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.editor.Markdown
import com.hiapps.hinotes.ui.theme.AppFontScale
import com.hiapps.hinotes.ui.theme.AppFontWeight

/**
 * The note text a widget shows, and the chunks its scrolling list is built from.
 *
 * The widget renders the note with the app's own Markdown parser when Markdown is switched on, so
 * a heading, a bold run, a bullet and a ticked checkbox read on the home screen the way they read
 * in the editor's preview; with Markdown off the note's own characters are shown, which is what a
 * plain text note expects. The parser hands back Compose-styled text, so it is converted to the
 * `Spanned` a RemoteViews can actually carry - the platform's own span objects, which survive the
 * trip to the launcher.
 */
internal object WidgetText {

    /** The body size the widget's layout declares; the parser's heading sizes are relative to it. */
    private const val BODY_SP = 14f

    /**
     * Source lines per chunk.
     *
     * A collection widget is a list, so "scroll for more" is the list itself: one item per chunk,
     * requested as it comes into view. Ten lines keeps the list cheap to build and each item short
     * enough that a swipe moves a visible amount through the note; the size the card is placed at is
     * the host's business, and a small card simply shows fewer items at a time.
     */
    private const val CHUNK = 10

    /**
     * A ceiling on a chunk's length as well as its line count.
     *
     * A note is not obliged to contain a line break, and one long paragraph would otherwise be a
     * single item taller than the widget - and a list cannot scroll inside an item, so the rest of
     * the note would be unreachable. Cutting at a character count as well keeps every chunk
     * scrollable; the cut lands mid-sentence, which in flowing text is simply where the next item
     * begins.
     */
    private const val CHUNK_CHARS = 600

    /** The note's body as the widget shows it: rendered when Markdown is on, verbatim when not. */
    fun body(content: String, markdown: Boolean): CharSequence =
        if (markdown) Markdown.render(content).toSpanned() else content

    /** The body's size in sp, at the scale the widget's own type setting asks for. */
    fun bodySizeSp(scale: AppFontScale): Float = BODY_SP * scale.multiplier

    /**
     * The item layout that draws the body at [weight].
     *
     * A RemoteViews cannot be handed a `Typeface`, so a weight cannot be set on the view at runtime:
     * there is one item layout per stop instead, and each names the style for the weight it draws -
     * see `values/styles.xml`, and its `values-v28` variant for the stops a text style cannot
     * express on its own.
     */
    @LayoutRes
    fun bodyLayout(weight: AppFontWeight): Int = when (weight) {
        AppFontWeight.Light -> R.layout.widget_item_light
        AppFontWeight.Regular -> R.layout.widget_item_regular
        AppFontWeight.Medium -> R.layout.widget_item_medium
        AppFontWeight.SemiBold -> R.layout.widget_item_semibold
    }

    /** [body] cut into chunks, with every span kept in the chunk it covers. */
    fun chunks(body: CharSequence): List<CharSequence> {
        if (body.isEmpty()) return emptyList()

        val out = mutableListOf<CharSequence>()
        var start = 0
        var lines = 0
        for (i in 0 until body.length) {
            if (body[i] == '\n') {
                lines++
                if (lines == CHUNK) {
                    out += slice(body, start, i)
                    start = i + 1
                    lines = 0
                }
            } else if (i - start >= CHUNK_CHARS) {
                out += slice(body, start, i)
                start = i
                lines = 0
            }
        }
        if (start < body.length) out += slice(body, start, body.length)
        // A chunk of blank lines would be an empty row with a gap above and below it.
        return out.filter { it.isNotBlank() }
    }

    /**
     * [source] from [start] up to [end], as its own copy.
     *
     * The text is copied and the spans come with it: `SpannableStringBuilder`'s ranged constructor
     * copies the spans that fall inside the range. A span that *straddles* the range's edge is
     * dropped rather than clamped, which is why chunks are cut at line boundaries: only the
     * character-count cut can land mid-span, and it can only cost the styling of that one run.
     */
    private fun slice(source: CharSequence, start: Int, end: Int): CharSequence =
        if (source is Spanned) {
            SpannableStringBuilder(source, start, end)
        } else {
            source.subSequence(start, end)
        }

    /**
     * The parser's Compose-styled text as the platform's own spanned text.
     *
     * Only the styles a widget can draw are mapped - bold, italic, strikethrough, monospace and the
     * larger heading sizes - and each becomes a `ParcelableSpan`, because anything else is dropped
     * on the way to the launcher. Colours are deliberately not carried over: the widget's own
     * scheme decides those.
     */
    private fun AnnotatedString.toSpanned(): CharSequence {
        val out = SpannableStringBuilder(text)
        spanStyles.forEach { range ->
            val style = range.item
            val from = range.start
            val to = range.end
            if (to <= from) return@forEach

            // SemiBold is the parser's heading weight and Bold its emphasis; a widget has one
            // bold face, so both land on it.
            if ((style.fontWeight?.weight ?: 0) >= FontWeight.SemiBold.weight) {
                out.setSpan(StyleSpan(Typeface.BOLD), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.fontStyle == FontStyle.Italic) {
                out.setSpan(StyleSpan(Typeface.ITALIC), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.textDecoration?.contains(TextDecoration.LineThrough) == true) {
                out.setSpan(StrikethroughSpan(), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (style.fontFamily == FontFamily.Monospace) {
                out.setSpan(TypefaceSpan("monospace"), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val size = style.fontSize
            if (size.isSpecified && size.value > BODY_SP) {
                out.setSpan(
                    RelativeSizeSpan(size.value / BODY_SP),
                    from,
                    to,
                    SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        return out
    }
}
