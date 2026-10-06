package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/** Standard Material 3 confirmation dialog used for every destructive action. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(HiNotesCorners.GroupOuter),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

/**
 * The app mark shown on the About screen and the lock screen.
 *
 * Circular, and drawn from the very same `ic_logo_note` vector the launcher icon is generated
 * from, so the in-app identity and the home-screen icon are the same shape.
 *
 * The glyph occupies 40% of the badge's diameter. The mark has been asked for smaller twice now,
 * so the number is stated here rather than buried: the badge itself - its size, its colour and
 * its shape - is untouched, only the artwork inside it shrinks.
 */
@Composable
fun AppLogoPlaceholder(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 95.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(
                    id = com.hiapps.hinotes.R.drawable.ic_logo_note,
                ),
                contentDescription = null,
                modifier = Modifier.size(size * 0.40f),
            )
        }
    }
}

/**
 * A note card used on the home screen.
 *
 * Long-press enters multi-select; when a selection is active the card shows a tick and its
 * selected state, and a tap toggles selection instead of opening the note.
 *
 * The card carries no timestamp: a note is recognised by what it says, and the date belongs in
 * the editor's properties sheet, where there is room to show it exactly.
 *
 * The shape is applied to the *modifier* as well as to the card's own background, so the ripple
 * and the long-press highlight are clipped to the rounded corners. Clipping only the background
 * left those overlays as sharp rectangles sitting on the corners of every card.
 *
 * @param content the note body, rendered as Markdown when [markdownEnabled].
 * @param compact a denser layout for the grid arrangement, where cards are half as wide.
 */
@Composable
fun NoteCard(
    title: String,
    content: String,
    markdownEnabled: Boolean,
    locked: Boolean,
    lockedLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selecting: Boolean = false,
    selected: Boolean = false,
    compact: Boolean = false,
    bodyMaxLines: Int = if (compact) 6 else 3,
) {
    val shape = RoundedCornerShape(HiNotesCorners.Card)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Column(Modifier.padding(if (compact) 12.dp else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selecting) {
                    SymbolIcon(
                        codepoint = if (selected) Symbols.Check else Symbols.CheckBoxOutlineBlank,
                        contentDescription = null,
                        size = 20.dp,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = if (compact) {
                        MaterialTheme.typography.titleSmall
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    SymbolIcon(
                        codepoint = Symbols.Lock,
                        contentDescription = lockedLabel,
                        size = 16.dp,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (content.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                MarkdownPreview(
                    content = content,
                    markdownEnabled = markdownEnabled,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = bodyMaxLines,
                )
            }
        }
    }
}
