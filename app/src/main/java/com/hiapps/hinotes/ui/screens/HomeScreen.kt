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
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridItemScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.HomeLayout
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.NoteSort
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.ConfirmDialog
import com.hiapps.hinotes.ui.components.ExtendedFab
import com.hiapps.hinotes.ui.components.HomeSearchBar
import com.hiapps.hinotes.ui.components.NoteCard
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.ColorTokens
import com.hiapps.hinotes.ui.theme.LocalDarkTheme
import com.hiapps.hinotes.ui.theme.nearestPairTo
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
 * The key of the rule drawn between the pinned notes and the rest.
 *
 * The grid's other keys are note ids, so the line needs one of its own: keyed by its position it
 * would be the slot a newly pinned note animates into, and the note and the line would swap places
 * on the way past.
 */
private const val PinnedRuleKey = "home-pinned-rule"

/**
 * The home screen: search, the note list or grid, and the create-note action.
 *
 * Long-pressing a note enters multi-select. While a selection is active the create button gives way
 * to the delete action - the same button in the same corner, saying what it does - the count line
 * reports the selection rather than the total, and the row of controls above the list offers
 * select-all, leaving the selection, and exporting exactly the notes that are selected.
 *
 * Pinned notes are shown as their own run above the rest, separated by a rule rather than by a
 * heading: the pin is the user's own ordering, and a note that is simply first in the list says so
 * without being labelled.
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val untitled = stringResource(R.string.home_untitled)
    val lockedLabel = stringResource(R.string.home_locked_badge)
    val hasQuery = query.isNotBlank()
    val selecting = selectedIds.isNotEmpty()
    val gridLayout = settings.homeLayout == HomeLayout.Grid

    // The list split where the rule goes: the repository already orders pinned notes first, so
    // these two runs are that same order cut in half rather than a second sort.
    val pinnedNotes = remember(notes) { notes.filter { it.pinned } }
    val otherNotes = remember(notes) { notes.filterNot { it.pinned } }

    // A selection must not outlive the notes it refers to.
    val visibleIds = remember(notes) { notes.map { it.id }.toSet() }
    LaunchedEffect(visibleIds) { viewModel.retainSelection(visibleIds) }

    /**
     * Shares the notes the selection holds.
     *
     * The same export the backup screen performs on the whole list - one zip of documents through
     * the share sheet - with the selection standing in for "all". The selection is left as it is:
     * exporting is not destructive, so there is nothing to clear up.
     */
    fun exportSelection() {
        val chosen = notes.filter { it.id in selectedIds }
        scope.launch {
            snackbarHostState.showSnackbar(shareNotes(context, chosen, settings.markdownEnabled))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        // Only the top and the sides keep a system inset. The page's own background is painted to
        // the bottom of the window and the list scrolls all the way down behind the navigation
        // bar, so nothing is reserved there for a bar the user cannot see; the list's own bottom
        // padding is what keeps its last note and the create button clear of the gesture handle.
        contentWindowInsets = WindowInsets.systemBars.only(
            WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // The IME is an overlay in an edge-to-edge window, so the action button has to be
            // moved above it itself - otherwise tapping the search field buries the one control
            // the empty screen is telling the user to press. The same inset keeps it clear of the
            // gesture handle when no keyboard is up.
            val bottomInset = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
            )
            if (selecting) {
                // Delete takes the create button's place rather than sitting beside it: one button
                // in that corner, at the same size, in the same spot, and labelled - so nothing
                // moves under the thumb when a long-press turns the page into a selection.
                //
                // The light scheme paints it in the pair of roles nearest the destructive red the
                // button was specified with - see [nearestPairTo], which reads the scheme rather
                // than naming a role, so a generated accent and a wallpaper-derived palette bring
                // their own red. The dark scheme keeps the pair it has always had: a pale button
                // carrying a dark red glyph, the same weight without the saturated fill.
                val scheme = MaterialTheme.colorScheme
                val destructive = remember(scheme) {
                    scheme.nearestPairTo(ColorTokens.DestructiveReference)
                }
                val dark = LocalDarkTheme.current
                ExtendedFab(
                    icon = Symbols.Delete,
                    label = stringResource(R.string.home_delete_notes),
                    onClick = { confirmDelete = true },
                    modifier = bottomInset,
                    containerColor = if (dark) scheme.onErrorContainer else destructive.container,
                    contentColor = if (dark) scheme.errorContainer else destructive.content,
                )
            } else {
                ExtendedFab(
                    // The standard outlined pencil.
                    icon = Symbols.Edit,
                    label = stringResource(R.string.home_create_note),
                    onClick = onCreateNote,
                    modifier = bottomInset,
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
                        // Export sits immediately LEFT of select-all, and carries the same glyph
                        // and wording as the backup screen's "Export notes" row: it is that same
                        // export, applied to the notes the user picked rather than to all of them.
                        IconButton(onClick = { exportSelection() }) {
                            SymbolIcon(
                                codepoint = Symbols.Download,
                                contentDescription = stringResource(R.string.backup_export_notes),
                                size = 24.dp,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                    // One note's card, described once and used by both runs below: the pinned notes
                    // and the rest differ in where they sit, not in how they are drawn.
                    val noteCard: @Composable LazyStaggeredGridItemScope.(Note) -> Unit = { note ->
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

                    items(pinnedNotes, key = { it.id }) { note -> noteCard(note) }

                    // The rule between the two runs, and only when there are two runs to separate:
                    // it takes the full width of a row, so it is drawn across both columns of the
                    // grid layout, and the grid's own side padding is what keeps it clear of the
                    // screen edges - the line stops where the cards do.
                    if (pinnedNotes.isNotEmpty() && otherNotes.isNotEmpty()) {
                        item(key = PinnedRuleKey, span = StaggeredGridItemSpan.FullLine) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                    }

                    items(otherNotes, key = { it.id }) { note -> noteCard(note) }
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
