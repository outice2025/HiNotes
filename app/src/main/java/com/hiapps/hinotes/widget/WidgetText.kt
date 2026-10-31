package com.hiapps.hinotes.widget

import android.content.Context
import android.graphics.Paint
import android.text.SpannableStringBuilder
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
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.editor.Markdown
import com.hiapps.hinotes.ui.theme.AppFontScale
import com.hiapps.hinotes.ui.theme.AppFontWeight
import kotlin.math.roundToInt

/**
 * The note text a widget shows.
 *
 * The widget renders the note with the app's own Markdown parser when Markdown is switched on, so a
 * heading, a bold run, a bullet and a ticked checkbox read on the home screen the way they read in
 * the editor's preview; with Markdown off the note's own characters are shown, which is what a plain
 * text note expects. The parser hands back Compose-styled text, so it is converted to the `Spanned` a
 * RemoteViews can actually carry - the platform's own span objects, which survive the trip to the
 * launcher - and the body a card is drawn from is cut into the list's items by [WidgetChunks].
 *
 * A picture is the one thing this does not put into the text. A span carrying a bitmap is not a
 * `ParcelableSpan`, so it is dropped on the way to the launcher and the note arrived there as the
 * single character the renderer had left in its place - "OBJ" on the home screen. The picture is
 * therefore drawn by an item of its own, with an `ImageView` the card fills in: see
 * [WidgetChunk.Picture] and `widget_item_regular.xml`.
 */
internal object WidgetText {

    /** The body size the widget's layout declares; the parser's heading sizes are relative to it. */
    private const val BODY_SP = 14f

    /**
     * A task's box, in dp.
     *
     * 20dp is the square Material 3 fills and outlines for a checkbox - the same box the app draws
     * on a card and in the editor's reading mode - and it is the same length at every type size,
     * exactly as the composed checkbox is: a checklist on the home screen has to read as the one in
     * the app, not as a picture of it that grew with the body's text.
     */
    private const val BOX_DP = 20f

    /**
     * The character a picture in the note is drawn as, before the list turns it into an item.
     *
     * The renderer cannot put a picture into a line of text, so a line that holds one is rendered as
     * this single character - the object replacement character, which is what a text engine
     * substitutes for something it has no glyph for - and the address that character stands for
     * travels with it as an annotation (see [Markdown.IMAGE_TAG]). One character is also what keeps
     * a picture whole when the body is cut into chunks for the list: a mark is never split, and
     * neither is the address it is drawn from.
     */
    private const val IMAGE_MARK = '\uFFFC'

    /**
     * The note's body as the items the widget's list draws.
     *
     * [drawBoxes] is whether a task's box can be drawn beside its text. A caller that cannot build
     * the box pictures passes false and the boxes stay in the text as the characters the renderer
     * wrote - a checklist with nothing to tap reads better than one with no boxes at all - see
     * [WidgetChunks].
     */
    fun body(
        content: String,
        markdown: Boolean,
        drawBoxes: Boolean = true,
    ): List<WidgetChunk> {
        if (!markdown) return WidgetChunks.chunks(content)
        // A picture is drawn as the single character the text has room for in its place, and the
        // address it stands for travels with that character as an annotation - see
        // [Markdown.IMAGE_TAG] - so the two cannot come apart however the note is written.
        val rendered = Markdown.renderMarks(content, imageMark = IMAGE_MARK)
        val pictures = rendered.text
            .getStringAnnotations(Markdown.IMAGE_TAG, 0, rendered.text.length)
            .filter { it.end == it.start + 1 }
            .associate { it.start to it.item }
        // The boxes the renderer drew are the ones the list turns into items, so a line it did not
        // draw a box for - a list bullet, or a "[ ]" inside a code block - is never offered as a
        // control. See Markdown.TaskMark for why the renderer reports them rather than a second
        // reading of the note.
        val marks = if (drawBoxes) rendered.marks else emptyList()
        return WidgetChunks.chunks(rendered.text.toSpanned(), marks, pictures)
    }

    /** The body's size in sp, at the scale the widget's own type setting asks for. */
    fun bodySizeSp(scale: AppFontScale): Float = BODY_SP * scale.multiplier

    /**
     * The leading the four item layouts set the body with, declared here as well.
     *
     * The `widget_item_*` layouts carry it as `lineSpacingMultiplier` and `lineSpacingExtra`, because
     * that is where a RemoteViews can have it; the box picture beside a task line has to be laid out
     * in the same band, and a picture cannot ask a layout it is not part of what that layout's line
     * is. The two therefore have to agree - see [lineHeightPx].
     */
    private const val LINE_SPACING_MULTIPLIER = 1.4f
    private const val LINE_SPACING_EXTRA_DP = 4f

    /**
     * The face's own height, as a fraction of the size it is set at.
     *
     * A line is not as tall as its text size: the platform lays a line out from the face's ascent and
     * descent, which are wider apart than the size names, and the layout's spacing multiplier applies
     * to that. Asking the platform's own face for its proportions once gives the number this file
     * cannot write down as a constant - it is the same reason [MarkdownText] measures its code panel
     * from the font's metrics rather than from the size.
     */
    private val faceHeight: Float by lazy {
        val metrics = Paint().apply { textSize = 1f }.fontMetrics
        metrics.descent - metrics.ascent
    }

    /**
     * One line of the widget's body, in device pixels.
     *
     * This is the height of the band a task's box is drawn in: the box's picture is pinned to the top
     * of the row (see the item layouts), so the band is what puts the box on the item's first line.
     * The text is set in sp by the launcher, so the system's font scale is part of the size, exactly
     * as it is on screen; the extra spacing is a dp length and does not follow it.
     *
     * A row also keeps a gap above and below its text (`WidgetItems` adds it to the text view); the
     * caller adds both to this to get the band the picture covers, because that padding is inside the
     * row and the picture is not.
     */
    fun lineHeightPx(context: Context, sizeSp: Float): Int {
        val display = context.resources.displayMetrics
        val textPx = sizeSp * context.resources.configuration.fontScale * display.density
        return (textPx * faceHeight * LINE_SPACING_MULTIPLIER + LINE_SPACING_EXTRA_DP * display.density)
            .roundToInt()
            .coerceAtLeast(1)
    }

    /**
     * A task's box in device pixels.
     *
     * The view is handed the body's size through a RemoteViews, which takes sp and scales it against
     * the launcher's own display; a picture cannot be scaled that way, so the square it is drawn at
     * is worked out here. It does not follow the widget's type setting, because the composed
     * checkbox it has to match does not follow the note's either.
     */
    fun boxSizePx(context: Context): Int = WidgetBoxes.sizePx(context, BOX_DP)

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

    /**
     * The parser's Compose-styled text as the platform's own spanned text.
     *
     * Only the styles a widget can draw are mapped - bold, italic, strikethrough, monospace and the
     * heading ranks - and each becomes a `ParcelableSpan`, because anything else is dropped on the
     * way to the launcher. Colours are deliberately not carried over: the widget's own scheme
     * decides those. A formula's own panel and a link's colour are dropped with them, which is why
     * a formula reads as monospace on the home screen and a link as plain text.
     *
     * A heading is marked with its level rather than with a size, because the size is a multiple of
     * whatever body the text is drawn in - see [Markdown.HEADING_SCALES]. The widget applies the
     * same multiple to its own body size, so its headings keep the rank they have on the page.
     */
    private fun AnnotatedString.toSpanned(): CharSequence {
        val out = SpannableStringBuilder(text)
        getStringAnnotations(Markdown.HEADING_TAG, 0, length).forEach { annotation ->
            val level = annotation.item.toIntOrNull() ?: return@forEach
            val scale = Markdown.HEADING_SCALES.getOrNull(level - 1) ?: return@forEach
            out.setSpan(
                RelativeSizeSpan(scale.size),
                annotation.start,
                annotation.end,
                SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        spanStyles.forEach { range ->
            val spanStyle = range.item
            val from = range.start
            val to = range.end
            if (to <= from) return@forEach

            // SemiBold is the parser's heading weight and Bold its emphasis; a widget has one
            // bold face, so both land on it.
            if ((spanStyle.fontWeight?.weight ?: 0) >= FontWeight.SemiBold.weight) {
                out.setSpan(StyleSpan(android.graphics.Typeface.BOLD), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanStyle.fontStyle == FontStyle.Italic) {
                out.setSpan(StyleSpan(android.graphics.Typeface.ITALIC), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true) {
                out.setSpan(StrikethroughSpan(), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanStyle.fontFamily == FontFamily.Monospace) {
                out.setSpan(TypefaceSpan("monospace"), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return out
    }
}
