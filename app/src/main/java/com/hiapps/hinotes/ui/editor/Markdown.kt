package com.hiapps.hinotes.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Markdown helpers.
 *
 * The editor writes real Markdown source rather than a rich-text model, so a note stays a
 * plain `.md` file that survives export, import, sharing and version control. These helpers
 * provide the editing verbs a Markdown editor needs (wrap inline, toggle a line prefix,
 * insert a block) and a small renderer for the preview mode.
 */
object Markdown {

    /** Wraps the selection in [marker] (e.g. `**`), or removes it when already wrapped. */
    fun toggleInline(value: TextFieldValue, marker: String): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val selected = text.substring(start, end)

        // Already wrapped inside the selection: unwrap.
        if (selected.length >= marker.length * 2 &&
            selected.startsWith(marker) &&
            selected.endsWith(marker)
        ) {
            val inner = selected.substring(marker.length, selected.length - marker.length)
            val newText = text.replaceRange(start, end, inner)
            return TextFieldValue(newText, TextRange(start, start + inner.length))
        }

        // Wrapped just outside the selection: unwrap.
        val outerStart = start - marker.length
        val outerEnd = end + marker.length
        if (outerStart >= 0 && outerEnd <= text.length &&
            text.regionMatches(outerStart, marker, 0, marker.length) &&
            text.regionMatches(end, marker, 0, marker.length)
        ) {
            val newText = text.removeRange(end, outerEnd).removeRange(outerStart, start)
            return TextFieldValue(newText, TextRange(outerStart, outerStart + selected.length))
        }

        val newText = text.replaceRange(start, end, marker + selected + marker)
        return if (selected.isEmpty()) {
            TextFieldValue(newText, TextRange(start + marker.length))
        } else {
            TextFieldValue(newText, TextRange(start + marker.length, end + marker.length))
        }
    }

    /**
     * Toggles a line prefix such as `- `, `- [ ] ` or `> ` across every line the selection
     * touches. Removes the prefix when the block already carries it, so the buttons behave as
     * toggles.
     *
     * Only lines that actually hold text decide which way the toggle goes. Treating a blank line
     * as "already prefixed" made an empty note - and any selection ending on a trailing newline -
     * look prefixed, so the first press removed a prefix that was never there and the button
     * appeared dead. A block with no text at all always gains the prefix.
     */
    fun toggleLinePrefix(value: TextFieldValue, prefix: String): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max

        // The search for the line's start runs from the character *before* the selection, which is
        // -1 once that selection sits at the very beginning of the note: the first line starts at 0
        // there, and asking for a newline at or before 0 instead answered with the newline at 0
        // itself - so a note whose first character is a line break reported a block running from 1
        // back to 0, and the substring it asked for took the editor down.
        val lineStart = text.lastIndexOf('\n', start - 1).let { if (it < 0) 0 else it + 1 }
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }

        val block = text.substring(lineStart, lineEnd)
        val lines = block.split('\n')
        val written = lines.filter { it.isNotBlank() }
        val allPrefixed = written.isNotEmpty() && written.all { it.startsWith(prefix) }

        val rebuilt = lines.joinToString("\n") { line ->
            when {
                allPrefixed && line.startsWith(prefix) -> line.removePrefix(prefix)
                allPrefixed -> line
                else -> prefix + line
            }
        }

        val newText = text.replaceRange(lineStart, lineEnd, rebuilt)
        val delta = rebuilt.length - block.length
        val newStart = (start + if (allPrefixed) -prefix.length else prefix.length)
            .coerceIn(lineStart, lineStart + rebuilt.length)
        val newEnd = (end + delta).coerceIn(newStart, lineStart + rebuilt.length)
        return TextFieldValue(newText, TextRange(newStart, newEnd))
    }

    /**
     * One task box a rendered body drew as a character, where it was written and what it stands for.
     *
     * [index] is the character of [Body.text] the ☑/☐ was written at, [line] the source line whose
     * task it is. A caller that draws a box of its own in that place - the widget puts a picture
     * there - learns the place and the line from the renderer instead of parsing the note a second
     * time. Two parsings of the same note could disagree, and the widget's did: a checklist the
     * renderer drew as boxes arrived on the home screen as a run of ☑/☐ characters because the
     * second reading of the lines recognised no tasks there.
     */
    data class TaskMark(val index: Int, val line: Int, val checked: Boolean)

    /**
     * A rendered body together with the task boxes it drew as characters.
     *
     * [marks] is where those boxes were written: the widget swaps each of them for the picture of a
     * checkbox, which a caller cannot find in the text a second time without reading the note again.
     */
    class Body internal constructor(
        val text: AnnotatedString,
        val marks: List<TaskMark>,
    )

    /**
     * The annotation marking a heading's run, so the size it is drawn at can follow the text it is
     * in.
     *
     * A heading used to be written at an absolute size (26/22/19sp). Absolute steps stop being
     * headings the moment the reader picks a larger note size - the body grows past H2 - and a span
     * size cannot grow the line's height with the glyph, so a small note size pressed the lines of
     * a heading together. The annotation carries the level instead, and each surface that draws the
     * text decides the size as a multiple of the style it is drawing in.
     */
    const val HEADING_TAG = "hinotes.heading"

    /**
     * The size and line height of a heading level, as multiples of the body style it is drawn in.
     *
     * The body is whatever the caller set: the note's own size in the editor, the card's, the
     * widget's. Multiplying it keeps the three ranks of heading in order at every size setting and
     * grows the line with the glyph, which a fixed sp size does not. One entry per level, H1 first.
     */
    data class HeadingScale(val size: Float, val lineHeight: Float)

    val HEADING_SCALES = listOf(
        HeadingScale(size = 1.6f, lineHeight = 2.0f),
        HeadingScale(size = 1.4f, lineHeight = 1.8f),
        HeadingScale(size = 1.2f, lineHeight = 1.6f),
    )

    /**
     * Whether [text] ends with a heading.
     *
     * A page gives the run of prose that ends with a heading a small breath before the block that
     * follows it. The blank line the author writes under a heading is folded by [buildBody], and
     * inside one run of prose the heading's line box is the separation to the next line; a block
     * that starts a new run - a task row, a picture, a table, a rule - has no leading of its own to
     * carry that separation, so the caller adds it where it can be laid out.
     *
     * False when the heading is not the run's last line, and false when the run ends on a line
     * break: a run that ends in a blank line ends in no heading at all.
     */
    internal fun endsWithHeading(text: AnnotatedString): Boolean =
        text.length > 0 &&
            text.getStringAnnotations(HEADING_TAG, 0, text.length).any { it.end == text.length }

    /**
     * The annotation marking a run of code, so a caller can draw the rounded background a text run
     * cannot draw for itself. The value is [CODE_BLOCK] for a fenced block, [CODE_INLINE] for a span.
     */
    const val CODE_TAG = "hinotes.code"
    const val CODE_BLOCK = "block"
    const val CODE_INLINE = "inline"

    /**
     * The annotation marking a formula, for the same reason [CODE_TAG] exists: a text run cannot
     * draw itself a rounded background, so the range is left in the string for the caller to fill
     * in. The value is [MATH_BLOCK] for a formula that has a line - or a `$$` fence - to itself,
     * and [MATH_INLINE] for `$…$` inside a sentence.
     */
    const val MATH_TAG = "hinotes.math"
    const val MATH_BLOCK = "block"
    const val MATH_INLINE = "inline"

    /**
     * [source] without the lines that hold nothing but a picture.
     *
     * A picture's line is drawn as the picture rather than as its address, so a caller that is
     * measuring what the note's *writing* costs - a card deciding how tall it is - leaves those
     * lines out. Every other line is kept exactly as written, including its line break.
     */
    fun withoutPictureLines(source: String): String =
        normalizedBreaks(source).split('\n')
            .filterNot { line -> IMAGE.matchEntire(line.trim()) != null }
            .joinToString("\n")

    /**
     * [source] with every line break written the way the app's own editor writes one.
     *
     * A note can hold `\r\n`: pasted out of another system's clipboard, copied from a file written
     * on Windows, or restored from an archive that carried one. A `\r` left at the end of a line is
     * invisible, but it is a character the line rules see - the task-list pattern does not match it
     * at all - so a checklist pasted that way was drawn as a run of ☑/☐ characters instead of as
     * boxes. Normalising before any rule reads the lines keeps the two apart, and it does not move a
     * line: a break is still one break, so a line number read here is the one a caller writes back.
     */
    internal fun normalizedBreaks(source: String): String =
        if (source.indexOf('\r') < 0) source else source.replace("\r\n", "\n").replace('\r', '\n')

    /**
     * Writes `![alt](url)` onto a line of its own, replacing the selection.
     *
     * A picture is drawn where it is written only when it is alone on its line - a text layout
     * cannot put one in the middle of a sentence - so the insertion makes room on both sides: a
     * line break before it where the text runs on, and one after it as well, at the end of the note
     * as much as in the middle. Without that last break whatever is typed next is appended to the
     * address, and the line stops being a picture for good: the preview would then show the address
     * and nothing else, with no way back but editing the Markdown by hand.
     */
    fun insertImage(value: TextFieldValue, url: String, alt: String = ""): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val written = buildString {
            if (start > 0 && text[start - 1] != '\n') append('\n')
            append("![").append(alt).append("](").append(url).append(')')
            // The caret is left on the empty line the picture was given, ready for a caption.
            append('\n')
        }
        return TextFieldValue(
            text = text.replaceRange(start, end, written),
            selection = TextRange(start + written.length),
        )
    }

    /**
     * The annotation carrying the address of a picture, over the range that picture was drawn as.
     *
     * That range is the address itself where the text has room to show it, or the single character a
     * caller asked for with `imageMark` where the caller draws the picture instead. Either way the
     * caller that draws it knows which address each place stands for without reading the note a
     * second time - which is what keeps a mark and its picture together through everything the
     * renderer does to a line on the way from the note to the text.
     */
    const val IMAGE_TAG = "hinotes.image"

    /** One rendered element of the preview. */
    sealed interface PreviewBlock {
        /** A run of prose, already carrying its inline spans. */
        data class Prose(val text: AnnotatedString) : PreviewBlock

        /**
         * One task-list line, drawn as an interactive checkbox rather than as a glyph.
         *
         * [line] is the index of the line in the note's source, which is what lets a tap on the
         * checkbox be written back to the note the reader is looking at.
         */
        data class Task(
            val text: AnnotatedString,
            val checked: Boolean,
            val line: Int,
        ) : PreviewBlock

        /**
         * A horizontal rule - `---`, `***` or `___` alone on a line - drawn as a real divider.
         *
         * It used to be drawn as a run of box-drawing characters, which is a fixed number of
         * glyphs wide: in a narrow canvas or at a large font size the run came out wider than the
         * line it was on, wrapped, and arrived as a broken rule with a stub hanging under it.
         */
        data object Divider : PreviewBlock

        /**
         * A pipe table, as GFM writes them.
         *
         * Cells come back as styled text rather than plain strings so a cell keeps the bold, code
         * or `~~strike~~` it was written with, and [alignments] carries one entry per column, read
         * from the delimiter row, so the caller can place each cell without reading the source a
         * second time.
         */
        data class Table(
            val header: List<AnnotatedString>,
            val rows: List<List<AnnotatedString>>,
            val alignments: List<ColumnAlignment>,
        ) : PreviewBlock

        /** One image, written as `![alt](url)` on a line of its own. */
        data class Image(val url: String, val alt: String) : PreviewBlock
    }

    /** Where a table column puts its cells, as the colons of its delimiter row ask. */
    enum class ColumnAlignment { Start, Center, End }

    /**
     * What one rendering pass does with the parts a run of text cannot draw.
     *
     * @param linkColor the colour a link takes, so a link reads as one; [Color.Unspecified] leaves
     *   it the colour of the text around it, underlined either way.
     * @param imageMark the character an image is reduced to, for a caller that swaps it for the
     *   picture itself (the widget's spanned text); null writes the image's URL, which is what the
     *   list cards show.
     */
    private data class Options(
        val linkColor: Color,
        val imageMark: Char?,
        /** Where the boxes drawn as characters were written, in the order they appear. */
        val marks: MutableList<TaskMark> = mutableListOf(),
    )

    /**
     * `![alt](url)`: the one form the renderer does not draw as the text it is written with - as a
     * block of its own when it stands alone on its line, and as the address it holds otherwise.
     */
    private val IMAGE = Regex("!\\[([^\\]]*)]\\(([^)\\s]+)\\)")

    /** `[text](url)`: a link, drawn in the link colour and opened by a tap. */
    private val LINK_AT = Regex("\\[([^\\]]*)]\\(([^)\\s]+)\\)")

    /** `<url>`: an address written bare, which is a link as well. */
    private val AUTOLINK_AT = Regex("<((?:https?://|mailto:)[^>\\s]+)>")

    /**
     * A task-list line: optional indent, an optional `-`/`*`/`+` bullet, the `[ ]` box and the
     * item's own text.
     *
     * The bullet is captured with the indent so [setTaskChecked] can write the line back exactly
     * as it found it, and the spaces between the box and the text are captured separately so
     * neither form loses its spacing.
     */
    private val TASK_LINE = Regex("^(\\s*(?:[-*+]\\s+)?)\\[([ xX])](\\s*)(.*)$")

    /**
     * A thematic break: three or more `-`, `*` or `_` and nothing else on the line.
     *
     * Three is the minimum CommonMark accepts, and it accepts any number beyond that, so `----`
     * is a rule too rather than a paragraph of dashes.
     */
    private val RULE_LINE = Regex("^ {0,3}(?:-{3,}|\\*{3,}|_{3,})\\s*$")

    /**
     * Removes HTML comments - `<!-- ... -->` - which are notes to the note's author and not part of
     * anything the reader asked to see.
     *
     * It works one line at a time and has to be handed every line, in order, because the preview
     * maps a rendered block back to the source line a tap landed on: taking the comments out of a
     * joined copy of the note would shift every line number below them. A line that held nothing
     * but a comment reports no text at all, so the line break goes with it instead of leaving a gap.
     *
     * Fences are tracked here as well as by the callers, so a comment-looking run inside a code
     * block stays code, exactly as the rest of the renderer treats it.
     */
    private class CommentStripper {
        private var inComment = false
        private var inCodeBlock = false

        /** [line] without its comments, or null when the line was comment and nothing else. */
        fun clean(line: String): String? {
            if (!inComment) {
                if (isCodeFence(line)) inCodeBlock = !inCodeBlock
                if (inCodeBlock || !line.contains(COMMENT_OPEN)) return line
            }
            val visible = StringBuilder()
            var i = 0
            while (i < line.length) {
                if (inComment) {
                    val end = line.indexOf(COMMENT_CLOSE, i)
                    // An unterminated comment runs to the end of the note, so the rest of this line
                    // is comment too and the state stays open for the lines below.
                    if (end < 0) break
                    inComment = false
                    i = end + COMMENT_CLOSE.length
                } else {
                    val start = line.indexOf(COMMENT_OPEN, i)
                    if (start < 0) {
                        visible.append(line, i, line.length)
                        break
                    }
                    visible.append(line, i, start)
                    inComment = true
                    i = start + COMMENT_OPEN.length
                }
            }
            val text = visible.toString()
            return if (text.isBlank()) null else text
        }
    }

    /**
     * Splits Markdown source into the pieces the preview draws.
     *
     * Prose keeps the full renderer, so headings, quotes, lists and inline styling look exactly as
     * they did when the whole note went through [render] in one call; task lines, tables, horizontal
     * rules and pictures come back as their own blocks so the caller can draw a real checkbox, a
     * real grid, a real divider or the picture for them. None of it is recognised inside a code
     * fence or a `$$` formula: there it is code or a formula, not Markdown - which is why both
     * states are tracked here exactly as [buildBody] tracks them.
     */
    fun previewBlocks(source: String, linkColor: Color = Color.Unspecified): List<PreviewBlock> {
        val options = Options(linkColor = linkColor, imageMark = null)
        val stripper = CommentStripper()
        // Comments are removed up front, but the lines that survive keep their index in the source:
        // that index is what a tap on a task writes back to.
        val lines = normalizedBreaks(source).split('\n').map { stripper.clean(it) }
        val out = ArrayList<PreviewBlock>()
        val prose = StringBuilder()
        var inCodeBlock = false
        var inMathBlock = false
        var index = 0

        fun flushProse() {
            if (prose.isEmpty()) return
            // The run keeps a break after its last line so it reads back as the lines it was
            // written as, but at a block boundary that break is drawn as a blank line of its own -
            // one body line of nothing between the prose and the block after it, which no block
            // asked for. It is cut here, before the run is rendered: the cards and the reading mode
            // both draw through this preview, and both showed that extra line. A blank line the
            // author left inside the run is untouched; only the trailing breaks of the run go.
            out += PreviewBlock.Prose(render(prose.toString().trimEnd('\n'), linkColor))
            prose.setLength(0)
        }

        // The line is prose until a block claims it, and it keeps the break it ended on so a run of
        // prose reads back as the lines it was written as.
        fun keepProse(line: String) {
            prose.append(line)
            if (index != lines.lastIndex) prose.append('\n')
        }

        while (index < lines.size) {
            val line = lines[index]
            if (line == null) {
                index++
                continue
            }
            val fence = isCodeFence(line)
            val mathFence = isMathFence(line)
            val blockLine = opensBlocks(line, inCodeBlock, inMathBlock)
            val table = if (blockLine) parseTable(lines, index) else null
            val task = if (!blockLine || table != null) null else TASK_LINE.matchEntire(line)
            val rule = blockLine && table == null && task == null && RULE_LINE.matches(line)
            val image = if (!blockLine || table != null || task != null || rule) {
                null
            } else {
                IMAGE.matchEntire(line.trim())
            }
            when {
                table != null -> {
                    flushProse()
                    out += PreviewBlock.Table(
                        header = table.header.map { renderInline(it, options) },
                        rows = table.rows.map { row ->
                            table.cells(row).map { renderInline(it, options) }
                        },
                        alignments = table.alignments,
                    )
                    index += table.lineCount
                }

                // A fence and its contents stay one run of prose: the run is what the text renderer
                // knows how to draw a code block or a formula from, and splitting it into blocks
                // would take that away from it.
                fence -> {
                    keepProse(line)
                    inCodeBlock = !inCodeBlock
                    index++
                }

                inCodeBlock -> {
                    keepProse(line)
                    index++
                }

                mathFence -> {
                    keepProse(line)
                    inMathBlock = !inMathBlock
                    index++
                }

                inMathBlock -> {
                    keepProse(line)
                    index++
                }

                rule -> {
                    flushProse()
                    out += PreviewBlock.Divider
                    index++
                }

                image != null -> {
                    flushProse()
                    out += PreviewBlock.Image(
                        url = image.groupValues[2],
                        alt = image.groupValues[1],
                    )
                    index++
                }

                task != null -> {
                    flushProse()
                    out += PreviewBlock.Task(
                        text = renderInline(task.groupValues[4], options),
                        checked = task.groupValues[2] != " ",
                        line = index,
                    )
                    index++
                }

                else -> {
                    keepProse(line)
                    index++
                }
            }
        }
        flushProse()
        return out
    }

    /**
     * Flips the checkbox on source line [line], keeping the line's indent, bullet and spacing.
     *
     * A line that is not a task line is returned untouched, as is a line index outside the note:
     * the preview can be drawn from a snapshot that a keystroke has already moved on from, and a
     * stale tap must not corrupt the note.
     */
    fun setTaskChecked(source: String, line: Int, checked: Boolean): String {
        val lines = normalizedBreaks(source).split('\n')
        if (line !in lines.indices) return source
        val match = TASK_LINE.matchEntire(lines[line]) ?: return source
        val box = if (checked) "[x]" else "[ ]"
        val rebuilt = match.groupValues[1] + box + match.groupValues[3] + match.groupValues[4]
        return lines.mapIndexed { index, text -> if (index == line) rebuilt else text }
            .joinToString("\n")
    }

    /**
     * A parsed pipe table: the rows as they were written, where the delimiter row puts each column,
     * and how many source lines the whole table occupies.
     */
    private class ParsedTable(
        val header: List<String>,
        val rows: List<List<String>>,
        val alignments: List<ColumnAlignment>,
        val lineCount: Int,
    ) {
        /**
         * [row] read as one cell per column.
         *
         * A row shorter or longer than the header is padded or cut, which is what GFM does: a row
         * that lost a pipe while being typed was still meant to fill the table, and one with an
         * extra cell must not stretch every column below the header's own count.
         */
        fun cells(row: List<String>): List<String> = List(header.size) { row.getOrElse(it) { "" } }
    }

    /**
     * Reads the pipe table that starts at [start], or returns null when those lines are not one.
     *
     * A header line, a delimiter row of hyphens and colons, and then body rows until a blank line
     * or a line without a pipe: that is the whole grammar, and anything that does not fit it - a
     * delimiter row whose width disagrees with the header, a cell of something other than hyphens
     * and colons where the delimiter belongs - is left alone to be drawn as the plain text it looks
     * like rather than being forced into a grid.
     */
    private fun parseTable(lines: List<String?>, start: Int): ParsedTable? {
        val header = lines.getOrNull(start) ?: return null
        if (!header.contains('|')) return null
        val delimiter = lines.getOrNull(start + 1) ?: return null
        if (!delimiter.contains('|')) return null
        val alignments = delimiterCells(delimiter) ?: return null
        val headerCells = splitRow(header)
        if (alignments.size != headerCells.size) return null

        val rows = ArrayList<List<String>>()
        var end = start + 2
        while (end < lines.size) {
            val row = lines[end] ?: break
            if (row.isBlank() || !row.contains('|')) break
            rows += splitRow(row)
            end++
        }
        return ParsedTable(headerCells, rows, alignments, end - start)
    }

    /**
     * A table row's cells.
     *
     * An escaped pipe belongs to the cell, not to the table's frame - `\|` is the only way to write
     * a pipe inside one - so the row is walked rather than split on the character. The escape is
     * left in the text for the inline pass to resolve, which keeps one place responsible for
     * escapes. The border pipes of `| a | b |` leave an empty cell at each end, which is not a
     * column.
     */
    private fun splitRow(line: String): List<String> {
        val cells = ArrayList<String>()
        val cell = StringBuilder()
        var i = 0
        while (i < line.length) {
            val escaped = escapedAt(line, i)
            when {
                escaped != null -> {
                    cell.append(line[i]).append(escaped)
                    i += 2
                }

                line[i] == '|' -> {
                    cells += cell.toString()
                    cell.setLength(0)
                    i++
                }

                else -> {
                    cell.append(line[i])
                    i++
                }
            }
        }
        cells += cell.toString()
        if (cells.size > 1 && cells.first().isBlank()) cells.removeAt(0)
        if (cells.size > 1 && cells.last().isBlank()) cells.removeAt(cells.lastIndex)
        return cells.map { it.trim() }
    }

    /**
     * The alignment each column of a delimiter row asks for, or null when a cell is not a delimiter
     * cell - which is what tells a malformed table apart from a real one.
     */
    private fun delimiterCells(line: String): List<ColumnAlignment>? {
        val cells = splitRow(line)
        if (cells.isEmpty()) return null
        val alignments = ArrayList<ColumnAlignment>(cells.size)
        for (cell in cells) {
            if (!ALIGNMENT_CELL.matches(cell)) return null
            alignments += when {
                cell.startsWith(":") && cell.endsWith(":") -> ColumnAlignment.Center
                cell.endsWith(":") -> ColumnAlignment.End
                // A plain run of hyphens states nothing, and left is what a reader assumes.
                else -> ColumnAlignment.Start
            }
        }
        return alignments
    }

    /**
     * Converts Markdown source into styled text for the preview mode.
     *
     * @param linkColor the colour a link takes, so it reads as one in the text around it;
     *   [Color.Unspecified] leaves it that text's own colour, underlined either way.
     * @param imageMark the character an image is reduced to, for a caller that draws the picture
     *   itself; null writes the image's URL, which is what a card has room for.
     */
    fun render(
        source: String,
        linkColor: Color = Color.Unspecified,
        imageMark: Char? = null,
    ): AnnotatedString = buildBody(source, Options(linkColor, imageMark)).text

    /**
     * [render] together with the task boxes it drew as characters.
     *
     * The drawing a caller does in their place - the widget puts a picture of a box there - needs
     * to know both where the box was written and which source line it stands for, because a tap on
     * that picture is a tick written back to that line. The renderer is the only thing that decides
     * which lines are tasks, so it is the one that reports them: reading the note a second time is
     * what let the two readings disagree - see [TaskMark].
     */
    fun renderMarks(source: String, imageMark: Char? = null): Body =
        buildBody(source, Options(Color.Unspecified, imageMark))

    /** [render] as one pass, keeping the source line every task's box was drawn for. */
    private fun buildBody(source: String, options: Options): Body {
        val stripper = CommentStripper()
        // Every source line is kept, with null standing for one that held nothing but a comment. A
        // dropped line would move every task below it, and the source line is what a caller's
        // checkbox writes back to.
        val lines = normalizedBreaks(source).split('\n').map { stripper.clean(it) }
        val text = buildAnnotatedString {
            var inCodeBlock = false
            var inMathBlock = false
            var started = false
            // Whether the last line that put something on the page was a heading, so the blank line
            // a heading is conventionally followed by can be told from a blank line the author kept.
            var lastWasHeading = false
            var index = 0

            fun lineBreak() {
                if (started) append('\n')
                started = true
            }

            while (index < lines.size) {
                val line = lines[index]
                if (line == null) {
                    index++
                    continue
                }
                // Markdown asks for a blank line under a heading, and a blank line of source is a
                // whole empty line of drawn text: kept as one, it put a body line's height of
                // nothing below every heading. It is folded away when content follows it, because
                // the heading's own line box is the separation that was meant - it is measured
                // against the heading's glyphs instead of against the body's line. The fold skips
                // the whole run of blank lines in one jump, so a pasted run of them cannot make the
                // scan quadratic. A blank line between two headings is kept: those are two banners,
                // and the line the author left is the section break between what they open.
                if (lastWasHeading && line.isBlank()) {
                    val next = nextContentIndex(lines, index + 1) ?: break
                    val nextLine = lines[next]
                    if (nextLine != null && !nextLine.startsWithHeading()) {
                        index = next
                        continue
                    }
                }
                val fence = isCodeFence(line)
                val formulaFence = isMathFence(line)
                val table = if (!opensBlocks(line, inCodeBlock, inMathBlock)) {
                    null
                } else {
                    parseTable(lines, index)
                }

                lineBreak()
                when {
                    table != null -> {
                        appendTable(table, options)
                        lastWasHeading = false
                        index += table.lineCount
                        continue
                    }

                    // The fence itself is not shown, but the break written above keeps its line.
                    fence -> {
                        inCodeBlock = !inCodeBlock
                        lastWasHeading = false
                    }

                    inCodeBlock -> {
                        withCode(CODE_BLOCK) {
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = Color.Transparent,
                                )
                            ) { append(line) }
                        }
                        lastWasHeading = false
                    }

                    // A lone `$$` fences the formula below it the same way, so a multi-line formula
                    // keeps the lines the author gave it instead of being run into the prose.
                    formulaFence -> {
                        inMathBlock = !inMathBlock
                        lastWasHeading = false
                    }

                    inMathBlock -> {
                        val start = length
                        withStyle(MATH_STYLE) { append(line) }
                        addStringAnnotation(MATH_TAG, MATH_BLOCK, start, length)
                        lastWasHeading = false
                    }

                    else -> {
                        appendInline(line, options, index)
                        lastWasHeading = line.startsWithHeading()
                    }
                }
                index++
            }
        }
        return Body(text, options.marks)
    }

    /**
     * The position of the next line from [from] on that will be drawn, or null when the run holds
     * none.
     *
     * A comment-only line reports null, and a blank line draws nothing either: the rule for folding
     * a blank line after a heading has to look past both to the line it is really making room for.
     */
    private fun nextContentIndex(lines: List<String?>, from: Int): Int? {
        for (position in from until lines.size) {
            val line = lines[position] ?: continue
            if (line.isBlank()) continue
            return position
        }
        return null
    }

    /** Whether [this] is a heading line: the first thing [appendInline] asks of a line. */
    private fun String.startsWithHeading(): Boolean = headingLevel(trimStart()) != 0

    /** One line's worth of inline markup, without the block-level treatment [render] adds. */
    private fun renderInline(text: String, options: Options): AnnotatedString =
        buildAnnotatedString { appendInlineMarkup(text, options) }

    /**
     * Appends what [block] writes and marks it as a run of code of the kind [kind].
     *
     * A text run cannot draw itself a rounded background, so the range is left in the string for
     * the caller to fill in: see [CODE_TAG].
     */
    private inline fun AnnotatedString.Builder.withCode(
        kind: String,
        block: AnnotatedString.Builder.() -> Unit,
    ) {
        val start = length
        block()
        if (length > start) addStringAnnotation(CODE_TAG, kind, start, length)
    }

    /**
     * A task's box on source line [line], written as the ☑/☐ character.
     *
     * The character is the form every surface can draw: the widget carries spanned text and fills
     * the box in with a picture, and a preview draws the row itself (`MarkdownTaskRow`) rather than
     * a place in the text. The place it was written at is reported to [Options.marks] so the widget
     * can put that picture where the character is, without reading the note a second time.
     */
    private fun AnnotatedString.Builder.appendTaskBox(checked: Boolean, options: Options, line: Int) {
        val glyph = if (checked) "☑" else "☐"
        options.marks += TaskMark(index = length, line = line, checked = checked)
        append("$glyph  ")
    }

    /**
     * Appends [text] as a heading of [level], and marks the run as a heading.
     *
     * The weight is the only thing written into the span; the size is left to the surface drawing
     * the text, because it has to be a multiple of that surface's own body size - see [HEADING_TAG]
     * and [HEADING_SCALES].
     */
    private fun AnnotatedString.Builder.appendHeading(
        text: String,
        level: Int,
        weight: FontWeight,
        options: Options,
    ) {
        val start = length
        withStyle(SpanStyle(fontWeight = weight)) { appendInlineMarkup(text, options) }
        if (length > start) addStringAnnotation(HEADING_TAG, level.toString(), start, length)
    }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
        line: String,
        options: Options,
        sourceLine: Int,
    ) {
        val trimmed = line.trimStart()
        val depth = indentDepth(line)
        val indent = "    ".repeat(depth)
        val ordered = NUMBERED.find(trimmed)
        // One pattern decides what a task line is, here and in [previewBlocks]: a line the preview
        // would draw a checkbox for must be a box in a card and on a widget as well, and a line it
        // would not must not become one anywhere else.
        val task = TASK_LINE.matchEntire(line)
        val heading = headingLevel(trimmed)

        when {
            // A formula alone on its line is a block: its delimiters are markup and are dropped,
            // exactly as the inline form drops its dollars a few lines below.
            isSingleLineFormula(trimmed) -> {
                val start = length
                withStyle(MATH_STYLE) {
                    append(trimmed.substring(MATH_FENCE.length, trimmed.length - MATH_FENCE.length))
                }
                addStringAnnotation(MATH_TAG, MATH_BLOCK, start, length)
            }

            heading != 0 -> appendHeading(
                text = trimmed.substring(heading + 1),
                level = heading,
                weight = if (heading == 1) FontWeight.Bold else FontWeight.SemiBold,
                options = options,
            )

            // A task-list line, with or without its bullet: the checkbox button writes the GitHub
            // form ("- [ ] ") and older notes carry the bare one ("[ ] "), and both are tasks.
            task != null -> {
                append(indent)
                appendTaskBox(task.groupValues[2] != " ", options, sourceLine)
                appendInlineMarkup(task.groupValues[4], options)
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                // The glyph changes with the nesting level, so a sub-item still reads as one where
                // the indent alone is hard to judge.
                append(indent)
                append(BULLETS[minOf(depth, BULLETS.lastIndex)])
                append("  ")
                appendInlineMarkup(trimmed.substring(2), options)
            }

            ordered != null -> {
                // The number is kept exactly as written: renumbering would fight a note that
                // quotes a list starting at three, or one whose items were written out of order.
                append(indent)
                append(ordered.value)
                appendInlineMarkup(trimmed.substring(ordered.value.length), options)
            }

            trimmed.startsWith("> ") -> {
                append("│  ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineMarkup(trimmed.removePrefix("> "), options)
                }
            }

            RULE_LINE.matches(line) -> append("─".repeat(RULER_WIDTH))

            else -> appendInlineMarkup(line, options)
        }
    }

    /**
     * The nesting depth a line's indentation asks for.
     *
     * Two spaces are one level, and a tab counts as those two columns so a note indented with tabs
     * nests the same way one indented with spaces does. A line deeper than the bullets below keeps
     * its indent and repeats the last glyph, because a level the author wrote still has to look
     * deeper than its parent even once the glyphs have run out.
     */
    private fun indentDepth(line: String): Int {
        var columns = 0
        for (c in line) {
            when (c) {
                ' ' -> columns += 1
                '\t' -> columns += INDENT_COLUMNS
                else -> break
            }
        }
        return columns / INDENT_COLUMNS
    }

    /**
     * Whether [line] opens or closes a fenced code block.
     *
     * One test for every reader of a note: the comment stripper, the preview and the text renderer
     * all decide the same question, and two answers to it are how a line came to be code in the
     * editor and Markdown in the preview.
     */
    private fun isCodeFence(line: String): Boolean = line.trimStart().startsWith("```")

    /** Whether [line] is the `$$` line that opens or closes a display formula. */
    private fun isMathFence(line: String): Boolean = line.trim() == MATH_FENCE

    /**
     * Whether [line] can open a block of its own.
     *
     * Inside a fence or a formula nothing is Markdown: a rule, a table or a task-looking line there
     * is the characters it is written as. Both readers of a note ask this same question, so a
     * formula can no longer be split into blocks by a line that looks like one.
     */
    private fun opensBlocks(line: String, inCodeBlock: Boolean, inMathBlock: Boolean): Boolean =
        !inCodeBlock && !inMathBlock && !isCodeFence(line) && !isMathFence(line)

    /** The level a heading line starts with, or 0 when it is not a heading. */
    private fun headingLevel(trimmed: String): Int = when {
        trimmed.startsWith("### ") -> 3
        trimmed.startsWith("## ") -> 2
        trimmed.startsWith("# ") -> 1
        else -> 0
    }

    /**
     * Whether [trimmed] is a `$$...$$` formula that opens and closes on its own line.
     *
     * A lone `$$` is the fence of a formula that spans lines instead, so it is not this form.
     */
    private fun isSingleLineFormula(trimmed: String): Boolean =
        trimmed.length > 2 * MATH_FENCE.length &&
            trimmed.startsWith(MATH_FENCE) &&
            trimmed.endsWith(MATH_FENCE)

    /** The character a backslash at [index] escapes, or null when it escapes nothing. */
    private fun escapedAt(text: String, index: Int): Char? {
        val next = text.getOrNull(index + 1) ?: return null
        return if (text[index] == '\\' && next in ESCAPABLE) next else null
    }

    /**
     * A table drawn as text, for the places with no layout to draw a grid in - the home cards and
     * the widget.
     *
     * Columns are padded to their widest cell so monospace rows line up, the delimiter row's colons
     * place each cell inside its column, and a `─┼─` rule separates the header from the body just
     * as the border pipes separate the cells. A cell keeps its own spans, so a bold or struck word
     * inside one still reads as one.
     */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendTable(
        table: ParsedTable,
        options: Options,
    ) {
        val header = table.header.map { renderInline(it, options) }
        val rows = table.rows.map { row -> table.cells(row).map { renderInline(it, options) } }
        val widths = table.alignments.indices.map { column ->
            (listOf(header) + rows).maxOf { displayWidth(it[column].text) }
        }
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
            appendRow(header, widths, table.alignments)
            append('\n')
            append(widths.joinToString("─┼─") { "─".repeat(it) })
            rows.forEach { row ->
                append('\n')
                appendRow(row, widths, table.alignments)
            }
        }
    }

    /** One text-mode table row: cells joined with ` │ ` and padded to the width of each column. */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendRow(
        cells: List<AnnotatedString>,
        widths: List<Int>,
        alignments: List<ColumnAlignment>,
    ) {
        cells.forEachIndexed { column, cell ->
            if (column > 0) append(" │ ")
            val pad = widths[column] - displayWidth(cell.text)
            val before = when (alignments[column]) {
                ColumnAlignment.Start -> 0
                ColumnAlignment.Center -> pad / 2
                ColumnAlignment.End -> pad
            }
            append(" ".repeat(before))
            append(cell)
            append(" ".repeat(pad - before))
        }
    }

    /**
     * How many columns of a monospace font [text] takes up.
     *
     * A full-width glyph - a Chinese character, say - is twice the width of a latin one, so
     * counting characters would leave a table of Chinese cells ragged in exactly the notes this
     * app is most likely to hold.
     */
    private fun displayWidth(text: String): Int = text.sumOf { if (it.isFullWidth()) 2 else 1 }

    /** The code point ranges that stand two columns wide in a monospace font. */
    private fun Char.isFullWidth(): Boolean =
        this in '\u1100'..'\u115F' || this in '\u2E80'..'\uA4CF' || this in '\uAC00'..'\uD7A3' ||
            this in '\uF900'..'\uFAFF' || this in '\uFE30'..'\uFE4F' || this in '\uFF00'..'\uFF60' ||
            this in '\uFFE0'..'\uFFE6'

    /**
     * An inline span read out of a line: the style it is drawn with, the delimiter that opened it,
     * and the range its own text occupies.
     */
    private class InlineSpan(
        val style: SpanStyle,
        val delimiter: String,
        val from: Int,
        val until: Int,
    )

    /**
     * Applies inline `**bold**`, `*italic*`, `` `code` ``, `~~strike~~` and `$math$` spans, and
     * resolves backslash escapes.
     *
     * The line is read one character at a time rather than matched against a pattern, because an
     * author's escapes have to be told apart from the markup they protect: `**a \* b**` is one bold
     * span holding a star, which a pattern looking for `**` on both sides cannot describe. Reading
     * left to right also leaves one rule for where a span ends - the first unescaped delimiter of
     * the kind that opened it.
     */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkup(
        text: String,
        options: Options,
    ) {
        var i = 0
        val plain = StringBuilder()

        fun flush() {
            if (plain.isNotEmpty()) {
                append(plain.toString())
                plain.clear()
            }
        }

        while (i < text.length) {
            // An escape is taken before any marker is looked at, so an escaped marker can never
            // open a span.
            val escaped = escapedAt(text, i)
            if (escaped != null) {
                plain.append(escaped)
                i += 2
                continue
            }

            // An image, a link and a bare address are read before the span table, so that the
            // brackets of a link are never taken for markup opening a span of its own.
            val image = IMAGE.matchAt(text, i)
            if (image != null) {
                flush()
                // A caller with no room to draw a picture is handed the address the note holds,
                // which is what a card shows; one that draws pictures is handed a place to fill.
                // The address goes with the text either way, so the caller that draws it does not
                // have to find it again - see [IMAGE_TAG].
                val start = length
                val mark = options.imageMark
                if (mark == null) append(image.groupValues[2]) else append(mark)
                addStringAnnotation(IMAGE_TAG, image.groupValues[2], start, length)
                i = image.range.last + 1
                continue
            }
            val link = LINK_AT.matchAt(text, i)
            if (link != null) {
                flush()
                withLink(LinkAnnotation.Url(link.groupValues[2], linkStyles(options.linkColor))) {
                    // The link's own text is read as markup, so a bold word inside a link is bold.
                    appendInlineMarkup(link.groupValues[1], options)
                }
                i = link.range.last + 1
                continue
            }
            val address = AUTOLINK_AT.matchAt(text, i)
            if (address != null) {
                flush()
                withLink(LinkAnnotation.Url(address.groupValues[1], linkStyles(options.linkColor))) {
                    append(address.groupValues[1])
                }
                i = address.range.last + 1
                continue
            }

            val span = spanAt(text, i)
            if (span == null) {
                plain.append(text[i])
                i++
                continue
            }
            flush()
            // A span's text is drawn as written, with its escapes resolved: markup inside it is not
            // read a second time, so the stars in a code span stay the characters they are.
            val body: AnnotatedString.Builder.() -> Unit = {
                appendLiteral(text.substring(span.from, span.until))
            }
            if (span.delimiter == CODE_DELIMITER) {
                withCode(CODE_INLINE) { withStyle(span.style, body) }
            } else if (span.delimiter == MATH_DELIMITER) {
                // A formula keeps its styling and gains a mark: the background it sits on is drawn
                // by the caller, rounded, where a span's own background is a plain rectangle.
                val start = length
                withStyle(span.style, body)
                addStringAnnotation(MATH_TAG, MATH_INLINE, start, length)
            } else {
                withStyle(span.style, body)
            }
            i = span.until + span.delimiter.length
        }
        flush()
    }

    /** A link's styling: the caller's colour where it asked for one, underlined either way. */
    private fun linkStyles(color: Color): TextLinkStyles = TextLinkStyles(
        style = SpanStyle(color = color, textDecoration = TextDecoration.Underline),
    )

    /**
     * The span that opens at [index], or null when none does: the character there is not a marker,
     * the line holds no delimiter that could close it, the close would leave the span empty, or the
     * pair is a pair of dollars that do not hug a formula.
     */
    private fun spanAt(text: String, index: Int): InlineSpan? {
        val marker = SPANS.firstOrNull { text.startsWith(it.first, index) } ?: return null
        val (delimiter, style) = marker
        val from = index + delimiter.length
        val close = closingDelimiter(text, from, delimiter)
        // An empty span is not a span: `****` is four stars, not a pair of empty bold ones.
        if (close <= from) return null
        // A formula's dollars hug their content - a space just inside one is what keeps
        // `$5 and $10` reading as a price range - and the rule holds at both ends of the pair.
        if (delimiter == MATH_DELIMITER) {
            val first = text[from]
            val last = text[close - 1]
            if (first.isWhitespace() || last.isWhitespace() || first == '$' || last == '$') return null
        }
        return InlineSpan(style, delimiter, from, close)
    }

    /**
     * Where the delimiter closing a span opened at [from] sits, or -1 when the line holds none.
     *
     * Only an unescaped delimiter closes: in `**a \* b**` the star behind the backslash is text, so
     * the search walks over it. A line break ends the search, because a span never runs across two
     * lines.
     */
    private fun closingDelimiter(text: String, from: Int, delimiter: String): Int {
        var i = from
        while (i < text.length) {
            when {
                text[i] == '\n' -> return -1
                escapedAt(text, i) != null -> i += 2
                text.startsWith(delimiter, i) -> return i
                else -> i++
            }
        }
        return -1
    }

    /** [text] with its escapes resolved and no markup read at all, for the inside of a span. */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendLiteral(text: String) {
        var i = 0
        while (i < text.length) {
            val escaped = escapedAt(text, i)
            if (escaped == null) {
                append(text[i])
                i++
            } else {
                append(escaped)
                i += 2
            }
        }
    }

    /**
     * An ordered-list marker: digits followed by `.` or `)` and a space - the two forms the two
     * Markdown traditions use.
     */
    private val NUMBERED = Regex("^\\d+[.)]\\s+")

    /** One cell of a delimiter row: hyphens, with a colon at either end asking for alignment. */
    private val ALIGNMENT_CELL = Regex("^:?-+:?$")

    /** The bullet of each list level, shallowest first. */
    private val BULLETS = listOf("•", "◦", "▪")

    /**
     * Inline and block formulas, drawn as they were written: there is no LaTeX engine here and the
     * app carries no room for one, so the styling is what tells a formula apart from prose.
     *
     * The formula's colour is the caller's business - see [MATH_TAG] - because the background it
     * sits on is drawn as a rounded panel by the text that shows it, and a panel painted by the
     * span underneath would be a rectangle showing at its corners.
     */
    private val MATH_STYLE = SpanStyle(
        fontFamily = FontFamily.Monospace,
    )

    /**
     * The inline spans the scanner reads, longest delimiter first so `**` is taken as the one marker
     * it is rather than as two `*`s. It has to sit below [MATH_STYLE] because an object builds its
     * properties in the order they are written and this table borrows that style.
     */
    private val SPANS = listOf(
        "**" to SpanStyle(fontWeight = FontWeight.Bold),
        "__" to SpanStyle(fontWeight = FontWeight.Bold),
        "~~" to SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough),
        CODE_DELIMITER to SpanStyle(fontFamily = FontFamily.Monospace),
        MATH_DELIMITER to MATH_STYLE,
        "*" to SpanStyle(fontStyle = FontStyle.Italic),
    )

    private const val COMMENT_OPEN = "<!--"
    private const val COMMENT_CLOSE = "-->"
    private const val MATH_FENCE = "$$"

    /** The inline formula's delimiter; the block form fences with [MATH_FENCE] instead. */
    private const val MATH_DELIMITER = "\$"

    /** The span delimiter that makes a run of code, which is the one drawn with a background. */
    private const val CODE_DELIMITER = "`"

    /** Columns one list level takes: two spaces, or a tab read as that same step. */
    private const val INDENT_COLUMNS = 2

    /**
     * Every ASCII punctuation character - the set a backslash strips of its markup.
     *
     * A backslash before anything else is left alone, so a Windows path or a hand-written `\n`
     * survives being read as Markdown.
     */
    private const val ESCAPABLE = "!\"#\$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

    /**
     * Width of the text-mode rule, in glyphs.
     *
     * The note's own preview draws a rule as a divider, so this only ever appears where the note
     * is rendered as text without a layout to draw in - the list cards. Twelve box-drawing glyphs
     * stay inside a card at any font size the editor offers, where 24 of them wrapped and left a
     * stub on the next line.
     */
    private const val RULER_WIDTH = 12
}
