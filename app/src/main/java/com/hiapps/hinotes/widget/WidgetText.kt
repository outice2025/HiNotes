package com.hiapps.hinotes.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ImageSpan
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
 * The note text a widget shows.
 *
 * The widget renders the note with the app's own Markdown parser when Markdown is switched on, so
 * a heading, a bold run, a bullet and a ticked checkbox read on the home screen the way they read
 * in the editor's preview; with Markdown off the note's own characters are shown, which is what a
 * plain text note expects. The parser hands back Compose-styled text, so it is converted to the
 * `Spanned` a RemoteViews can actually carry - the platform's own span objects, which survive the
 * trip to the launcher - and the two parts of a note that are a picture rather than a character, a
 * task's box and a picture the note holds, are drawn into that text where the parser left a mark for
 * them. The body a card is drawn from is cut into the list's items by [WidgetChunks].
 */
internal object WidgetText {

    /** The body size the widget's layout declares; the parser's heading sizes are relative to it. */
    private const val BODY_SP = 14f

    /**
     * A task's box, in dp, at the default type scale.
     *
     * 18dp is the box Material 3 draws a checkbox at, so a checklist on the home screen holds the
     * same box the editor's preview does; it grows with the body's type size, because a box that
     * stayed at one size would crowd a line of a note set large and float in one set small.
     */
    private const val BOX_DP = 18f

    /**
     * The characters the parser writes a task's box as, and the pair whose pictures replace them.
     *
     * With Markdown on, a task is rendered as `☑` or `☐` followed by the gap that separates it from
     * the item's text. Both are swapped for a drawn box where one can be made - see [WidgetBoxes] -
     * and left as the characters where it cannot, which is why they are looked for rather than
     * counted: the note's text must survive whatever happens to the pictures.
     */
    private const val BOX_CHECKED = '☑'
    private const val BOX_UNCHECKED = '☐'

    /**
     * The character a picture in the note is drawn as, before the picture itself is put in its place.
     *
     * The renderer cannot put a picture into a line of text, so a line that holds one is rendered as
     * this single character - the object replacement character, which is what a text engine
     * substitutes for something it has no glyph for - and the address that character stands for
     * travels with it (see [Markdown.IMAGE_TAG]). One character is also what keeps a picture whole
     * when the body is cut into chunks for the list: a mark is never split, and neither is the
     * address it is drawn from.
     */
    private const val IMAGE_MARK = '\uFFFC'

    /**
     * The note's body as the items the widget's list draws.
     *
     * A task's box is drawn at [boxPx], the size of the font the body is drawn in, and a picture is
     * drawn where [image] answers with one for the address the text carries. A body without Markdown
     * holds neither a task line nor a picture, so nothing there asks for either. A picture in the
     * text is drawn at the display's own density rather than at the 160dpi a bitmap is created or
     * decoded at - a box is stamped with it where [WidgetBoxes] draws it, and a note's own picture
     * where the caller decodes it - because a span scales what it is given by that density.
     *
     * [taskLines] are the source lines of the note's tasks: the lines that become items of their own,
     * with their boxes drawn beside the text rather than inside it. A caller that cannot draw a box
     * passes none, and the boxes stay in the text as the characters they are - see [WidgetChunks].
     */
    fun body(
        content: String,
        markdown: Boolean,
        boxPx: Int,
        style: WidgetStyle,
        image: (String) -> Bitmap?,
        taskLines: List<Int> = emptyList(),
    ): List<WidgetChunk> {
        if (!markdown) return WidgetChunks.chunks(content)
        // A picture is drawn as the single character the text has room for in its place, and the
        // address it stands for travels with that character as an annotation - see
        // [Markdown.IMAGE_TAG] - so the two cannot come apart however the note is written.
        val spanned = Markdown.render(content, imageMark = IMAGE_MARK).toSpanned(boxPx, style, image)
        return WidgetChunks.chunks(spanned, taskLines)
    }

    /** The body's size in sp, at the scale the widget's own type setting asks for. */
    fun bodySizeSp(scale: AppFontScale): Float = BODY_SP * scale.multiplier

    /**
     * A task's box in device pixels, at the same scale.
     *
     * The view is handed the body's size through a RemoteViews, which takes sp and scales it against
     * the launcher's own display; a picture cannot be scaled that way, so the size it is drawn at is
     * worked out here from the same setting.
     */
    fun boxSizePx(context: Context, scale: AppFontScale): Int =
        WidgetBoxes.sizePx(context, BOX_DP * scale.multiplier)

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
     * larger heading sizes - and each becomes a `ParcelableSpan`, because anything else is dropped
     * on the way to the launcher. Colours are deliberately not carried over: the widget's own
     * scheme decides those.
     *
     * A task's box and a picture are the parts of the text that are not characters: a box becomes
     * the drawn checkbox from [WidgetBoxes], and a picture is drawn where [image] answers with one
     * for the address its place carries. Both travel as the exception to the rule above, being the
     * spans a widget can show that carry a picture: the bitmap is written into the parcel and the
     * launcher draws from the copy it reads back, which is why both are bitmaps to begin with. Each
     * bitmap is drawn at the display's own density, which the style carries for [WidgetBoxes] and
     * which the picture's own decoder has to be told separately.
     */
    private fun AnnotatedString.toSpanned(
        boxPx: Int,
        style: WidgetStyle,
        image: (String) -> Bitmap?,
    ): CharSequence {
        val out = SpannableStringBuilder(text)
        spanStyles.forEach { range ->
            val spanStyle = range.item
            val from = range.start
            val to = range.end
            if (to <= from) return@forEach

            // SemiBold is the parser's heading weight and Bold its emphasis; a widget has one
            // bold face, so both land on it.
            if ((spanStyle.fontWeight?.weight ?: 0) >= FontWeight.SemiBold.weight) {
                out.setSpan(StyleSpan(Typeface.BOLD), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanStyle.fontStyle == FontStyle.Italic) {
                out.setSpan(StyleSpan(Typeface.ITALIC), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanStyle.textDecoration?.contains(TextDecoration.LineThrough) == true) {
                out.setSpan(StrikethroughSpan(), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (spanStyle.fontFamily == FontFamily.Monospace) {
                out.setSpan(TypefaceSpan("monospace"), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val size = spanStyle.fontSize
            if (size.isSpecified && size.value > BODY_SP) {
                out.setSpan(
                    RelativeSizeSpan(size.value / BODY_SP),
                    from,
                    to,
                    SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
        // The places the renderer left for a picture, each with the address it stands for.
        return out.withMarks(boxPx, style, image, getStringAnnotations(Markdown.IMAGE_TAG, 0, length))
    }

    /**
     * [this] with every task box and every picture drawn rather than written.
     *
     * A box is found by its own character, because the two characters are exactly what the renderer
     * writes a task as - and a note that happens to spell one out is asking for a box too. Each mark
     * gets a span of its own: the text layout writes the size it measured into the span, so two marks
     * sharing one would take turns overwriting what the other is drawn at, while the bitmaps behind
     * them are shared and a span holds only one.
     *
     * Nothing here can cost a note its text. A box that cannot be drawn keeps the character the note
     * was rendered with, which is still a box a reader recognises; a picture that cannot be drawn
     * gives up the single character that stood in for it, and the note reads on without a gap of
     * anything that was written.
     */
    private fun SpannableStringBuilder.withMarks(
        boxPx: Int,
        style: WidgetStyle,
        image: (String) -> Bitmap?,
        pictures: List<AnnotatedString.Range<String>>,
    ): CharSequence {
        // Pictures first, from the back: a mark whose picture cannot be drawn is taken out of the
        // text, and taking it out from the back leaves every place before it where the renderer put
        // it. Each mark is replaced where the renderer said it is rather than counted out, so a note
        // with one picture above another keeps each one with its own address.
        pictures.sortedByDescending { it.start }.forEach { range ->
            val end = range.end.coerceAtMost(length)
            if (end <= range.start) return@forEach
            val bitmap = image(range.item)
            if (bitmap == null) delete(range.start, end) else placeMark(bitmap, range.start, end)
        }

        // Boxes are spans rather than edits, so they are placed over the text as it now stands.
        val boxes = WidgetBoxes.boxes(boxPx, style.densityDpi, style)
        for (i in length - 1 downTo 0) {
            val bitmap = when (this[i]) {
                BOX_CHECKED -> boxes?.checked
                BOX_UNCHECKED -> boxes?.unchecked
                else -> null
            } ?: continue
            placeMark(bitmap, i, i + 1)
        }
        return this
    }

    /**
     * Draws [bitmap] in place of the text from [from] up to [to].
     *
     * The constructor that takes a bitmap and an alignment is deprecated in favour of one that takes
     * a context as well, and that form arrived in API 29 while this app still supports 24. The
     * deprecated call is the one every supported version honours and it is the only one that accepts
     * a picture drawn here rather than a resource, so it stays.
     *
     * Centring is what both marks are drawn with: a box is a little taller than the text it sits in,
     * so centring keeps it inside the line's own height instead of pushing its bottom below the
     * baseline, and a picture takes the line it is on as its own row either way.
     *
     * The span draws [bitmap] at its pixel size only because the bitmap says which density those
     * pixels are for: a `BitmapDrawable` scales what it is given by the display's density over the
     * bitmap's own, so a bitmap left at the 160dpi a fresh one is created with is drawn at almost
     * three times its size on a modern phone. Every caller sets that density, and this is the one
     * place both kinds of mark arrive, so it is written down here.
     */
    @Suppress("DEPRECATION")
    private fun SpannableStringBuilder.placeMark(bitmap: Bitmap, from: Int, to: Int) {
        setSpan(ImageSpan(bitmap, ImageSpan.ALIGN_CENTER), from, to, SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}
