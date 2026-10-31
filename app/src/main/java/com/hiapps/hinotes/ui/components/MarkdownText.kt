package com.hiapps.hinotes.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiapps.hinotes.ui.editor.Markdown

/** The corner a panel behind a run of code or a formula is drawn with. */
private val PanelCorner = 6.dp

/** How far a panel reaches past the run it sits behind, horizontally. */
private val PanelPadding = 4.dp

/** How far a panel reaches above and below the run's own box. */
private val PanelInset = 2.dp

/** The size text falls back to when the style it is drawn in names none. */
private val FallbackTextSize = 14.sp

/**
 * The grey a formula sits on, at a fifth of its opacity: readable on the light theme and the dark
 * one alike, and quiet enough that a formula still reads as part of the sentence it is written in.
 */
private val MathBackground = Color(0x33808080)

/**
 * A rendered run of Markdown.
 *
 * A `Text` draws a span's background as a rectangle, and Material 3 gives it no corner radius, so
 * the rounded panel a run of code - or a formula - sits on is painted here instead: the renderer
 * marks every such range it wrote (see [Markdown.CODE_TAG] and [Markdown.MATH_TAG]) and this draws
 * each of them from the text layout the field has just produced. A fenced block is one panel - its
 * lines are merged into a single rounded rectangle running from the first code line's baseline to
 * the last one's - while a span inside a sentence is a box per line, which is what it is when it
 * wraps.
 *
 * The panel is the height of the run's *own* font box, measured from the line's baseline, and not
 * the height of the line. A line is taller than the text standing on it - the paragraph sets a line
 * height, and a line holding Chinese takes its ascent from the fallback face, which is taller than
 * the monospace one - while the glyphs hang from a baseline part-way down it. A background drawn
 * from the line's own top and bottom therefore left a band of colour above the text and let its
 * descenders cross the lower edge, which read as a background that had slipped off its text; the
 * platform's own span background, which a text field draws, is the height of the run's face and
 * stays on the glyphs, and that is the look this matches.
 *
 * Links are the other thing the text cannot finish by itself: the renderer leaves them unstyled and
 * this colours them, because a link that looks like the prose around it is a link nobody taps.
 *
 * A heading's size is not written into the text it was rendered from: the renderer marks the run
 * with its level (see [Markdown.HEADING_TAG]) and this scales the level against the style the text
 * is drawn in, so a reader's type size moves the heading with the body instead of leaving a fixed
 * step behind. A code panel inside a heading measures the scaled size the same way.
 *
 * @param codeBackground the colour a run of code sits on.
 * @param mathBackground the colour a formula sits on; a formula's panel is drawn here because a
 *   span's own background has square corners.
 * @param onTextLayout called with the layout this text produced, after the internal one is kept;
 *   a page that has to scroll to a match asks it where the match's text sits.
 */
@Composable
fun MarkdownText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    mathBackground: Color = MathBackground,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    // Headings are drawn at a multiple of the body style rather than at a size of their own, and
    // the multiple can only be folded in here, where both the style and the spans are in hand.
    val shown = remember(text, style) { text.withHeadingScales(style) }
    // Every panel this text asks for, in the order the renderer wrote them. Code and formulas are
    // marked apart, because they are drawn on different colours, and the two lists are merged into
    // one so the drawing below is one pass over one shape.
    val panels = remember(shown, codeBackground, mathBackground) {
        shown.runs(Markdown.CODE_TAG, Markdown.CODE_BLOCK)
            .map { Panel(it.start, it.end, it.block, codeBackground) } +
            shown.runs(Markdown.MATH_TAG, Markdown.MATH_BLOCK)
                .map { Panel(it.start, it.end, it.block, mathBackground) }
    }
    val density = LocalDensity.current
    val corner = with(density) { PanelCorner.toPx() }
    val padding = with(density) { PanelPadding.toPx() }
    val inset = with(density) { PanelInset.toPx() }
    val baseSize = style.fontSize.takeIf { it.isSp } ?: FallbackTextSize
    // How far the run's own face reaches above and below the baseline it is written on, as a
    // fraction of the size it is set at. It is asked of the platform's monospace face once, because
    // that is the face both a run of code and a formula are written in and its proportions do not
    // change with the size; what does change is the size, which a heading gives its own runs.
    val metrics = remember { Paint().apply { typeface = Typeface.MONOSPACE; textSize = 1f }.fontMetrics }
    val ascent = -metrics.ascent
    val descent = metrics.descent

    Text(
        text = shown,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        onTextLayout = { result ->
            layout = result
            onTextLayout?.invoke(result)
        },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            // Every offset below is clamped to the text the layout itself was made from. The two are
            // the same text in the ordinary run of things - the layout is the one this node has just
            // produced - but a panel is painted from ranges the renderer wrote into a string, and a
            // frame in which the layout and the string disagree has to lose a panel at worst rather
            // than ask the layout about an offset it does not have.
            val total = minOf(result.layoutInput.text.length, shown.length)
            // And what is drawn is what is on screen: a card shows a few lines of a note and cuts
            // the rest, and a range below that cut belongs to lines the layout never kept - asking
            // where one of those sits answers with the last line it does have, which drew a panel
            // across a line of prose that held none at all.
            val lastDrawnLine = result.lineCount - 1
            panels.forEach { panel ->
                val firstLine = result.getLineForOffset(panel.start.coerceIn(0, total))
                if (firstLine > lastDrawnLine) return@forEach
                val lastLine = result
                    .getLineForOffset((panel.end - 1).coerceIn(0, total))
                    .coerceAtMost(lastDrawnLine)
                // The run's own box on this line, measured from the baseline the layout gave the
                // line rather than from the line's edges: the panel is exactly as tall as the face
                // the run is set in, plus the small inset that keeps the glyphs off its edge.
                val runSize = shown.runFontSize(panel.start, baseSize).toPx()
                val up = ascent * runSize + inset
                val down = descent * runSize + inset
                if (panel.block) {
                    val top = result.getLineBaseline(firstLine) - up
                    val bottom = result.getLineBaseline(lastLine) + down
                    // The panel reaches past the text on both sides instead of stopping at the
                    // first and last character. A fenced block whose code began on the panel's own
                    // rounded edge read as code that had spilled out of its background, which is
                    // the same reason a run inside a sentence is drawn with a margin around it; the
                    // few dp this takes come out of the container's own padding, which is wider.
                    drawRoundRect(
                        color = panel.color,
                        topLeft = Offset(-padding, top),
                        size = Size(size.width + padding * 2, bottom - top),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    return@forEach
                }
                for (line in firstLine..lastLine) {
                    // Only the visible part of the run on this line, and the whitespace a line
                    // break leaves at either end is left out of it. A space that ended a line is
                    // measured as a box the width of the gap that follows it, which drew a panel
                    // reaching far past the run it belongs to.
                    var from = maxOf(panel.start, result.getLineStart(line)).coerceIn(0, total)
                    var to = minOf(panel.end, result.getLineEnd(line, visibleEnd = true))
                        .coerceIn(0, total)
                    while (from < to && shown[from].isWhitespace()) from++
                    while (to > from && shown[to - 1].isWhitespace()) to--
                    if (to <= from) continue
                    // The first and last characters' own boxes, so the panel follows the run rather
                    // than the line it happens to sit on.
                    val left = result.getBoundingBox(from).left - padding
                    val right = result.getBoundingBox(to - 1).right + padding
                    val baseline = result.getLineBaseline(line)
                    val top = baseline - up
                    val bottom = baseline + down
                    if (right <= left || bottom <= top) continue
                    drawRoundRect(
                        color = panel.color,
                        topLeft = Offset(left, top),
                        size = Size(right - left, bottom - top),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                }
            }
        },
    )
}

/**
 * The size the run starting at [at] is drawn at.
 *
 * The renderer sets the family of a run and nothing else, so a run is set at whatever size the text
 * it was written into is: the note's own body size, or a heading's, for a run inside one. The
 * largest size among the spans covering the run's first character is that size, and a run no span
 * names a size for falls back to the style the whole text is drawn with.
 */
private fun AnnotatedString.runFontSize(at: Int, fallback: TextUnit): TextUnit =
    spanStyles.asSequence()
        .filter { it.start <= at && at < it.end }
        .mapNotNull { it.item.fontSize.takeIf { size -> size.isSp } }
        // TextUnit is not ordered, so the largest is taken by its own value: every candidate is in
        // sp, which is the one unit this looks at.
        .reduceOrNull { largest, size -> if (size.value > largest.value) size else largest }
        ?: fallback

/**
 * [this] with every heading run given the size and the line height its level asks for, as multiples
 * of [style]'s own size.
 *
 * The renderer knows a run is a heading but not the size of the body it will be drawn in, so the
 * scale is applied here, where the paragraph style is known. A note size setting therefore moves the
 * headings with the body - at 1.5x the text and the headings are both half again as large, and a
 * heading keeps the rank it was written with - instead of leaving a fixed step that the body can
 * overtake. The line height grows with the size as well, which is the other half of the same
 * problem: a heading glyph taller than the body's line height is pressed against the lines around
 * it.
 */
private fun AnnotatedString.withHeadingScales(style: TextStyle): AnnotatedString {
    val headings = getStringAnnotations(Markdown.HEADING_TAG, 0, length)
    if (headings.isEmpty()) return this
    val base = style.fontSize.takeIf { it.isSp } ?: FallbackTextSize
    return buildAnnotatedString {
        append(this@withHeadingScales)
        headings.forEach { annotation ->
            val level = annotation.item.toIntOrNull() ?: return@forEach
            val scale = Markdown.HEADING_SCALES.getOrNull(level - 1) ?: return@forEach
            // The size is a span's; the line height is a paragraph's, because a text run has no line
            // height of its own - a line is as tall as the paragraph that sets it. A heading is a
            // line of its own, so one paragraph style over the same range is what grows its line.
            addStyle(
                SpanStyle(fontSize = base * scale.size),
                annotation.start,
                annotation.end,
            )
            addStyle(
                ParagraphStyle(lineHeight = base * scale.lineHeight),
                annotation.start,
                annotation.end,
            )
        }
    }
}

/** One panel a [MarkdownText] draws: a run of code or a formula, and the colour behind it. */
private class Panel(val start: Int, val end: Int, val block: Boolean, val color: Color)

/** One run the renderer marked, and whether it has a line or a fence to itself. */
private class Run(val start: Int, val end: Int, val block: Boolean)

/**
 * The ranges of [this] carrying [tag], with the lines of one block merged into a single run.
 *
 * The renderer marks a block a line at a time, because a line is what it writes; a panel per line
 * would stack a column of boxes where the note asked for one block, and the merge is exact - two
 * runs join only when nothing but the line break separates them.
 *
 * @param blockTag the value that marks a run as a block rather than something inside a sentence.
 */
private fun AnnotatedString.runs(tag: String, blockTag: String): List<Run> =
    getStringAnnotations(tag, 0, length)
        .mapNotNull { annotation ->
            if (annotation.end <= annotation.start) {
                null
            } else {
                Run(
                    start = annotation.start,
                    end = annotation.end,
                    block = annotation.item == blockTag,
                )
            }
        }
        .sortedBy { it.start }
        .fold(mutableListOf<Run>()) { merged, run ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.block && run.block &&
                run.start == previous.end + 1 && this[previous.end] == '\n'
            ) {
                merged[merged.lastIndex] = Run(previous.start, run.end, block = true)
            } else {
                merged += run
            }
            merged
        }
