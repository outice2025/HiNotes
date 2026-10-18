package com.hiapps.hinotes.widget

import android.text.SpannableStringBuilder
import android.text.Spanned
import com.hiapps.hinotes.ui.editor.Markdown

/**
 * One item of the widget's list.
 *
 * A collection widget is a list, so "scroll for more" is the list itself: one item per chunk of the
 * body, requested as it comes into view. A task line is an item of its own, and a different kind of
 * one: its box is a control beside its text rather than a picture inside it, so that the box can be
 * tapped without the note being opened.
 */
internal sealed interface WidgetChunk {

    /** What the item draws. */
    val text: CharSequence

    /** A run of the note's prose. */
    class Text(override val text: CharSequence) : WidgetChunk

    /**
     * One task line, drawn as a box beside its text.
     *
     * [line] is the index of the line in the note's source, which is what a tap on the box writes
     * the tick back to; [checked] is the state the note records, which is what decides which of the
     * two box pictures is drawn.
     */
    class Task(
        override val text: CharSequence,
        val checked: Boolean,
        val line: Int,
    ) : WidgetChunk
}

/**
 * The note's body as the items the widget's list is built from.
 *
 * Cutting the body up is the only part of the widget's drawing that does not need a scheme, a
 * bitmap or a launcher, so it lives on its own and the checks in `tools/verify` run it - rather
 * than reason about it - on a desktop JVM.
 */
internal object WidgetChunks {

    /** The characters the renderer writes a task's box as; see [WidgetText]. */
    private const val BOX_CHECKED = '\u2611'
    private const val BOX_UNCHECKED = '\u2610'

    /**
     * Source lines per chunk.
     *
     * Ten lines keeps the list cheap to build and each item short enough that a swipe moves a visible
     * amount through the note; the size the card is placed at is the host's business, and a small
     * card simply shows fewer items at a time.
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

    /**
     * The source lines of [content] that are task items, in the order the body draws them.
     *
     * The renderer is the only thing that decides what a task line is - a box with an item's text
     * after it, inside no fence and outside no table - so the lines are read out of its own parse of
     * the note rather than matched a second time here, where the two answers could drift apart.
     */
    fun taskLines(content: String): List<Int> =
        Markdown.previewBlocks(content)
            .filterIsInstance<Markdown.PreviewBlock.Task>()
            .map { block -> block.line }

    /**
     * [body] cut into items, with every task line drawn as an item of its own.
     *
     * [taskLines] are the source lines of the note's tasks, in the order the body draws them, and a
     * line that opens with a box takes the next of them. The box itself is taken out of the item's
     * text, because the item draws the box beside the text rather than inside it.
     *
     * The task lines are matched up with the boxes the body draws rather than trusted: a body that
     * draws a different number of them than the note has task lines is a body whose lines cannot be
     * matched up, and a tick written back to the wrong line is worse than a box that has to be
     * ticked in the editor. Nothing is offered as a task then, and the body is cut as plain prose.
     *
     * Trailing whitespace is left off first, because a rendered body may end with the newline of its
     * last source line and a body is not obliged to end at one: cutting that off turns a blank last
     * line into the blank tail of a chunk, which a `TextView` draws as an empty line and the list
     * then shows as an item taller than the text in it. Every chunk is stripped the same way before
     * it is kept, so that no item can end on a blank line whatever the note was written like.
     */
    fun chunks(body: CharSequence, taskLines: List<Int> = emptyList()): List<WidgetChunk> {
        val end = trimmedLength(body)
        if (end == 0) return emptyList()
        val lines = taskLines.takeIf { countBoxes(body, end) == it.size }.orEmpty()

        val items = ArrayList<WidgetChunk>()
        var proseStart = -1
        var nextTask = 0
        var lineStart = 0
        while (lineStart < end) {
            val newline = indexOf('\n', body, lineStart, end)
            val lineEnd = if (newline < 0) end else newline
            val box = if (nextTask < lines.size) boxAt(body, lineStart, lineEnd) else null
            if (box != null) {
                if (proseStart >= 0) items += textChunks(slice(body, proseStart, lineStart))
                proseStart = -1
                items += WidgetChunk.Task(
                    text = withoutBox(body, lineStart, box.textStart, lineEnd),
                    checked = box.checked,
                    line = lines[nextTask],
                )
                nextTask++
            } else if (proseStart < 0) {
                proseStart = lineStart
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
        if (proseStart >= 0) items += textChunks(slice(body, proseStart, end))
        return items
    }

    /** A task box found at the head of a line: whether it is ticked, and where the text starts. */
    private class Box(val checked: Boolean, val textStart: Int)

    /**
     * The box the line running from [start] to [end] opens with, or null when it is prose.
     *
     * The line is the indent the renderer wrote, then the box, then the gap between it and the
     * item's text - which is the whole of how a task line is drawn. The indent goes with the box:
     * the item's own layout puts the box at the card's inset, which is where the editor's checklist
     * puts it too.
     */
    private fun boxAt(body: CharSequence, start: Int, end: Int): Box? {
        var i = start
        while (i < end && body[i] == ' ') i++
        val checked = when {
            i >= end -> return null
            body[i] == BOX_CHECKED -> true
            body[i] == BOX_UNCHECKED -> false
            else -> return null
        }
        i++
        while (i < end && body[i] == ' ') i++
        return Box(checked, i)
    }

    /**
     * [source] from [start] up to [end] without the box and indent that open it.
     *
     * A body that carries spans is copied through `SpannableStringBuilder`, which keeps the styling
     * of the text after the box; a body of plain characters is cut as it stands, which is what the
     * desktop checks can run - the platform's own type is a stub there.
     */
    private fun withoutBox(
        source: CharSequence,
        start: Int,
        textStart: Int,
        end: Int,
    ): CharSequence =
        if (source is Spanned) {
            SpannableStringBuilder(source, start, end).apply { delete(0, textStart - start) }
        } else {
            source.subSequence(textStart, end)
        }

    /** How many task boxes [body] draws up to [end]. */
    private fun countBoxes(body: CharSequence, end: Int): Int {
        var count = 0
        for (i in 0 until end) {
            if (body[i] == BOX_CHECKED || body[i] == BOX_UNCHECKED) count++
        }
        return count
    }

    /**
     * [source] cut into the items a run of prose is shown as: [CHUNK] lines, or [CHUNK_CHARS]
     * characters, whichever comes first.
     */
    private fun textChunks(source: CharSequence): List<WidgetChunk> {
        val end = trimmedLength(source)
        if (end == 0) return emptyList()

        val out = mutableListOf<WidgetChunk>()
        var start = 0
        var lines = 0
        for (i in 0 until end) {
            if (source[i] == '\n') {
                lines++
                if (lines == CHUNK) {
                    out += WidgetChunk.Text(stripped(source, start, i))
                    start = i + 1
                    lines = 0
                }
            } else if (i - start >= CHUNK_CHARS) {
                out += WidgetChunk.Text(stripped(source, start, i))
                start = i
                lines = 0
            }
        }
        if (start < end) out += WidgetChunk.Text(stripped(source, start, end))
        // A chunk of blank lines would be an empty row with a gap above and below it.
        return out.filter { trimmedLength(it.text) > 0 }
    }

    /**
     * The length of [source] once the whitespace at its end is left off.
     *
     * The whitespace of a line is exactly what has no glyph to draw, so a body that ends in spaces
     * has as much text as one that ends at its last written character, and both are cut at the same
     * place.
     */
    private fun trimmedLength(source: CharSequence): Int {
        val last = source.indexOfLast { !it.isWhitespace() }
        return last + 1
    }

    /** [source] from [start] up to [end], with any whitespace at the end of that run left off. */
    private fun stripped(source: CharSequence, start: Int, end: Int): CharSequence {
        val slice = slice(source, start, end)
        return slice.subSequence(0, trimmedLength(slice))
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

    /** The first [character] at or after [from], or -1 when the run holds none up to [end]. */
    private fun indexOf(character: Char, source: CharSequence, from: Int, end: Int): Int {
        for (i in from until end) {
            if (source[i] == character) return i
        }
        return -1
    }
}
