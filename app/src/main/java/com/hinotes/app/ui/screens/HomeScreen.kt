package com.hinotes.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hinotes.app.R
import com.hinotes.app.data.Note
import com.hinotes.app.data.NoteSort
import com.hinotes.app.ui.AppViewModel
import com.hinotes.app.ui.components.ConfirmDialog
import com.hinotes.app.ui.components.HiNotesExtendedFab
import com.hinotes.app.ui.components.HomeSearchBar
import com.hinotes.app.ui.components.NoteCard
import com.hinotes.app.ui.icons.SymbolIcon
import com.hinotes.app.ui.icons.Symbols
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * The home screen: search, the note list (or the centred empty state), and the create-note
 * extended FAB, in the order the design specifies.
 *
 * @param onCreateNote opens the editor for a brand-new note.
 * @param onOpenNote opens the editor for an existing note.
 * @param onOpenSettings opens the settings screen.
 */
@Composable
fun HomeScreen(
    viewModel: AppViewModel,
    onCreateNote: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Note?>(null) }

    val untitled = stringResource(R.string.home_untitled)
    val lockedLabel = stringResource(R.string.home_locked_badge)
    val hasQuery = query.isNotBlank()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        floatingActionButton = {
            // Bottom-right: the "Create note" extended FAB (56dp tall, 16dp radius, icon
            // leading and label trailing, both centred on one axis).
            HiNotesExtendedFab(
                icon = Symbols.Edit,
                label = stringResource(R.string.home_create_note),
                onClick = onCreateNote,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Top: the search field with the settings action on its right, on one axis.
            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                HomeSearchBar(
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    onSearch = {},
                    placeholder = stringResource(R.string.home_search_placeholder),
                    settingsContentDescription = stringResource(R.string.home_open_settings),
                    onSettingsClick = onOpenSettings,
                    onClear = { viewModel.setQuery("") },
                    clearContentDescription = stringResource(R.string.home_clear_search),
                )
            }

            if (notes.isEmpty()) {
                // Middle: the centred empty state.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 96.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(
                                if (hasQuery) R.string.home_empty_search else R.string.home_empty,
                            ),
                            fontSize = 23.sp,
                            lineHeight = 32.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (!hasQuery) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.home_empty_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.home_note_count, notes.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Box {
                                IconButton(onClick = { sortMenuOpen = true }) {
                                    SymbolIcon(
                                        codepoint = Symbols.Sort,
                                        contentDescription = stringResource(R.string.home_sort),
                                        size = 24.dp,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                DropdownMenu(
                                    expanded = sortMenuOpen,
                                    onDismissRequest = { sortMenuOpen = false },
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    SortMenuItem(
                                        R.string.home_sort_updated,
                                        sort == NoteSort.UpdatedDesc,
                                        onClick = { viewModel.setSort(NoteSort.UpdatedDesc) },
                                        onDismiss = { sortMenuOpen = false },
                                    )
                                    SortMenuItem(
                                        R.string.home_sort_created,
                                        sort == NoteSort.CreatedDesc,
                                        onClick = { viewModel.setSort(NoteSort.CreatedDesc) },
                                        onDismiss = { sortMenuOpen = false },
                                    )
                                    SortMenuItem(
                                        R.string.home_sort_title,
                                        sort == NoteSort.TitleAsc,
                                        onClick = { viewModel.setSort(NoteSort.TitleAsc) },
                                        onDismiss = { sortMenuOpen = false },
                                    )
                                }
                            }
                        }
                    }

                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            title = note.displayTitle(untitled),
                            snippet = note.snippet(),
                            meta = DateFormat
                                .getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(note.updatedAt)),
                            locked = note.isLocked,
                            lockedLabel = lockedLabel,
                            onClick = { onOpenNote(note.id) },
                            onLongClick = { pendingDelete = note },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        ConfirmDialog(
            title = stringResource(R.string.home_delete_title),
            message = stringResource(
                R.string.home_delete_message,
                deleting.displayTitle(untitled),
            ),
            confirmLabel = stringResource(R.string.home_delete_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                scope.launch { viewModel.deleteNote(deleting.id) }
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun SortMenuItem(
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        },
        onClick = {
            onClick()
            onDismiss()
        },
    )
}
