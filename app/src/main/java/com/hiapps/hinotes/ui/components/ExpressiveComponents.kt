package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.ui.icons.SymbolIcon

/**
 * M3 Expressive connected button group: adjacent icon buttons share 8dp inner corners while the
 * group's own outer corners are fully rounded, so the toolbar reads as one connected control.
 *
 * Rendered as a [LazyRow] so a toolbar wider than the screen scrolls horizontally instead of
 * clipping or wrapping - the connected silhouette is preserved either way.
 */
@Composable
fun ConnectedIconButtonGroup(
    buttons: List<ConnectedIconButton>,
    modifier: Modifier = Modifier,
    size: ExpressiveButtonSize = ExpressiveButtonSize.Medium,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    if (buttons.isEmpty()) return
    val outer = size.height / 2
    val inner = size.innerCorner

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(buttons) { index, button ->
            val shape = when {
                buttons.size == 1 -> RoundedCornerShape(outer)
                index == 0 -> RoundedCornerShape(
                    topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner,
                )
                index == buttons.lastIndex -> RoundedCornerShape(
                    topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer,
                )
                else -> RoundedCornerShape(inner)
            }
            IconButton(
                onClick = button.onClick,
                modifier = Modifier.size(size.height),
                shape = shape,
                enabled = button.enabled,
                colors = if (button.selected) {
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    IconButtonDefaults.iconButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                        disabledContainerColor = containerColor,
                        disabledContentColor = LocalContentColor.current.copy(alpha = 0.38f),
                    )
                },
            ) {
                SymbolIcon(
                    codepoint = button.icon,
                    contentDescription = button.contentDescription,
                    size = size.iconSize,
                )
            }
        }
    }
}
