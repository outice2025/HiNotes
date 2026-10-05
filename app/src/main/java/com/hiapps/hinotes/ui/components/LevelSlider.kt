package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * A discrete level slider.
 *
 * Material 3's own [Slider] with `steps = levels.size - 2`, so the thumb snaps to exactly one
 * stop per level: nothing to type, nothing to open, and the choice is visible at a glance. The
 * level names sit under the stops, and the live value is echoed in the label row so the current
 * setting is readable without counting ticks.
 *
 * @param levels the ordered level values.
 * @param selected index of the current level.
 * @param label text for each level, drawn under its stop.
 * @param onSelect called with the new index.
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
                text = label(levels[safeIndex]),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Slider(
            value = safeIndex.toFloat(),
            onValueChange = { onSelect(it.toInt().coerceIn(0, lastIndex)) },
            valueRange = 0f..lastIndex.toFloat(),
            // `steps` counts the stops *between* the ends, so N levels need N-2 of them.
            steps = (lastIndex - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = title },
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            levels.forEachIndexed { index, level ->
                Text(
                    text = label(level),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (index == safeIndex) {
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

/**
 * The four-level scale shared by the app and note type settings.
 *
 * Used for both the size and the weight sliders so the two read as the same control.
 */
@Composable
fun LevelSliderRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
