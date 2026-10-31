package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A level slider that follows the finger and commits the nearest level on release.
 *
 * The track is continuous rather than stepped. A stepped [Slider] quantises its value while the
 * thumb is being dragged, so the thumb teleports from stop to stop instead of staying under the
 * finger; and with one setting per stop, every quantised step used to be committed immediately -
 * a write through storage and back as a new app-wide type scale, which re-laid out the app
 * mid-gesture and could reset the thumb to the last committed level. So the gesture stays local
 * state: the raw track value drives the thumb, the live label and the highlighted name, and only
 * the level nearest to where the finger was lifted is handed to [onSelect]. The level names under
 * the track already spell out the stops, so the tick marks of a stepped slider are not needed.
 *
 * The stored level does not drive the display until it has caught up with the release. That wait
 * is short but not zero, and showing the previous level in that instant is exactly the jump back
 * this component exists to avoid, so the level that was just committed keeps driving the display
 * until [selected] echoes it.
 *
 * @param levels the ordered level values.
 * @param selected index of the current level.
 * @param label text for each level, drawn under its stop.
 * @param onSelect called with the nearest level index, once per gesture when it ends.
 */
@Composable
fun <T> LevelSlider(
    levels: List<T>,
    selected: Int,
    label: @Composable (T) -> String,
    onSelect: (Int) -> Unit,
    title: String,
    modifier: Modifier = Modifier,
) {
    if (levels.isEmpty()) return
    val lastIndex = levels.lastIndex
    val safeIndex = selected.coerceIn(0, lastIndex)

    // The gesture lives here, not in the setting: where the finger is on the track, whether it
    // is down at all, and the level that has been committed but has not come back through
    // storage yet.
    var dragPosition by remember { mutableFloatStateOf(safeIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    var pendingLevel by remember { mutableStateOf<Int?>(null) }

    // While the finger is down the raw position is shown, so the thumb and labels track it
    // without jumping; otherwise the committed (or still pending) level is shown.
    val shownValue = if (dragging) dragPosition else (pendingLevel ?: safeIndex).toFloat()
    val shownIndex = shownValue.roundToInt().coerceIn(0, lastIndex)
    val shownLabel = label(levels[shownIndex])

    // The write is asynchronous: hand the display back to the stored setting as soon as that
    // setting carries the level this slider last committed.
    LaunchedEffect(safeIndex, dragging) {
        if (!dragging && pendingLevel == safeIndex) {
            pendingLevel = null
        }
    }

    fun commit(position: Float) {
        dragging = false
        val index = position.roundToInt().coerceIn(0, lastIndex)
        // A second gesture can begin before the previous write has come back, so compare with
        // the level already shown as well as with the stored one - otherwise that second gesture
        // would be silently dropped.
        if (index == (pendingLevel ?: safeIndex)) return
        pendingLevel = index
        onSelect(index)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = shownLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Slider(
            value = shownValue,
            onValueChange = { position ->
                dragging = true
                dragPosition = position.coerceIn(0f, lastIndex.toFloat())
            },
            onValueChangeFinished = {
                // Lifting the finger and tapping the track both end here, which makes this the
                // single place that writes the setting: one commit, one theme update, no write
                // per step of the gesture.
                commit(dragPosition)
            },
            valueRange = 0f..lastIndex.toFloat(),
            // Deliberately no `steps`: that is what quantised the value mid-drag. The release
            // snaps to the nearest level instead, and the level names below mark the stops.
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = title
                    // The finger gets a continuous track, but assistive technology keeps the
                    // discrete levels: a swipe moves one level and the level name is announced,
                    // which is what the stepped slider expressed through its `steps`.
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = shownIndex.toFloat(),
                        range = 0f..lastIndex.toFloat(),
                        steps = (lastIndex - 1).coerceAtLeast(0),
                    )
                    stateDescription = shownLabel
                    setProgress { target ->
                        // An accessibility adjustment is a complete gesture on its own, so it is
                        // shown and committed at once instead of waiting for a release.
                        commit(target)
                        true
                    }
                },
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            levels.forEachIndexed { index, level ->
                Text(
                    text = label(level),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == shownIndex) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = 2.dp),
                )
            }
        }
    }
}
