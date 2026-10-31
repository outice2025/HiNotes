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

    /**
     * A picture the note holds, drawn by an item of its own.
     *
     * A span that carries a bitmap is not a `ParcelableSpan`, so the text a widget hands the
     * launcher cannot hold one: the picture arrived there as the single character it was written as
     * and the home screen showed "OBJ". The list therefore gives a picture an item of its own, with
     * an `ImageView` for the card to fill in - see `HiNotesWidgetService`.
     */
    class Picture(val url: String) : WidgetChunk {
        /** A picture draws no text. */
        override val text: CharSequence get() = ""
    }
}

/**
 * The note's body as the items the widget's list is built from.
 *
 * Cutting the body up is the only part of the widget's drawing that does not need a scheme, a
 * bitmap or a launcher, so it lives on its own and the checks in `tools/verify` run it - rather
 * than reason about it - on a desktop JVM.
 */
internal object WidgetChunks {

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
     * [body] cut into items, with every box the renderer drew as an item of its own.
     *
     * [marks] are the boxes the renderer drew, in the order it wrote them, each carrying the place
     * it was written at and the source line it stands for - see [Markdown.TaskMark]. The places are
     * what this splits on rather than a second reading of the note: a line is a task because the
     * renderer drew its box, so the two cannot disagree about which lines are controls, and a tick
     * written back from one of them lands on the line its box was drawn for.
     *
     * Trailing whitespace is left off first, because a rendered body may end with the newline of its
     * last source line and a body is not obliged to end at one: cutting that off turns a blank last
     * line into the blank tail of a chunk, which a `TextView` draws as an empty line and the list
     * then shows as an item taller than the text in it. Every chunk is stripped the same way before
     * it is kept, so that no item can end on a blank line whatever the note was written like.
     *
     * [pictures] maps the position of each picture's mark in [body] to the address it stands for. A
     * line that holds nothing but one of those marks becomes an item of its own, because a picture
     * cannot travel in the text a widget hands the launcher - see [WidgetChunk.Picture].
     */
    fun chunks(
        body: CharSequence,
        marks: List<Markdown.TaskMark> = emptyList(),
        pictures: Map<Int, String> = emptyMap(),
    ): List<WidgetChunk> {
        val end = trimmedLength(body)
        if (end == 0) return emptyList()

        val items = ArrayList<WidgetChunk>()
        var proseStart = -1
        var nextMark = 0
        var lineStart = 0
        while (lineStart < end) {
            val newline = indexOf('\n', body, lineStart, end)
            val lineEnd = if (newline < 0) end else newline
            val picture = pictureUrl(body, lineStart, lineEnd, pictures)
            // A mark that lies behind this line's start belongs to a line already drawn, which a
            // body this was handed cannot have: the list is skipped rather than shifting every
            // later box onto the line above its own.
            while (nextMark < marks.size && marks[nextMark].index < lineStart) nextMark++
            val mark = marks.getOrNull(nextMark)
            val box = if (picture != null || mark == null) {
                null
            } else {
                boxAt(body, lineStart, lineEnd, mark)
            }
            when {
                picture != null -> {
                    if (proseStart >= 0) items += textChunks(slice(body, proseStart, lineStart))
                    proseStart = -1
                    items += WidgetChunk.Picture(picture)
                }

                box != null -> {
                    if (proseStart >= 0) items += textChunks(slice(body, proseStart, lineStart))
                    proseStart = -1
                    items += WidgetChunk.Task(
                        text = withoutTrailingSpace(
                            withoutBox(body, lineStart, box.textStart, lineEnd),
                        ),
                        checked = box.checked,
                        line = box.line,
                    )
                    nextMark++
                }

                proseStart < 0 -> proseStart = lineStart
            }
            if (newline < 0) break
            lineStart = newline + 1
        }
        if (proseStart >= 0) items += textChunks(slice(body, proseStart, end))
        return items
    }

    /** A task box found at the head of a line: the state it records, and where its text starts. */
    private class Box(val checked: Boolean, val line: Int, val textStart: Int)

    /**
     * The address of the picture a line holds, when the whole line is that picture.
     *
     * The renderer writes a picture that has its line to itself as one character with the address
     * annotated over it, and that is the only form this recognises. A picture written inside a
     * sentence has no line of its own to be drawn on, so it stays the character the renderer wrote
     * and is drawn with the text around it.
     */
    private fun pictureUrl(
        body: CharSequence,
        start: Int,
        end: Int,
        pictures: Map<Int, String>,
    ): String? {
        var i = start
        while (i < end && body[i].isWhitespace()) i++
        val at = i
        if (at >= end || body[at] != '\uFFFC') return null
        i++
        while (i < end && body[i].isWhitespace()) i++
        if (i != end) return null
        return pictures[at]
    }

    /**
     * The box [mark] stands for when it opens the line running from [start] to [end], or null.
     *
     * The line is the indent the renderer wrote, then the box, then the gap between it and the
     * item's text - which is the whole of how a task line is drawn. The indent goes with the box:
     * the item's own layout puts the box at the card's inset, which is where the editor's checklist
     * puts it too. A mark anywhere else is not this line's box, and the line is drawn as prose.
     */
    private fun boxAt(body: CharSequence, start: Int, end: Int, mark: Markdown.TaskMark): Box? {
        if (mark.index < start || mark.index >= end) return null
        var i = start
        while (i < mark.index && body[i] == ' ') i++
        if (i != mark.index) return null
        var textStart = mark.index + 1
        while (textStart < end && body[textStart] == ' ') textStart++
        return Box(mark.checked, mark.line, textStart)
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

    /**
     * [source] without the whitespace at its end.
     *
     * A task line keeps whatever spaces it was written with, and a run of them at the end of an
     * item's text is drawn as an empty line the list then shows as an item taller than the text in
     * it - the same reason every prose chunk is stripped. Nothing but whitespace is lost, and a task
     * line that holds no text at all is still an item: its box is what the row is.
     */
    private fun withoutTrailingSpace(source: CharSequence): CharSequence =
        source.subSequence(0, trimmedLength(source))

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
