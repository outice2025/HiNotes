package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * list as it does when opened. When Markdown is switched off in settings the body is shown
 * verbatim, which is what "the note is plain text" means.
 *
 * @param maxLines how much of the body to show before ellipsising; pass [Int.MAX_VALUE] for all
 *   of it.
 */
@Composable
fun MarkdownPreview(
    content: String,
    markdownEnabled: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val rendered: AnnotatedString = remember(content, markdownEnabled) {
        if (markdownEnabled && content.isNotBlank()) {
            Markdown.render(content)
        } else {
            AnnotatedString(content)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                text = rendered,
                style = style,
                color = color,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
