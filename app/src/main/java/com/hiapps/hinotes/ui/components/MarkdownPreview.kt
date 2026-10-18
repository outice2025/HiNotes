package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.hiapps.hinotes.ui.editor.Markdown

/**
 * A note body rendered as Markdown.
 *
 * Uses the same [Markdown.render] the editor's preview mode does, so a note looks the same in the
 * list as it does when opened - down to the rounded background behind a run of code and the real
 * checkbox a task line carries (see [MarkdownText] and [taskInlineContent]). With Markdown switched
 * off in settings the body is shown verbatim, which is what "the note is plain text" means.
 *
 * A picture is the one thing a card does not draw: the address the note holds for it is shown
 * instead, because a card is a few lines of a note rather than a page, and a picture in a list of
 * cards pushes what identifies them off the screen. The editor's preview draws it.
 *
 * The text is deliberately *not* inside a `SelectionContainer`. It sits on a card whose gesture is
 * a long press, and a selection container claims that same gesture: holding a card put the note
 * under a text-selection handle instead of starting multi-select. Copying is served by the
 * editor's preview and by sharing the note, both of which are explicit actions.
 *
 * @param maxLines how much of the body to show; what does not fit is cut off, without the
 *   ellipsis a truncated sentence would carry. Pass [Int.MAX_VALUE] for all of it.
 * @param onToggleTask called with the source line of a task and the state it should be written as,
 *   when the user taps one of the body's checkboxes; null for a note that cannot be written, whose
 *   boxes are then drawn without a tap.
 */
@Composable
fun MarkdownPreview(
    content: String,
    markdownEnabled: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onToggleTask: ((Int, Boolean) -> Unit)? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val body: Markdown.Body = remember(content, markdownEnabled, linkColor) {
        if (markdownEnabled && content.isNotBlank()) {
            Markdown.renderBody(content, linkColor)
        } else {
            // Plain text has no task lines, so nothing asks to be filled in.
            Markdown.Body(AnnotatedString(content), emptyList())
        }
    }
    val boxes = remember(body, onToggleTask) {
        taskInlineContent(body.slots) { slot ->
            onToggleTask?.invoke(slot.line, !slot.checked)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        MarkdownText(
            text = body.text,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Clip,
            inlineContent = boxes,
        )
    }
}
