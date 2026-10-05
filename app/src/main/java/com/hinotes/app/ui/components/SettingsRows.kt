package com.hinotes.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hinotes.app.ui.icons.SymbolIcon
import com.hinotes.app.ui.theme.HiNotesCorners

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
 * Matches the brief's measurements: 72dp tall, 24dp leading glyph on a 40dp `primaryContainer`
 * circle, `bodyLarge` headline and `bodyMedium` `onSurfaceVariant` supporting line, with an
 * optional trailing slot.
 *
 * @param isFirst whether this row starts its group (rounded top corners).
 * @param isLast whether this row ends its group (rounded bottom corners).
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
    showIconContainer: Boolean = true,
    isFirst: Boolean = true,
    isLast: Boolean = true,
) {
    val shape = groupedRowShape(isFirst, isLast)
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(16.dp))
            LeadingIcon(
                icon = icon,
                inContainer = showIconContainer,
            )
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
            Spacer(Modifier.width(16.dp))
        }
    }

    // A row with an action is a real M3 clickable Surface; a row without one is a plain
    // container, so it neither ripples nor reports itself as a button to accessibility.
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = shape,
            color = containerColor,
        ) { content() }
    } else {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = shape,
            color = containerColor,
        ) { content() }
    }
}

/**
 * The 40dp circular `primaryContainer` badge the design puts behind a row's leading glyph, or
 * the bare 24dp glyph when a row has no badge.
 *
 * The inner box fills the surface exactly and centres the glyph, so the icon sits on the
 * circle's centre rather than on its top-left.
 */
@Composable
internal fun LeadingIcon(
    icon: Int,
    inContainer: Boolean,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    glyphSize: androidx.compose.ui.unit.Dp = 24.dp,
) {
    if (!inContainer) {
        SymbolIcon(
            codepoint = icon,
            contentDescription = null,
            size = glyphSize,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            SymbolIcon(codepoint = icon, contentDescription = null, size = glyphSize)
        }
    }
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp),
        shape = groupedRowShape(isFirst, isLast),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = { onCheckedChange(!checked) },
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(16.dp))
            LeadingIcon(icon = icon, inContainer = true)
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
            Spacer(Modifier.width(16.dp))
        }
    }
}

/**
 * M3 Expressive button sizes.
 *
 * The Compose Material 3 artifact on this build line does not yet ship the `SplitButton` and
 * `ButtonGroup` composables (only their design tokens), so they are assembled here from
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
    Medium(56.dp, 24.dp, 8.dp),
    Large(96.dp, 32.dp, 12.dp),
    XLarge(136.dp, 40.dp, 16.dp),
}

/** One entry in a [SplitButton]'s dropdown. */
data class SplitButtonMenuItem(
    val icon: Int,
    val label: String,
    val onClick: () -> Unit,
)

/**
 * One button inside a [ConnectedIconButtonGroup].
 *
 * @param groupPosition where the button sits in its group, which decides its corner treatment.
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
fun SettingsSectionGap(height: androidx.compose.ui.unit.Dp = 16.dp) {
    Spacer(Modifier.height(height))
}
