package com.hiapps.hinotes.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
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

        val lineStart = text.lastIndexOf('\n', (start - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
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

    /** Inserts [snippet] at the cursor, replacing the selection, and leaves the caret after it. */
    fun insertAtCursor(
        value: TextFieldValue,
        snippet: String,
        caretOffsetFromEnd: Int = 0,
    ): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val needsLeadingNewline = start > 0 && text[start - 1] != '\n'
        val insertion = (if (needsLeadingNewline) "\n" else "") + snippet
        val newText = text.replaceRange(start, end, insertion)
        val caret = start + insertion.length - caretOffsetFromEnd
        return TextFieldValue(newText, TextRange(caret.coerceIn(0, newText.length)))
    }

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
    }

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
     * Splits Markdown source into the pieces the preview draws.
     *
     * Prose keeps the full renderer, so headings, lists, quotes and inline styling look exactly
     * as they did when the whole note went through [render] in one call; task lines and horizontal
     * rules come back as their own blocks so the caller can draw a real checkbox or a real divider
     * for them. Neither is recognised inside a code fence: there it is code, not Markdown.
     */
    fun previewBlocks(source: String): List<PreviewBlock> {
        val lines = source.split('\n')
        val out = ArrayList<PreviewBlock>()
        val prose = StringBuilder()
        var inCodeBlock = false

        fun flushProse() {
            if (prose.isEmpty()) return
            out += PreviewBlock.Prose(render(prose.toString()))
            prose.setLength(0)
        }

        lines.forEachIndexed { index, line ->
            if (line.trimStart().startsWith("```")) inCodeBlock = !inCodeBlock
            val task = if (inCodeBlock) null else TASK_LINE.matchEntire(line)
            val rule = !inCodeBlock && task == null && RULE_LINE.matches(line)
            when {
                rule -> {
                    flushProse()
                    out += PreviewBlock.Divider
                }

                task != null -> {
                    flushProse()
                    out += PreviewBlock.Task(
                        text = renderInline(task.groupValues[4]),
                        checked = task.groupValues[2] != " ",
                        line = index,
                    )
                }

                else -> {
                    prose.append(line)
                    if (index != lines.lastIndex) prose.append('\n')
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

    /** Converts Markdown source into styled text for the preview mode. */
    fun render(source: String): AnnotatedString = buildAnnotatedString {
        val lines = source.split('\n')
        var inCodeBlock = false

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine

            when {
                line.trimStart().startsWith("```") -> {
                    inCodeBlock = !inCodeBlock
                    // The fence itself is not shown, but its line break is preserved below.
                }

                inCodeBlock -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                    ) { append(line) }
                }

                else -> appendInline(line)
            }

            if (index != lines.lastIndex) append('\n')
        }
    }

    /** One line's worth of inline markup, without the block-level treatment [render] adds. */
    private fun renderInline(text: String): AnnotatedString =
        buildAnnotatedString { appendInlineMarkup(text) }

    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(line: String) {
        val trimmed = line.trimStart()
        val indent = line.length - trimmed.length

        when {
            trimmed.startsWith("### ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = PREVIEW_H3)) {
                    appendInlineMarkup(trimmed.removePrefix("### "))
                }
            }

            trimmed.startsWith("## ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = PREVIEW_H2)) {
                    appendInlineMarkup(trimmed.removePrefix("## "))
                }
            }

            trimmed.startsWith("# ") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = PREVIEW_H1)) {
                    appendInlineMarkup(trimmed.removePrefix("# "))
                }
            }

            trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") ||
                trimmed.startsWith("- [X] ") -> {
                val checked = !trimmed.startsWith("- [ ] ")
                append("    ".repeat(indent / 4))
                append(if (checked) "☑  " else "☐  ")
                appendInlineMarkup(trimmed.substring(6))
            }

            // The toolbar's checkbox button writes a bare "[ ] " at the start of a line, so the
            // renderer understands that form as well as the task-list one above.
            trimmed.startsWith("[ ] ") || trimmed.startsWith("[x] ") ||
                trimmed.startsWith("[X] ") -> {
                val checked = !trimmed.startsWith("[ ] ")
                append("    ".repeat(indent / 4))
                append(if (checked) "☑  " else "☐  ")
                appendInlineMarkup(trimmed.substring(4))
            }

            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                append("    ".repeat(indent / 4))
                append("•  ")
                appendInlineMarkup(trimmed.substring(2))
            }

            NUMBERED.matches(trimmed) -> {
                val marker = NUMBERED.find(trimmed)!!.value
                append("    ".repeat(indent / 4))
                append(marker)
                appendInlineMarkup(trimmed.substring(marker.length))
            }

            trimmed.startsWith("> ") -> {
                append("│  ")
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendInlineMarkup(trimmed.removePrefix("> "))
                }
            }

            RULE_LINE.matches(line) -> append("─".repeat(RULER_WIDTH))

            else -> appendInlineMarkup(line)
        }
    }

    /** Applies inline `**bold**`, `*italic*`, `` `code` `` and `~~strike~~` spans. */
    private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInlineMarkup(text: String) {
        var i = 0
        val plain = StringBuilder()

        fun flush() {
            if (plain.isNotEmpty()) {
                append(plain.toString())
                plain.clear()
            }
        }

        while (i < text.length) {
            val rest = text.substring(i)
            val match = INLINE.find(rest)
            if (match == null || match.range.first != 0) {
                plain.append(text[i])
                i++
                continue
            }
            flush()
            val token = match.value
            val inner = when {
                token.startsWith("**") -> token.removeSurrounding("**")
                token.startsWith("__") -> token.removeSurrounding("__")
                token.startsWith("~~") -> token.removeSurrounding("~~")
                token.startsWith("`") -> token.removeSurrounding("`")
                else -> token.removeSurrounding("*")
            }
            val style = when {
                token.startsWith("**") || token.startsWith("__") ->
                    SpanStyle(fontWeight = FontWeight.Bold)
                token.startsWith("~~") ->
                    SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)
                token.startsWith("`") ->
                    SpanStyle(fontFamily = FontFamily.Monospace)
                else -> SpanStyle(fontStyle = FontStyle.Italic)
            }
            withStyle(style) { append(inner) }
            i += token.length
        }
        flush()
    }

    private val INLINE = Regex(
        "\\*\\*[^*\\n]+\\*\\*|__[^_\\n]+__|~~[^~\\n]+~~|`[^`\\n]+`|\\*[^*\\n]+\\*"
    )
    private val NUMBERED = Regex("^\\d+\\.\\s")

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
