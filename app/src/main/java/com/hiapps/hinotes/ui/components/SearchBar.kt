package com.hiapps.hinotes.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
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
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols

/**
 * The home screen's search field, with the screen's own action beside it.
 *
 * The field is a 56dp [BasicTextField] on a `surfaceContainerHigh` pill: no border and no
 * focus ring, because Material 3's outlined field reserves a stroke that reads as a permanent
 * highlight ring on a filled container. Content is centred with explicit padding rather than a
 * text field's internal insets, which keeps it on exactly the same axis as the button beside it
 * (also 56dp) - Material 3's `SearchBar` reserves extra room for its expanded results and so
 * sits off that axis.
 *
 * The button is described by its caller rather than named here, because the one place beside the
 * field holds whatever the screen is doing at the moment: settings while the list is being browsed,
 * and the pin of the selected notes once a long press has turned the page into a selection.
 *
 * @param actionIcon the glyph the button draws.
 * @param actionContentDescription what the button says it does, for a reader that cannot see it.
 * @param onActionClick what the button does.
 */
@Composable
fun HomeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    placeholder: String,
    actionIcon: Int,
    actionContentDescription: String,
    onActionClick: () -> Unit,
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
            onClick = onActionClick,
            modifier = Modifier.size(SearchFieldHeight),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SymbolIcon(
                    codepoint = actionIcon,
                    contentDescription = actionContentDescription,
                    size = 24.dp,
                )
            }
        }
    }
}

/** Height shared by the search field and the button beside it, per the design. */
private val SearchFieldHeight = 56.dp
