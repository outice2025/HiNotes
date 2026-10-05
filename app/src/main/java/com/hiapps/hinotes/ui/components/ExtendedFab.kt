package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/** Extended FAB geometry, per the design: 56dp tall, 16dp radius. */
private val FabHeight = 56.dp
private val FabHorizontalPadding = 20.dp
private val FabIconSize = 24.dp
private val FabIconLabelGap = 12.dp

/** Tighter horizontal padding, for a narrow pill such as the delete action. */
private val FabPillHorizontalPadding = 16.dp

/**
 * Label size for the extended FAB.
 *
 * Material 3's extended FAB defaults to 16sp, which reads a size too large against a 24dp icon
 * at this size; 14sp matches the visual weight of the other primary actions.
 */
private val FabLabelSize = 14.sp

/**
 * An extended FAB: icon leading, label trailing, 56dp tall.
 *
 * Built directly on [Surface] rather than Material 3's `ExtendedFloatingActionButton` so the
 * icon and label share one explicit [Row]: both are centred on the same vertical axis by
 * construction, instead of depending on the container's internal layout.
 *
 * @param pill round the corners fully; used by the delete action so it reads as a different
 *   kind of button from "Create note".
 */
@Composable
fun ExtendedFab(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    pill: Boolean = false,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(FabHeight),
        shape = if (pill) {
            androidx.compose.foundation.shape.CircleShape
        } else {
            RoundedCornerShape(HiNotesCorners.ExtendedFab)
        },
        color = containerColor,
        contentColor = contentColor,
    ) {
        // Wraps its content: filling the available space here would stretch the button across
        // the whole screen, because the Scaffold hands the FAB slot unbounded width.
        Row(
            modifier = Modifier
                .wrapContentSize()
                .padding(
                    horizontal = if (pill) FabPillHorizontalPadding else FabHorizontalPadding,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The glyph is centred inside its own square, so it shares the Row's centre line.
            Box(
                modifier = Modifier.size(FabIconSize),
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(
                    codepoint = icon,
                    contentDescription = null,
                    size = FabIconSize,
                    tint = LocalContentColor.current,
                )
            }
            Spacer(Modifier.width(FabIconLabelGap))
            Text(
                text = label,
                fontSize = FabLabelSize,
                // Line height equal to the size keeps the text box flush with the icon box,
                // so neither side nudges the shared centre line.
                lineHeight = FabLabelSize,
                color = LocalContentColor.current,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
