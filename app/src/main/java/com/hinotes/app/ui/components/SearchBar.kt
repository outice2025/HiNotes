package com.hinotes.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hinotes.app.ui.icons.SymbolIcon
import com.hinotes.app.ui.icons.Symbols

/**
 * The home screen's search field, with the settings action beside it.
 *
 * The field is a 56dp [BasicTextField] on a `surfaceContainerHigh` pill: no border and no
 * focus ring, because Material 3's outlined field reserves a stroke that reads as a permanent
 * highlight ring on a filled container. Content is centred with explicit padding rather than a
 * text field's internal insets, which keeps it on exactly the same axis as the settings button
 * (also 56dp) - Material 3's `SearchBar` reserves extra room for its expanded results and so
 * sits off that axis.
 */
@Composable
fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    settingsContentDescription: String,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
    clearContentDescription: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(SearchFieldHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(SearchFieldHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            onClick = onSearch,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymbolIcon(
                    codepoint = Symbols.Search,
                    contentDescription = null,
                    size = 24.dp,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = textStyle,
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        interactionSource = interactionSource,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    )
                }

                if (query.isNotEmpty() && onClear != null) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(40.dp),
                    ) {
                        SymbolIcon(
                            codepoint = Symbols.Close,
                            contentDescription = clearContentDescription,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        // Same height and container colour as the field, so the two read as siblings on one line.
        Surface(
            onClick = onSettingsClick,
            modifier = Modifier.size(SearchFieldHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SymbolIcon(
                    codepoint = Symbols.Settings,
                    contentDescription = settingsContentDescription,
                    size = 24.dp,
                )
            }
        }
    }
}

/** Height shared by the search field and the settings button, per the design. */
private val SearchFieldHeight = 56.dp

/**
 * A full-width search overlay shown above the note list while the user is typing.
 *
 * Kept separate from the field so the resting layout stays a clean 56dp row.
 */
@Composable
fun SearchResultsOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        content = { androidx.compose.foundation.layout.Column(content = content) },
    )
}
