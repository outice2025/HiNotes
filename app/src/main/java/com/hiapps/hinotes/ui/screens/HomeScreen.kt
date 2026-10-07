package com.hiapps.hinotes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.HomeLayout
import com.hiapps.hinotes.data.NoteSort
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.ConfirmDialog
import com.hiapps.hinotes.ui.components.ExtendedFab
import com.hiapps.hinotes.ui.components.HomeSearchBar
import com.hiapps.hinotes.ui.components.NoteCard
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

/** The screen's outer gutter, shared by the search field and the note list. */
private val HomeGutter = 16.dp

/**
 * Left inset of the note-count line.
 *
 * The count sits on the same row as the two icon buttons, so its left margin is set to match the
 * *visual* right margin of the rightmost button - the one the eye actually measures. That margin
 * is the gutter, plus the 12dp an `IconButton` keeps between its 48dp touch target and the 24dp
 * glyph, plus the 3.818dp the `sort` glyph's ink leaves inside its own 24dp box (its path runs to
 * x = 20.182). Both sides therefore come to 31.8dp and the row reads as balanced.
 *
 * Only this inset moves; the buttons and their margins are untouched.
 */
private val HeaderInset = HomeGutter + 12.dp + 3.82.dp

/**
 * The home screen: search, the note list or grid, and the create-note action.
 *
 * Long-pressing a note enters multi-select. While a selection is active the create button is
 * joined by a delete action on its left, and the count line reports the selection rather than
 * the total.
 *
 * The grid is a staggered one: each card is exactly as tall as its own content and the columns
 * pack tight underneath, so a one-line note no longer leaves a hole beside a long one. The count
 * line is pinned above the list rather than scrolling with it, which keeps the layout toggle and
 * the sort menu reachable however far down the list the user is.
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
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedNoteIds.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    var sortMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val untitled = stringResource(R.string.home_untitled)
    val lockedLabel = stringResource(R.string.home_locked_badge)
    val hasQuery = query.isNotBlank()
    val selecting = selectedIds.isNotEmpty()
    val gridLayout = settings.homeLayout == HomeLayout.Grid

    // A selection must not outlive the notes it refers to.
    val visibleIds = remember(notes) { notes.map { it.id }.toSet() }
    LaunchedEffect(visibleIds) { viewModel.retainSelection(visibleIds) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // Only the top and the sides keep a system inset. The page's own background is painted to
        // the bottom of the window and the list scrolls all the way down behind the navigation
        // bar, so nothing is reserved there for a bar the user cannot see; the list's own bottom
        // padding is what keeps its last note and the create button clear of the gesture handle.
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        floatingActionButton = {
            // The IME is an overlay in an edge-to-edge window, so the action buttons have to be
            // moved above it themselves - otherwise tapping the search field buries the one
            // control the empty screen is telling the user to press. The same inset keeps them
            // clear of the gesture handle when no keyboard is up.
            Row(
                modifier = Modifier.windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selecting) {
                    // Delete sits to the LEFT of "Create note". The Scaffold's FAB slot is at
                    // the end of the screen, so this order places delete first.
                    //
                    // A bare trash icon rather than a labelled button: the selected count is on
                    // the header line directly above, so the button only has to say which action
                    // it performs.
                    //
                    // The error container and its content swap places with the theme - a pale red
                    // button carrying a dark red glyph in light mode, the reverse in dark mode -
                    // which lands on the same visual weight either way without losing contrast.
                    val dark = LocalDarkTheme.current
                    ExtendedFab(
                        icon = Symbols.Delete,
                        label = null,
                        contentDescription = stringResource(
                            R.string.home_delete_selected,
                            selectedIds.size,
                        ),
                        onClick = { confirmDelete = true },
                        containerColor = if (dark) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        contentColor = if (dark) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                    Spacer(Modifier.width(12.dp))
                }
                ExtendedFab(
                    // The standard outlined pencil.
                    icon = Symbols.Edit,
                    label = stringResource(R.string.home_create_note),
                    onClick = onCreateNote,
                    // The emphasis is inverted against the delete button beside it: this one is
                    // the saturated action, carrying the primary colour as its background with
                    // onPrimary as its content, in both themes.
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // While the keyboard is up it covers the lower part of the window, so the list is
                // given the height that is left rather than scrolling underneath the keys.
                .imePadding(),
        ) {
            Box(Modifier.padding(horizontal = HomeGutter, vertical = 12.dp)) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(start = HeaderInset, end = HomeGutter),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selecting) {
                            stringResource(R.string.home_selected_count, selectedIds.size)
                        } else {
                            stringResource(R.string.home_note_count, notes.size)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selecting) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )

                    if (selecting) {
                        IconButton(
                            onClick = { viewModel.selectAllNotes(notes.map { it.id }) },
                        ) {
                            SymbolIcon(
                                codepoint = Symbols.SelectAll,
                                contentDescription = stringResource(R.string.home_select_all),
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            SymbolIcon(
                                codepoint = Symbols.Close,
                                contentDescription = stringResource(
                                    R.string.home_exit_selection,
                                ),
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        // Layout toggle sits immediately LEFT of the sort button.
                        IconButton(
                            onClick = {
                                viewModel.updateSettings {
                                    setHomeLayout(
                                        if (gridLayout) HomeLayout.List else HomeLayout.Grid,
                                    )
                                }
                            },
                        ) {
                            SymbolIcon(
                                codepoint = if (gridLayout) {
                                    Symbols.ViewList
                                } else {
                                    Symbols.GridView
                                },
                                contentDescription = stringResource(
                                    if (gridLayout) {
                                        R.string.home_layout_list
                                    } else {
                                        R.string.home_layout_grid
                                    },
                                ),
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

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

                Spacer(Modifier.height(8.dp))

                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(if (gridLayout) 2 else 1),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HomeGutter,
                        end = HomeGutter,
                        bottom = 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalItemSpacing = 8.dp,
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            title = note.displayTitle(untitled),
                            content = note.content,
                            markdownEnabled = settings.markdownEnabled,
                            locked = note.isLocked,
                            lockedLabel = lockedLabel,
                            selecting = selecting,
                            selected = note.id in selectedIds,
                            compact = gridLayout,
                            onClick = {
                                if (selecting) {
                                    viewModel.toggleSelection(note.id)
                                } else {
                                    onOpenNote(note.id)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(note.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.home_delete_title),
            message = stringResource(R.string.home_delete_selected_message, selectedIds.size),
            confirmLabel = stringResource(R.string.home_delete_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                confirmDelete = false
                scope.launch { viewModel.deleteSelectedNotes() }
            },
            onDismiss = { confirmDelete = false },
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
