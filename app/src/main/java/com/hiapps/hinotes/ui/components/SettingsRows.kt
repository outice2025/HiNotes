package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/** Row metrics, all taken from the style reference the settings list was revised to. */
private val RowMinHeight = 76.dp
private val RowSidePadding = 16.dp
private val RowVerticalPadding = 16.dp
private val RowIconGap = 16.dp
private val RowTrailingGap = 12.dp
private val LeadingGlyphSize = 24.dp

/**
 * The corner treatment for one row inside a grouped list.
 *
 * Material 3's connected list look: the group's own outer corners are fully rounded (28dp) and
 * rows that touch a neighbour use the smaller 8dp inner corner on that side, so a run of rows
 * reads as one continuous block with no gaps in its silhouette.
 */
private fun groupedRowShape(isFirst: Boolean, isLast: Boolean): Shape {
    val outer = HiNotesCorners.GroupOuter
    val inner = HiNotesCorners.GroupInner
    return RoundedCornerShape(
        topStart = if (isFirst) outer else inner,
        topEnd = if (isFirst) outer else inner,
        bottomStart = if (isLast) outer else inner,
        bottomEnd = if (isLast) outer else inner,
    )
}

/**
 * An M3 Expressive list row.
 *
 * Measured against the style reference the list was revised to: a bare 24dp glyph on the left -
 * no badge behind it - a `titleMedium` headline, a `bodySmall` supporting line, and a row that is
 * as tall as its own text rather than pinned to a fixed height, so a two-line row lands near
 * 76dp and a three-line one near 88dp instead of everything being squeezed into 72dp. The glyph
 * is vertically centred on the row, which is where the reference puts it.
 *
 * @param isFirst whether this row starts its group (rounded top corners).
 * @param isLast whether this row ends its group (rounded bottom corners).
 * @param enabled whether the row's action can be taken; a disabled row neither ripples nor
 *   reports itself as clickable.
 */
@Composable
fun SettingsRow(
    icon: Int,
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    enabled: Boolean = true,
    isFirst: Boolean = true,
    isLast: Boolean = true,
) {
    val shape = groupedRowShape(isFirst, isLast)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RowMinHeight)
                .padding(horizontal = RowSidePadding, vertical = RowVerticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LeadingGlyph(icon = icon)
            Spacer(Modifier.width(RowIconGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(RowTrailingGap))
                trailing()
            }
        }
    }

    // A row with an action is a real M3 clickable Surface; a row without one is a plain
    // container, so it neither ripples nor reports itself as a button to accessibility.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            enabled = enabled,
        ) { content() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
        ) { content() }
    }
}

/** The row's leading glyph: 24dp of Material Symbols, tinted like the supporting text. */
@Composable
private fun LeadingGlyph(icon: Int) {
    SymbolIcon(
        codepoint = icon,
        contentDescription = null,
        size = LeadingGlyphSize,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A settings row with a trailing [androidx.compose.material3.Switch].
 *
 * The whole row is the toggle target, which is what the accessibility guidance for switch rows
 * recommends, while the switch itself stays a standard M3 component.
 */
@Composable
fun SwitchRow(
    icon: Int,
    headline: String,
    supporting: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isFirst: Boolean = true,
    isLast: Boolean = true,
) {
    SettingsRow(
        icon = icon,
        headline = headline,
        supporting = supporting,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        enabled = enabled,
        isFirst = isFirst,
        isLast = isLast,
    )
}

/**
 * M3 Expressive button sizes.
 *
 * The Compose Material 3 artifact on this build line does not yet ship every Expressive
 * composable (some exist only as design tokens), so the ones the app needs are assembled from
 * standard M3 building blocks - [Surface], [IconButton] and M3 motion - rather than being
 * redrawn from scratch. Every colour comes from a Material 3 role.
 */
enum class ExpressiveButtonSize(
    val height: androidx.compose.ui.unit.Dp,
    val iconSize: androidx.compose.ui.unit.Dp,
    val innerCorner: androidx.compose.ui.unit.Dp,
) {
    XSmall(32.dp, 20.dp, 8.dp),
    Small(40.dp, 24.dp, 8.dp),

    /**
     * 48dp: the editor toolbar's size from the design. Seven of these plus their seams come to
     * 348dp, which fits the 380dp canvas of a 412dp screen - the whole toolbar is reachable
     * without scrolling, where the 56dp Medium size overflowed it by half a button.
     */
    Compact(48.dp, 24.dp, 8.dp),
    Medium(56.dp, 24.dp, 8.dp),
    Large(96.dp, 32.dp, 12.dp),
    XLarge(136.dp, 40.dp, 16.dp),
}

/**
 * One button inside a [ConnectedIconButtonGroup].
 */
data class ConnectedIconButton(
    val icon: Int,
    val contentDescription: String,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** A vertical run of settings rows, with 3dp between neighbours. */
@Composable
fun SettingsSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HiNotesCorners.GroupGap),
        content = content,
    )
}

/** Vertical space between two [SettingsSection]s. */
@Composable
fun SettingsSectionGap(height: androidx.compose.ui.unit.Dp = SectionGap) {
    Spacer(Modifier.height(height))
}

/**
 * Space between two groups of rows.
 *
 * Slightly wider than it was. On the reference device the gap between two groups measured 16dp
 * against the 3dp seam inside a group and read as a single list; 20dp separates the groups without
 * breaking the page into separate cards. The seam itself is unchanged - it is what makes a group
 * look connected.
 */
private val SectionGap = 20.dp
