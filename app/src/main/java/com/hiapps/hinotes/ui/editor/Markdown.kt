package com.hiapps.hinotes.ui.editor

import androidx.compose.foundation.text.appendInlineContent
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
import androidx.compose.ui.unit.sp

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
     * How a rendered body draws a task's box.
     *
     * [Glyph] writes the ☑/☐ character, which is all the spanned text a widget carries can hold.
     * [Inline] leaves a place for a real checkbox instead, which the caller fills in - see
     * [TASK_BOX_CHECKED] and [TASK_BOX_UNCHECKED].
     */
    enum class TaskBox { Glyph, Inline }

    /** The inline-content ids a task's box is left under when [TaskBox.Inline] asks for one. */
    const val TASK_BOX_CHECKED = "hinotes.task.checked"
    const val TASK_BOX_UNCHECKED = "hinotes.task.unchecked"

    /**
     * The inline-content id of the box standing on source line [line].
     *
     * The line is what makes one box tell itself apart from another: a box a caller fills in is a
     * control, and a control that only said "a box was tapped" could not write the tick back to the
     * line it belongs to. The two forms stay separate ids so that a caller can fill in one of them
     * without the other, which is what the card does when a note is locked.
     */
    fun taskBoxId(line: Int, checked: Boolean): String =
        "${if (checked) TASK_BOX_CHECKED else TASK_BOX_UNCHECKED}@$line"

    /** One place a rendered body left for the caller's own checkbox, and the line it stands for. */
    data class TaskSlot(val id: String, val line: Int, val checked: Boolean)

    /** A rendered body together with the places it left for the caller's own checkboxes. */
    class Body internal constructor(val text: AnnotatedString, val slots: List<TaskSlot>)

    /**
     * The annotation marking a run of code, so a caller can draw the rounded background a text run
     * cannot draw for itself. The value is [CODE_BLOCK] for a fenced block, [CODE_INLINE] for a span.
     */
    const val CODE_TAG = "hinotes.code"
    const val CODE_BLOCK = "block"
    const val CODE_INLINE = "inline"

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
     * @param taskBox a task's box, as [TaskBox] describes.
     * @param imageMark the character an image is reduced to, for a caller that swaps it for the
     *   picture itself (the widget's spanned text); null writes the image's URL, which is what the
     *   list cards show.
     */
    private data class Options(
        val linkColor: Color,
        val taskBox: TaskBox,
        val imageMark: Char?,
        /**
         * Where the places left for a caller's checkboxes are reported as they are written.
         *
         * A render that fills none in leaves it empty, and one that draws the ☑/☐ character never
         * adds to it: the list is how a caller learns which line each of its controls stands for.
         */
        val slots: MutableList<TaskSlot> = mutableListOf(),
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
                if (line.trimStart().startsWith("```")) inCodeBlock = !inCodeBlock
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
     * Prose keeps the full renderer, so headings, lists, quotes and inline styling look exactly
     * as they did when the whole note went through [render] in one call; task lines, tables and
     * horizontal rules come back as their own blocks so the caller can draw a real checkbox, a real
     * grid or a real divider for them. None of the three is recognised inside a code fence: there it
     * is code, not Markdown.
     */
    fun previewBlocks(source: String, linkColor: Color = Color.Unspecified): List<PreviewBlock> {
        val options = Options(linkColor = linkColor, taskBox = TaskBox.Glyph, imageMark = null)
        val stripper = CommentStripper()
        // Comments are removed up front, but the lines that survive keep their index in the source:
        // that index is what a tap on a task writes back to.
        val lines = source.split('\n').map { stripper.clean(it) }
        val out = ArrayList<PreviewBlock>()
        val prose = StringBuilder()
        var inCodeBlock = false
        var index = 0

        fun flushProse() {
            if (prose.isEmpty()) return
            out += PreviewBlock.Prose(render(prose.toString(), linkColor))
            prose.setLength(0)
        }

        while (index < lines.size) {
            val line = lines[index]
            if (line == null) {
                index++
                continue
            }
            if (line.trimStart().startsWith("```")) inCodeBlock = !inCodeBlock
            val table = if (inCodeBlock) null else parseTable(lines, index)
            val task = if (inCodeBlock || table != null) null else TASK_LINE.matchEntire(line)
            val rule = !inCodeBlock && table == null && task == null && RULE_LINE.matches(line)
            val image = if (inCodeBlock || table != null || task != null || rule) {
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
                    prose.append(line)
                    if (index != lines.lastIndex) prose.append('\n')
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
        val lines = source.split('\n')
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
     * @param taskBox whether a task's box is the ☑/☐ character or a place for a real checkbox.
     * @param imageMark the character an image is reduced to, for a caller that draws the picture
     *   itself; null writes the image's URL, which is what a card has room for.
     */
    fun render(
        source: String,
        linkColor: Color = Color.Unspecified,
        taskBox: TaskBox = TaskBox.Glyph,
        imageMark: Char? = null,
    ): AnnotatedString = buildBody(source, Options(linkColor, taskBox, imageMark)).text

    /**
     * [render] together with the places it left for a caller's own checkboxes.
     *
     * The boxes are left as places rather than drawn, so that a caller can draw a control of its own
     * in each one - and the places are reported with the source line each of them stands for, which
     * is what a caller needs to write a tick, or its absence, back to the note. See [taskBoxId].
     */
    fun renderBody(source: String, linkColor: Color = Color.Unspecified): Body =
        buildBody(source, Options(linkColor, TaskBox.Inline, imageMark = null))

    /** [render] as one pass, keeping the source line every task's box was drawn for. */
    private fun buildBody(source: String, options: Options): Body {
        val stripper = CommentStripper()
        // Every source line is kept, with null standing for one that held nothing but a comment. A
        // dropped line would move every task below it, and the source line is what a caller's
        // checkbox writes back to.
        val lines = source.split('\n').map { stripper.clean(it) }
        val text = buildAnnotatedString {
            var inCodeBlock = false
            var inMathBlock = false
            var started = false
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
                val fence = line.trimStart().startsWith("```")
                val formulaFence = line.trim() == MATH_FENCE
                val table = if (inCodeBlock || inMathBlock || fence || formulaFence) {
                    null
                } else {
                    parseTable(lines, index)
                }

                lineBreak()
                when {
                    table != null -> {
                        appendTable(table, options)
                        index += table.lineCount
                        continue
                    }

                    // The fence itself is not shown, but the break written above keeps its line.
                    fence -> inCodeBlock = !inCodeBlock

                    inCodeBlock -> withCode(CODE_BLOCK) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color.Transparent,
                            )
                        ) { append(line) }
                    }

                    // A lone `$$` fences the formula below it the same way, so a multi-line formula
                    // keeps the lines the author gave it instead of being run into the prose.
                    formulaFence -> inMathBlock = !inMathBlock

                    inMathBlock -> withStyle(MATH_STYLE) { append(line) }

                    else -> appendInline(line, options, index)
                }
                index++
            }
        }
        return Body(text, options.slots)
    }

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
     * A task's box on source line [line]: the ☑/☐ character, or a place for the caller's own
     * checkbox.
     *
     * The place carries that character as its alternative text, so a caller that fills the place in
     * draws a real checkbox and one that does not still draws something a reader understands. The
     * place is reported to [Options.slots] under [taskBoxId] of the line it stands on, which is how
     * a caller that fills one in learns what it is a control for.
     */
    private fun AnnotatedString.Builder.appendTaskBox(checked: Boolean, options: Options, line: Int) {
        val glyph = if (checked) "☑" else "☐"
        if (options.taskBox == TaskBox.Inline) {
            val id = taskBoxId(line, checked)
            options.slots += TaskSlot(id, line, checked)
            appendInlineContent(
                id,
                // The glyph and a space stand in for the box wherever nothing fills the place in.
                "$glyph ",
            )
        } else {
            append("$glyph  ")
        }
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

        when {
            // A formula alone on its line is a block: its delimiters are markup and are dropped,
            // exactly as the inline form drops its dollars a few lines below.
            isSingleLineFormula(trimmed) -> {
                withStyle(MATH_STYLE) {
                    append(trimmed.substring(MATH_FENCE.length, trimmed.length - MATH_FENCE.length))
                }
            }

            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = PREVIEW_H3)) {
                    appendInlineMarkup(trimmed.removePrefix("### "), options)
                }
            }

            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = PREVIEW_H2)) {
                    appendInlineMarkup(trimmed.removePrefix("## "), options)
                }
            }

            trimmed.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = PREVIEW_H1)) {
                    appendInlineMarkup(trimmed.removePrefix("# "), options)
                }
            }

            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") ||
                trimmed.startsWith("- [X] ") -> {
                val checked = !trimmed.startsWith("- [ ] ")
                append(indent)
                appendTaskBox(checked, options, sourceLine)
                appendInlineMarkup(trimmed.substring(6), options)
            }

            // The toolbar's checkbox button writes a bare "[ ] " at the start of a line, so the
            // renderer understands that form as well as the task-list one above.
            trimmed.startsWith("[ ] ") || trimmed.startsWith("[x] ") ||
                trimmed.startsWith("[X] ") -> {
                val checked = !trimmed.startsWith("[ ] ")
                append(indent)
                appendTaskBox(checked, options, sourceLine)
                appendInlineMarkup(trimmed.substring(4), options)
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
     * app carries no room for one, so the styling is what tells a formula apart from prose. Grey at
     * a fifth of its opacity reads on the light theme and the dark one alike.
     */
    private val MATH_STYLE = SpanStyle(
        fontFamily = FontFamily.Monospace,
        background = androidx.compose.ui.graphics.Color(0x33808080),
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

    // Heading scale for the preview. The caller's body style supplies the base size, so these
    // are absolute steps that read as headings at any note size setting.
    private val PREVIEW_H1 = 26.sp
    private val PREVIEW_H2 = 22.sp
    private val PREVIEW_H3 = 19.sp

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
