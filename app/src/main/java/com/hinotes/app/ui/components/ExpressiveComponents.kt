package com.hinotes.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.hinotes.app.ui.icons.SymbolIcon
import com.hinotes.app.ui.icons.Symbols
import com.hinotes.app.ui.theme.HiNotesCorners
import com.hinotes.app.ui.theme.LocalMotionScheme

/**
 * M3 Expressive split button: a primary segment plus a 2dp-separated menu segment.
 *
 * Both segments are pill-shaped on their outer edge (height / 2) and carry an 8dp corner on the
 * inner edges, which is what makes the pair read as one control split in two. Opening the menu
 * rotates the dropdown arrow, driven by the motion scheme's fast effects spring.
 */
@Composable
fun SplitButton(
    primaryIcon: Int,
    primaryContentDescription: String?,
    onPrimaryClick: () -> Unit,
    menuContentDescription: String?,
    menuItems: List<SplitButtonMenuItem>,
    modifier: Modifier = Modifier,
    size: ExpressiveButtonSize = ExpressiveButtonSize.Medium,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    var expanded by remember { mutableStateOf(false) }
    val outer = size.height / 2
    val inner = size.innerCorner
    val motion = LocalMotionScheme.current

    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.fastEffects,
        label = "splitButtonArrow",
    )

    Box(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Primary segment: pill on the outside, 8dp where it meets the menu segment.
            Surface(
                onClick = onPrimaryClick,
                modifier = Modifier.height(size.height),
                shape = RoundedCornerShape(
                    topStart = outer,
                    bottomStart = outer,
                    topEnd = inner,
                    bottomEnd = inner,
                ),
                color = containerColor,
                contentColor = contentColor,
                tonalElevation = 0.dp,
            ) {
                Box(
                    modifier = Modifier.size(size.height),
                    contentAlignment = Alignment.Center,
                ) {
                    SymbolIcon(
                        codepoint = primaryIcon,
                        contentDescription = primaryContentDescription,
                        size = size.iconSize,
                    )
                }
            }

            Spacer(Modifier.width(HiNotesCorners.SplitSeam))

            Box {
                Surface(
                    onClick = { expanded = true },
                    modifier = Modifier.height(size.height),
                    shape = RoundedCornerShape(
                        topStart = inner,
                        bottomStart = inner,
                        topEnd = outer,
                        bottomEnd = outer,
                    ),
                    color = containerColor,
                    contentColor = contentColor,
                    tonalElevation = 0.dp,
                ) {
                    Box(
                        modifier = Modifier.size(size.height),
                        contentAlignment = Alignment.Center,
                    ) {
                        SymbolIcon(
                            codepoint = Symbols.KeyboardArrowDown,
                            contentDescription = menuContentDescription,
                            size = size.iconSize,
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
}

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
