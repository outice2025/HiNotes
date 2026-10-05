package com.hinotes.app.ui.editor

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
     * Toggles a line prefix such as `- `, `> ` or `# ` across every line the selection touches.
     * Removes the prefix when all lines already carry it, so the buttons behave as toggles.
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
        val allPrefixed = lines.all { it.startsWith(prefix) || it.isBlank() }

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
        /** A run of styled text, already carrying its inline spans. */
        data class Text(val text: AnnotatedString) : PreviewBlock

        /** A standalone `![alt](ref)` image on its own line. */
        data class Image(val reference: String, val alt: String) : PreviewBlock
    }

    private val IMAGE_LINE = Regex("^!\\[([^\\]]*)]\\(([^)\\s]+)\\)\\s*$")

    /**
     * Splits Markdown source into preview blocks.
     *
     * Inline styling is resolved to an [AnnotatedString] here; images are returned as their own
     * block so the caller can render them with Compose. Doing the split in one pass avoids
     * re-parsing in the composable and keeps the renderer free of Compose UI types.
     */
    fun blocks(source: String): List<PreviewBlock> {
        val lines = source.split('\n')
        val out = ArrayList<PreviewBlock>()
        val pending = StringBuilder()
        var inCodeBlock = false

        fun flushText() {
            if (pending.isEmpty()) return
            out += PreviewBlock.Text(render(pending.toString()))
            pending.setLength(0)
        }

        lines.forEachIndexed { index, line ->
            val imageMatch = if (inCodeBlock) null else IMAGE_LINE.matchEntire(line.trim())
            when {
                line.trimStart().startsWith("```") -> {
                    inCodeBlock = !inCodeBlock
                }

                imageMatch != null -> {
                    flushText()
                    out += PreviewBlock.Image(
                        reference = imageMatch.groupValues[2],
                        alt = imageMatch.groupValues[1],
                    )
                }

                else -> {
                    pending.append(line)
                    if (index != lines.lastIndex) pending.append('\n')
                }
            }
        }
        flushText()
        return out
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

            trimmed == "---" || trimmed == "***" -> append("─".repeat(24))

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
}
