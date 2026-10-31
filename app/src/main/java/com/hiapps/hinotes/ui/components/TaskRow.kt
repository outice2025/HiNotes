package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

/**
 * The slot a task's box gets beside its text.
 *
 * Material 3's own minimum interactive size is 48dp, which is right for a settings switch and heavy
 * for a line of a note: at the note's line height - 24dp at the default type size - a one-line task
 * stood a third taller than the prose around it, and a ten-item checklist came to 480dp of
 * scrolling. 24dp is that line height, so a task line is exactly as tall as the text beside it,
 * which is the spacing the reference for this style shows.
 */
internal val TaskBoxTarget = 24.dp

/** The gap between a task's box and the text of the item. */
internal val TaskBoxGap = 8.dp

/**
 * The line pitch a note's body is set to.
 *
 * A box is 20dp high and the lines of a 14sp body are barely more than that apart, so a checklist
 * set at the body's own line height came out as a column of boxes that touched. The pitch is the
 * same 24dp the editor gives one of its task rows - one box per line, with the same gap between one
 * item and the next as a page has - and it is a length in dp rather than in the text's units,
 * because the box it has to make room for is a fixed length whatever size the reader has set the
 * text to.
 */
internal val TaskBoxPitch = 24.dp

/**
 * The leading a text without its own line height is laid out at, as a multiple of its size.
 *
 * Used only to find the first line of such a style so the box can stand on it; the platform's own
 * leading is a little above this, which moves a box by a hair rather than leaving it off the line.
 */
private const val DefaultLineHeightRatio = 1.4f

/**
 * One task-list line a reader can tick: a Material 3 checkbox beside the item's text.
 *
 * Both ways of showing a note draw this same row: a note's card, the editor's reading mode and the
 * widget's own shape of the row all put the box where this one does, which is what keeps a checklist
 * looking the same wherever it is read.
 *
 * The box stands on the item's *first* line rather than in the middle of the row. An item longer
 * than the card's width wraps onto several lines, and centring the box on the whole row put it
 * somewhere in the middle of the sentence, level with a line the reader had not begun to tick: the
 * box belongs beside the line the item starts on, whichever line the eye then reads. The row is
 * therefore top-aligned, and the box is pushed down by however much the first line is taller than
 * the slot the box occupies - a line set at 24dp with a 24dp slot needs no push, while a larger note
 * type size moves the box with the line.
 *
 * @param onToggle what a tap on the box writes back; null for a page that cannot be written - a
 *   locked note - where the box keeps Material 3's disabled colours and draws the state the note
 *   records without offering to change it.
 */
@Composable
fun MarkdownTaskRow(
    checked: Boolean,
    text: AnnotatedString,
    style: TextStyle,
    onToggle: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // How far the first line's centre sits below the slot's own centre. Both lengths are taken in
    // pixels because one is the text's line height and the other the box's target size, and only
    // their difference turns into the dp the modifier wants.
    val push = with(LocalDensity.current) {
        val lineHeightPx = if (style.lineHeight.isSpecified) {
            style.lineHeight.toPx()
        } else {
            // Text that names no line height is set at the platform's own leading, which is close
            // enough to what the text engine does to keep the box on the first line.
            (style.fontSize * DefaultLineHeightRatio).toPx()
        }
        ((lineHeightPx - TaskBoxTarget.toPx()) / 2f).coerceAtLeast(0f).toDp()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // One announcement per row: a screen reader reads the item's text together with the
            // state it is in, where a bare "checkbox" on its own says nothing about what is ticked.
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.Top,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides TaskBoxTarget) {
            Checkbox(
                checked = checked,
                onCheckedChange = onToggle,
                modifier = Modifier.offset(y = push),
            )
        }
        Spacer(Modifier.width(TaskBoxGap))
        MarkdownText(
            text = text,
            style = style,
            modifier = Modifier.weight(1f),
        )
    }
}
