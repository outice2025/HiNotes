package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/**
 * The frame both single-choice pickers share: the language list and the accent colour list.
 *
 * The reference the design asks for is a tall, nearly-screen-wide card with the title in its top
 * left corner, a scrolling column of radio rows, and one 取消 button in the bottom right. Measured
 * on the 1080x2400 reference screenshots, which run at 400dpi (2.5x, so the screen is 432x960dp):
 *
 * - the card is the screen width less 28dp at each side (measured 374.4dp wide), and 28dp round
 *   corners (fitted 27.6dp - the same [HiNotesCorners.Dialog] the app's other dialogs use);
 * - the title sits 24dp from the start and 16dp from the top, at [headlineSmall];
 * - an option row is 48dp tall (measured 48.8dp) with 16dp at each side, a 20dp radio button whose
 *   Material touch target makes the circle's centre land 40dp from the card's start, and a
 *   [bodyLarge] name about 12dp after that target;
 * - 取消 is one text button at the end, 24dp from the end and 16dp from the bottom.
 *
 * The card is as tall as its rows and never taller than the screen: past that the list scrolls, so
 * a long accent list stays on one screen while a three-item language list does not leave a hollow
 * card. [PickerMaxWidth] is Material's own ceiling for dialogs; on a phone the reference's
 * screen-less-margins width is narrower than it anyway.
 */
@Composable
internal fun PickerDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    // The content below fills the dialog window so the card can keep the reference's edge margins.
    // That also defeats the dialog's own outside-click handling, which measures the composition's
    // bounds: the empty area around the card is still "inside". The tap is handled here instead,
    // and `dismiss` keeps that handler current without restarting it on every recomposition.
    val dismiss by rememberUpdatedState(onDismiss)

    // A platform-width dialog would ignore the reference's edge margins: the default window insets
    // the content to at most 560dp and centres it, while the reference is the screen minus 28dp.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                // Placed before the padding so the 28dp/24dp gaps around the card count too. A
                // tap here means the same as 取消: close, leaving an applied choice in place.
                .pointerInput(Unit) {
                    detectTapGestures { dismiss() }
                }
                .padding(horizontal = PickerSideMargin, vertical = PickerVerticalMargin),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = PickerMaxWidth)
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    // A tap on the card is not a dismissal: swallow it before the handler around
                    // the card sees it. Rows and the cancel button are hit first and still work.
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitFirstDown().consume() }
                    },
                shape = RoundedCornerShape(HiNotesCorners.Dialog),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = PickerTitleStart,
                                end = PickerTitleStart,
                                top = PickerTitleTop,
                                bottom = PickerTitleBottom,
                            ),
                    )
                    // fill = false keeps the list at its content's height when it fits, so a short
                    // list gives a short card instead of stretching it to the screen.
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .selectableGroup(),
                        content = content,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                end = PickerActionEnd,
                                top = PickerActionTop,
                                bottom = PickerActionBottom,
                            ),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    }
                }
            }
        }
    }
}

/**
 * One option of a [PickerDialog]: the radio button and the option's own name.
 *
 * The row is the selection target, not just the button, so a tap anywhere on the 48dp line chooses
 * the option - the same target the settings rows give their switches. The 48dp is a floor rather
 * than a fixed height, so a user running the app's larger type sizes gets a taller row instead of
 * a clipped label.
 */
@Composable
internal fun PickerRow(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = PickerRowHeight)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(start = PickerRowStart, end = PickerRowEnd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row owns the click and the role, so the button must not be a second target.
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(PickerRadioGap))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val PickerSideMargin = 28.dp
private val PickerVerticalMargin = 24.dp
private val PickerMaxWidth = 560.dp
private val PickerTitleStart = 24.dp
private val PickerTitleTop = 16.dp
private val PickerTitleBottom = 8.dp
private val PickerRowHeight = 48.dp

/**
 * How far inside the card an option row starts.
 *
 * The same 24dp the title starts at, so a row's radio button and the title above it stand on one
 * line down the card's left side. The reference screenshots measured the row's own inset at 16dp,
 * which read as too far left against the title once the card was on screen; the row and the title
 * are one column here.
 */
private val PickerRowStart = 24.dp
private val PickerRowEnd = 16.dp
private val PickerRadioGap = 12.dp
private val PickerActionEnd = 24.dp
private val PickerActionTop = 16.dp
private val PickerActionBottom = 16.dp
