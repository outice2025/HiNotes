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

/** Extended FAB geometry, per the design: 56dp tall, 20dp corners. */
private val FabHeight = 56.dp
private val FabHorizontalPadding = 20.dp
private val FabIconSize = 24.dp
private val FabIconLabelGap = 12.dp

/**
 * Label size for the extended FAB.
 *
 * Material 3's extended FAB defaults to 16sp, which reads a size too large against a 24dp icon
 * at this size; 14sp matches the visual weight of the other primary actions.
 */
private val FabLabelSize = 14.sp

/**
 * The home screen's action button: icon leading, optional label trailing, 56dp tall.
 *
 * Built directly on [Surface] rather than Material 3's `ExtendedFloatingActionButton` so the
 * icon and label share one explicit [Row]: both are centred on the same vertical axis by
 * construction, instead of depending on the container's internal layout.
 *
 * @param label the text beside the icon, or null for an icon-only button. An icon-only button
 *   keeps the same height and the same corner radius and squares itself off, so a row of one
 *   labelled and one icon-only action still lines up.
 * @param contentDescription the accessibility name; required when [label] is null, because the
 *   icon alone says nothing to a screen reader.
 */
@Composable
fun ExtendedFab(
    icon: Int,
    label: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(FabHeight),
        shape = RoundedCornerShape(HiNotesCorners.ExtendedFab),
        color = containerColor,
        contentColor = contentColor,
    ) {
        // Wraps its content: filling the available space here would stretch the button across
        // the whole screen, because the Scaffold hands the FAB slot unbounded width.
        Row(
            modifier = Modifier
                .wrapContentSize()
                .padding(horizontal = if (label == null) 0.dp else FabHorizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The glyph is centred inside its own square, so it shares the Row's centre line.
            // Without a label that square is the whole button, which is what keeps an icon-only
            // action exactly as tall and as wide as its corner radius expects.
            Box(
                modifier = if (label == null) {
                    Modifier.size(FabHeight)
                } else {
                    Modifier.size(FabIconSize)
                },
                contentAlignment = Alignment.Center,
            ) {
                SymbolIcon(
                    codepoint = icon,
                    contentDescription = contentDescription,
                    size = FabIconSize,
                    tint = LocalContentColor.current,
                )
            }
            if (label != null) {
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
}
