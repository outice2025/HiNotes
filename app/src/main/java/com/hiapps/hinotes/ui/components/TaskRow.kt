package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiapps.hinotes.ui.editor.Markdown

/**
 * The slot a task's box gets beside its text.
 *
 * Material 3's own minimum interactive size is 48dp, which is right for a settings switch and heavy
 * for a line of a note: at the note's line height - 24dp at the default type size - a one-line task
 * stood a third taller than the prose around it, and a ten-item checklist came to 480dp of
 * scrolling. 24dp is that line height, so a task line is exactly as tall as the text beside it,
 * which is the spacing the reference for this style shows.
 */
private val TaskBoxTarget = 24.dp

/** The gap between a task's box and the text of the item. */
private val TaskBoxGap = 8.dp

/**
 * The width a task's box takes when it is drawn *inside* a line of text rather than beside it.
 *
 * The box itself is 18dp, so the rest of the slot is the gap before the item's text - the same
 * grouping a row gives it, for a body that is one run of text. It is measured in the text's own
 * units rather than in dp, because the slot stands in the line the box sits on: it has to grow with
 * the note's type size the way that line does.
 */
private val TaskBoxInlineWidth = 32.sp

/** How tall the slot for a box drawn inside a line of text is; see [TaskBoxPitch]. */
private val TaskBoxInlineHeight = 30.sp

/**
 * The line pitch a body that draws its task boxes inside the text needs.
 *
 * A box drawn inside a line of text is as tall as the line it stands in, so a body whose lines were
 * the height of the box itself came out as a column of boxes that touched: the box Material 3 draws
 * is 18dp high, and the lines of a 14sp body are barely more than that apart. A card therefore sets
 * its body's line height to this pitch, which is the same 24dp the editor gives one of its task
 * rows - one box per line, with the same gap between one item and the next as a page has.
 *
 * The pitch is a length in dp rather than in the text's own units, because the box it has to make
 * room for is 18dp whatever the reader has set the app's text size to: a pitch measured in the
 * text's units would move the lines apart with the type without ever changing the gap.
 */
internal val TaskBoxPitch = 24.dp

/**
 * One task-list line: a Material 3 checkbox beside the item's text.
 *
 * The editor's preview draws its checklist with this, where the box writes the tick back into the
 * note. A card draws the same box inside the text itself instead: see [taskInlineContent].
 *
 * @param enabled false for a note that cannot be written - a locked one - where the box reports the
 *   state the note records without offering to change it.
 */
@Composable
fun MarkdownTaskRow(
    checked: Boolean,
    text: AnnotatedString,
    style: TextStyle,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // One announcement per row: a screen reader reads the item's text together with the
            // state it is in, where a bare "checkbox" on its own says nothing about what is ticked.
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides TaskBoxTarget) {
            Checkbox(
                checked = checked,
                onCheckedChange = if (enabled) onToggle else null,
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

/**
 * A task's box as inline content, for a body drawn as one run of text.
 *
 * A note card shows a note the way a page does, so its task lines are lines of that page rather
 * than a column of rows: the box is drawn wherever the renderer left a slot for it (see
 * [Markdown.TaskBox.Inline]).
 *
 * A box is a control, not a picture: tapping it writes the tick back to the task line its place
 * stands for, which is the line [Markdown.TaskSlot.line] carries. A caller that cannot write to the
 * note - the card of a locked one - passes no callback and gets the same boxes drawn without a tap.
 *
 * The result is stable and cheap to build, but it is still built once per body and remembered there
 * rather than here: what it holds is a pair of composables, and a caller that rebuilt them on every
 * frame would give the text a new inline content to lay out each time.
 */
internal fun taskInlineContent(
    slots: List<Markdown.TaskSlot>,
    onToggle: ((Markdown.TaskSlot) -> Unit)?,
): Map<String, InlineTextContent> = slots.associate { slot ->
    slot.id to taskBox(slot.checked) { onToggle?.invoke(slot) }
}

private fun taskBox(checked: Boolean, onClick: (() -> Unit)?): InlineTextContent = InlineTextContent(
    placeholder = Placeholder(
        width = TaskBoxInlineWidth,
        height = TaskBoxInlineHeight,
        placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
    ),
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides TaskBoxTarget) {
            // The box itself carries the tap rather than the slot around it: it is the one part of
            // the line a screen reader should announce as a checkbox, and the tick has to be
            // reported with the item's text.
            Checkbox(
                checked = checked,
                onCheckedChange = onClick?.let { toggle -> { _: Boolean -> toggle() } },
            )
        }
    }
}
