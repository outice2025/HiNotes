package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
// The delegated state of the menu's open flag reads as a plain property: these are what make the
// `by` do its work.
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols

/**
 * One entry in the editor's "more" menu.
 *
 * @param destructive paints the entry in the error colour, the way Material 3 marks an action
 *   that removes something.
 * @param enabled whether the action is available now; a disabled entry is dimmed and takes no tap.
 */
data class EditorMenuItem(
    val icon: Int,
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * The two actions at the note's right edge: save, and the menu of everything else.
 *
 * They were one split control, and the menu half could only be found by pressing the half that was
 * not - so they are two buttons now, drawn exactly like the find button beside them: a 44dp circle,
 * no container, one content colour. The save half is left out entirely while the app saves by
 * itself, because a button that repeats what leaving the note already does is a button that asks to
 * be pressed for nothing.
 *
 * @param showSave whether the save button is drawn at all; the menu is always there.
 */
@Composable
fun EditorActionButtons(
    saveContentDescription: String,
    onSave: () -> Unit,
    showSave: Boolean,
    moreContentDescription: String,
    menuItems: List<EditorMenuItem>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.height(EditorActionSize),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSave) {
            EditorActionButton(
                icon = Symbols.Check,
                contentDescription = saveContentDescription,
                onClick = onSave,
            )
        }

        Box {
            EditorActionButton(
                icon = Symbols.MoreVert,
                contentDescription = moreContentDescription,
                onClick = { expanded = true },
            )

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                menuItems.forEach { item ->
                    val tint = when {
                        // Dimmed by hand: the entry's own label and glyph carry an explicit
                        // colour, which would otherwise override the menu item's disabled state.
                        !item.enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        item.destructive -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    DropdownMenuItem(
                        text = {
                            Text(item.label, style = MaterialTheme.typography.bodyLarge, color = tint)
                        },
                        onClick = {
                            expanded = false
                            item.onClick()
                        },
                        enabled = item.enabled,
                        leadingIcon = {
                            SymbolIcon(
                                codepoint = item.icon,
                                contentDescription = null,
                                size = 24.dp,
                                tint = tint,
                            )
                        },
                    )
                }
            }
        }
    }
}

/** One action of the editor's header: a 44dp circle with no container of its own. */
@Composable
private fun EditorActionButton(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(EditorActionSize),
        shape = RoundedCornerShape(percent = 50),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        SymbolIcon(codepoint = icon, contentDescription = contentDescription, size = 24.dp)
    }
}

/** The size of one action in the editor's header, which is the back button's own height. */
private val EditorActionSize = 44.dp

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
