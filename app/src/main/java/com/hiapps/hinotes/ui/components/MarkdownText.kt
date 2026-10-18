package com.hiapps.hinotes.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.text.InlineTextContent
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
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiapps.hinotes.ui.editor.Markdown

/** The corner a run of code's background is drawn with. */
private val CodeCorner = 6.dp

/** How far the background reaches past the code it sits behind, horizontally. */
private val CodePadding = 4.dp

/** How far the background reaches above and below the code's own box. */
private val CodeInset = 2.dp

/** The size a run of code falls back to when the style it is drawn in names none. */
private val FallbackCodeSize = 14.sp

/**
 * A rendered run of Markdown.
 *
 * A `Text` draws a span's background as a rectangle, and Material 3 gives it no corner radius, so
 * the rounded background a run of code sits on is painted here instead: the renderer marks every
 * code range it wrote (see [Markdown.CODE_TAG]) and this draws each of them from the text layout
 * the field has just produced. A fenced block is one panel - its lines are merged into a single
 * rounded rectangle running from the first code line's baseline to the last one's - while a span of
 * inline code is a box per line, which is what it is when it wraps.
 *
 * The panel is the height of the *code's* own font box, measured from the line's baseline, and not
 * the height of the line. A line is taller than the text standing on it - the paragraph sets a line
 * height, and a line holding Chinese takes its ascent from the fallback face, which is taller than
 * the monospace one - while the code's glyphs hang from a baseline part-way down it. A background
 * drawn from the line's own top and bottom therefore left a band of colour above the code and let
 * the code's descenders cross the lower edge, which read as a background that had slipped off its
 * text; the platform's own span background, which a text field draws, is the height of the run's
 * face and stays on the glyphs, and that is the look this matches.
 *
 * Links are the other thing the text cannot finish by itself: the renderer leaves them unstyled and
 * this colours them, because a link that looks like the prose around it is a link nobody taps.
 *
 * @param codeBackground the colour a run of code sits on.
 * @param inlineContent what to draw wherever the text left a place for something (a task's box).
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
    inlineContent: Map<String, InlineTextContent> = emptyMap(),
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val runs = remember(text) { text.codeRuns() }
    val density = LocalDensity.current
    val corner = with(density) { CodeCorner.toPx() }
    val padding = with(density) { CodePadding.toPx() }
    val inset = with(density) { CodeInset.toPx() }
    val baseSize = style.fontSize.takeIf { it.isSp } ?: FallbackCodeSize
    // How far the code's own face reaches above and below the baseline it is written on, as a
    // fraction of the size it is set at. It is asked of the platform's monospace face once, because
    // the face is the one the renderer writes code in and its proportions do not change with the
    // size; what does change is the size, which a heading gives its own code runs.
    val metrics = remember { Paint().apply { typeface = Typeface.MONOSPACE; textSize = 1f }.fontMetrics }
    val ascent = -metrics.ascent
    val descent = metrics.descent

    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
        inlineContent = inlineContent,
        onTextLayout = { layout = it },
        modifier = modifier.drawBehind {
            val result = layout ?: return@drawBehind
            // Every offset below is clamped to the text the layout itself was made from. The two are
            // the same text in the ordinary run of things - the layout is the one this node has just
            // produced - but a background is painted from ranges the renderer wrote into a string,
            // and a frame in which the layout and the string disagree has to lose a background at
            // worst rather than ask the layout about an offset it does not have.
            val total = minOf(result.layoutInput.text.length, text.length)
            // And what is drawn is what is on screen: a card shows a few lines of a note and cuts
            // the rest, and a range below that cut belongs to lines the layout never kept - asking
            // where one of those sits answers with the last line it does have, which drew a
            // background across a line of prose that held no code at all.
            val lastDrawnLine = result.lineCount - 1
            runs.forEach { run ->
                val firstLine = result.getLineForOffset(run.start.coerceIn(0, total))
                if (firstLine > lastDrawnLine) return@forEach
                val lastLine = result
                    .getLineForOffset((run.end - 1).coerceIn(0, total))
                    .coerceAtMost(lastDrawnLine)
                // The code's own box on this run, measured from the baseline the layout gave the
                // line rather than from the line's edges: the panel is exactly as tall as the face
                // the code is set in, plus the small inset that keeps the glyphs off its edge.
                val codeSize = text.codeFontSize(run.start, baseSize).toPx()
                val up = ascent * codeSize + inset
                val down = descent * codeSize + inset
                if (run.block) {
                    val top = result.getLineBaseline(firstLine) - up
                    val bottom = result.getLineBaseline(lastLine) + down
                    // The panel reaches past the text on both sides instead of stopping at the
                    // first and last character. A fenced block whose code began on the panel's own
                    // rounded edge read as code that had spilled out of its background, which is
                    // the same reason a run of inline code is drawn with a margin around it; the
                    // few dp this takes come out of the container's own padding, which is wider.
                    drawRoundRect(
                        color = codeBackground,
                        topLeft = Offset(-padding, top),
                        size = Size(size.width + padding * 2, bottom - top),
                        cornerRadius = CornerRadius(corner, corner),
                    )
                    return@forEach
                }
                for (line in firstLine..lastLine) {
                    // Only the visible part of the run on this line, and the whitespace a line
                    // break leaves at either end is left out of it. A space that ended a line is
                    // measured as a box the width of the gap that follows it, which drew a
                    // background reaching far past the code it belongs to.
                    var from = maxOf(run.start, result.getLineStart(line)).coerceIn(0, total)
                    var to = minOf(run.end, result.getLineEnd(line, visibleEnd = true))
                        .coerceIn(0, total)
                    while (from < to && text[from].isWhitespace()) from++
                    while (to > from && text[to - 1].isWhitespace()) to--
                    if (to <= from) continue
                    // The first and last characters' own boxes, so the background follows the code
                    // rather than the line it happens to sit on.
                    val left = result.getBoundingBox(from).left - padding
                    val right = result.getBoundingBox(to - 1).right + padding
                    val baseline = result.getLineBaseline(line)
                    val top = baseline - up
                    val bottom = baseline + down
                    if (right <= left || bottom <= top) continue
                    drawRoundRect(
                        color = codeBackground,
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
 * The size the run of code starting at [at] is drawn at.
 *
 * The renderer sets the family of a code run and nothing else, so a run is set at whatever size the
 * text it was written into is: the note's own body size, or a heading's, for code inside one. The
 * largest size among the spans covering the run's first character is that size, and a run no span
 * names a size for falls back to the style the whole text is drawn with.
 */
private fun AnnotatedString.codeFontSize(at: Int, fallback: TextUnit): TextUnit =
    spanStyles.asSequence()
        .filter { it.start <= at && at < it.end }
        .mapNotNull { it.item.fontSize.takeIf { size -> size.isSp } }
        // TextUnit is not ordered, so the largest is taken by its own value: every candidate is in
        // sp, which is the one unit this looks at.
        .reduceOrNull { largest, size -> if (size.value > largest.value) size else largest }
        ?: fallback

/** One run of code the renderer marked, and whether it is a fenced block rather than a span. */
private class CodeRun(val start: Int, val end: Int, val block: Boolean)

/**
 * The code ranges of [this], with the lines of one fence merged into a single run.
 *
 * The renderer marks a fenced block a line at a time, because a line is what it writes; a panel per
 * line would stack a column of boxes where the note asked for one block, and the merge is exact -
 * two runs join only when nothing but the line break separates them.
 */
private fun AnnotatedString.codeRuns(): List<CodeRun> =
    getStringAnnotations(Markdown.CODE_TAG, 0, length)
        .mapNotNull { annotation ->
            if (annotation.end <= annotation.start) {
                null
            } else {
                CodeRun(
                    start = annotation.start,
                    end = annotation.end,
                    block = annotation.item == Markdown.CODE_BLOCK,
                )
            }
        }
        .sortedBy { it.start }
        .fold(mutableListOf<CodeRun>()) { merged, run ->
            val previous = merged.lastOrNull()
            if (previous != null && previous.block && run.block &&
                run.start == previous.end + 1 && this[previous.end] == '\n'
            ) {
                merged[merged.lastIndex] = CodeRun(previous.start, run.end, block = true)
            } else {
                merged += run
            }
            merged
        }
