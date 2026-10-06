package com.hiapps.hinotes.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.HiNotesCorners
import com.hiapps.hinotes.ui.theme.LocalMotionScheme

/** One entry in a [SplitButton]'s menu. */
data class SplitButtonMenuItem(
    val icon: Int,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * M3 Expressive split button: a primary action plus a menu, sharing one silhouette.
 *
 * The two segments are 16dp-rounded squares separated by a 2dp seam, which is what makes them
 * read as one control split in two rather than as two buttons that happen to sit together. The
 * primary segment is square (44dp, the same height as the back button beside it); the menu
 * segment is narrower because all it carries is the chevron.
 *
 * Colours follow the icon buttons in the same row - a `surfaceContainerHigh` container, with the
 * primary action tinted `primary` - so the save action stays the emphasised one without
 * introducing a second visual language into the editor's header.
 */
@Composable
fun SplitButton(
    primaryIcon: Int,
    primaryContentDescription: String?,
    onPrimaryClick: () -> Unit,
    menuItems: List<SplitButtonMenuItem>,
    menuContentDescription: String?,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    corner: Dp = HiNotesCorners.ExtendedFab,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    primaryContentColor: Color = MaterialTheme.colorScheme.primary,
    menuContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var expanded by remember { mutableStateOf(false) }
    val motion = LocalMotionScheme.current

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.fastEffects,
        label = "splitButtonArrow",
    )

    Row(
        modifier = modifier.height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onPrimaryClick,
            modifier = Modifier.size(height),
            shape = RoundedCornerShape(corner),
            color = containerColor,
            contentColor = primaryContentColor,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SymbolIcon(
                    codepoint = primaryIcon,
                    contentDescription = primaryContentDescription,
                    size = SplitIconSize,
                )
            }
        }

        Spacer(Modifier.width(HiNotesCorners.SplitSeam))

        Box {
            Surface(
                onClick = { expanded = true },
                modifier = Modifier
                    .width(SplitMenuWidth)
                    .height(height),
                shape = RoundedCornerShape(corner),
                color = containerColor,
                contentColor = menuContentColor,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SymbolIcon(
                        codepoint = Symbols.KeyboardArrowDown,
                        contentDescription = menuContentDescription,
                        size = SplitIconSize,
                        modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                menuItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label, style = MaterialTheme.typography.bodyLarge) },
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                        leadingIcon = {
                            SymbolIcon(
                                codepoint = item.icon,
                                contentDescription = null,
                                size = 24.dp,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** Glyph size inside a split button segment. */
private val SplitIconSize = 24.dp

/** Width of the menu segment: a comfortable target for the chevron, narrower than the primary. */
private val SplitMenuWidth = 34.dp

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
