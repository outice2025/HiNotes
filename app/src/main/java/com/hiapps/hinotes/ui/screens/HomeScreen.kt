package com.hiapps.hinotes.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.hiapps.hinotes.ui.components.ExtendedFab
import com.hiapps.hinotes.ui.components.HomeSearchBar
import com.hiapps.hinotes.ui.components.NoteCard
import com.hiapps.hinotes.ui.components.cardSideInset
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
 * The keys of the two headings over the pinned run and the rest.
 *
 * The grid's other keys are note ids, so each heading needs one of its own: keyed by its position it
 * would be the slot a newly pinned note animates into, and the note and the heading would swap
 * places on the way past.
 */
private const val PinnedLabelKey = "home-pinned-label"
private const val OtherLabelKey = "home-other-label"

/** What one item of the note grid is spaced from the next by, in either direction. */
private val HomeItemGap = 8.dp

/**
 * What a run's heading adds to [HomeItemGap].
 *
 * The grid spaces every pair of items the same way, so a heading that pads itself by the same
 * amount above and below ends up with equal gaps on both sides - the gap above it being the grid's
 * top padding, which is set from this same spacing while the headings are shown.
 */
private val RunLabelGap = 4.dp

/**
 * The home screen: search, the note list or grid, and the create-note action.
 *
 * Long-pressing a note enters multi-select. While a selection is active the create button gives way
 * to the delete action - the same button in the same corner, saying what it does - the count line
 * reports the selection rather than the total, and the row of controls above the list offers
 * select-all, leaving the selection, and exporting exactly the notes that are selected.
 *
 * Pinned notes are shown as their own run above the rest, each run under a heading of its own. A
 * heading is drawn only when there are two runs to tell apart, carries the note count's own type and
 * colour, and is spaced the same above and below: the two runs are the list's own division, so they
 * read as part of it rather than as a new section of the screen.
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

    val untitled = stringResource(R.string.home_untitled)
    val lockedLabel = stringResource(R.string.home_locked_badge)
    val hasQuery = query.isNotBlank()
    val selecting = selectedIds.isNotEmpty()
    val gridLayout = settings.homeLayout == HomeLayout.Grid

    // The two runs the list is divided into: the repository already orders pinned notes first, so
    // these are that same order cut in half rather than a second sort. They are named only when
    // both are there to name - one run on its own needs no heading to say what it is.
    val pinnedNotes = remember(notes) { notes.filter { it.pinned } }
    val otherNotes = remember(notes) { notes.filterNot { it.pinned } }
    val runsLabelled = pinnedNotes.isNotEmpty() && otherNotes.isNotEmpty()

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

    /**
     * Whether every note of the selection is already pinned.
     *
     * The button beside the field says what it will do, not what the notes are: a selection that is
     * pinned already is offered as "unpin", and one that is not as "pin". A mixed selection counts
     * as not pinned, because pinning it is the action that leaves every note of it pinned.
     */
    val selectionPinned = remember(notes, selectedIds) {
        val chosen = notes.filter { it.id in selectedIds }
        chosen.isNotEmpty() && chosen.all { it.pinned }
    }

    /** Pins the selected notes, or unpins them when they are all pinned already. */
    fun pinSelection() {
        val pinned = !selectionPinned
        val ids = notes.filter { it.id in selectedIds }.map { it.id }
        // The selection is left as it is: pinning is one of the things a selection is for, and the
        // button beside the field now says the opposite, which is how the change is read back.
        // The whole selection goes in one write: pinning a note at a time re-flowed the page once
        // per note, which is the flash the user saw on a long selection.
        scope.launch {
            viewModel.pinNotes(ids, pinned)
        }
    }

    /**
     * Deletes the notes the selection holds, with the undo offer standing in for a confirmation.
     *
     * The question a dialog used to ask had been answered twice already - by the long press that
     * started the selection and by the delete button itself - and the window that follows the
     * deletion is a better guard than a question, because a note that goes by mistake can be
     * brought back inside it.
     */
    fun deleteSelection() {
        val removed = notes.filter { it.id in selectedIds }
        if (removed.isEmpty()) return
        // The rows go at once and the notes are held, deleted but still stored, until the window
        // closes. The offer to bring them back is made by the list itself - see the effect below -
        // because the editor deletes notes too, and both deletions have to be answerable here.
        scope.launch {
            viewModel.stageDelete(removed)
        }
    }

    // The offer to bring a deleted note back, made wherever the deletion came from. The window is
    // the view model's, so it closes whether or not the user is looking at this screen; the bar is
    // shown while something is held and taken away the moment nothing is.
    //
    // The effect is keyed on there being anything held rather than on the notes themselves: deleting
    // the same note twice is two equal values, and a key that compared equal would leave the offer
    // off the screen the second time.
    val stagedDelete by viewModel.stagedDelete.collectAsStateWithLifecycle()
    LaunchedEffect(stagedDelete.isNotEmpty()) {
        if (stagedDelete.isEmpty()) return@LaunchedEffect
        val answer = snackbarHostState.showSnackbar(
            message = context.getString(R.string.home_deleted_message, stagedDelete.size),
            actionLabel = context.getString(R.string.editor_undo),
            duration = SnackbarDuration.Indefinite,
        )
        if (answer == SnackbarResult.ActionPerformed) viewModel.undoStagedDelete()
    }
    LaunchedEffect(stagedDelete.isEmpty()) {
        // The window closed on its own: an offer that outlived it could be tapped for nothing.
        if (stagedDelete.isEmpty()) snackbarHostState.currentSnackbarData?.dismiss()
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
        // Above the navigation bar rather than in it: the list reaches past that bar, but a message
        // is read and tapped, and one drawn inside the gesture area has the bar drawn over it.
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
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
                    onClick = { deleteSelection() },
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
                    // Beside the field sits whatever the page is doing: the way into settings while
                    // the list is being browsed, and the pin of the selection once a long press has
                    // turned the page into one. It is the same button in the same place either way,
                    // which is why it is the component's own action rather than two buttons.
                    actionIcon = when {
                        !selecting -> Symbols.Settings
                        selectionPinned -> Symbols.KeepOff
                        else -> Symbols.Keep
                    },
                    actionContentDescription = stringResource(
                        when {
                            !selecting -> R.string.home_open_settings
                            selectionPinned -> R.string.editor_unpin
                            else -> R.string.editor_pin
                        },
                    ),
                    onActionClick = {
                        if (selecting) pinSelection() else onOpenSettings()
                    },
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

                    // The controls of the mode being entered fade in over the ones of the mode
                    // being left, in the same place: laid out one group after the other, the new
                    // buttons were pushed in from the left while the old ones were still fading,
                    // and the row read as a small slide rather than as one state becoming
                    // another. Both groups sit at the row's end inside one box, so each fades
                    // where the other was - and the selection's controls are drawn last, on top.
                    //
                    // The calls are qualified because this column is inside a row, whose own
                    // `AnimatedVisibility` is a scoped one: it can only say where a fading button
                    // sits in a row, and these two are placed by the box over each other instead.
                    Box(contentAlignment = Alignment.CenterEnd) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !selecting,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Row {
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

                        androidx.compose.animation.AnimatedVisibility(
                            visible = selecting,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            Row {
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
                        // A label's gaps are measured from the grid's own spacing, so the first one
                        // needs the same gap above it that every other item already has. Without a
                        // label there is nothing to measure and the padding is as it was.
                        top = if (runsLabelled) HomeItemGap else 0.dp,
                        bottom = 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(HomeItemGap),
                    verticalItemSpacing = HomeItemGap,
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
                            // A locked note is one the app will not write to, so its boxes report
                            // what the note says without offering to change it. While a selection
                            // is being made the whole card is a selection target: a box that ticked
                            // the note there would be the opposite of the tap the user meant.
                            onToggleTask = if (note.isLocked || selecting) {
                                null
                            } else {
                                { line, checked ->
                                    scope.launch {
                                        viewModel.setTaskChecked(note.id, line, checked)
                                    }
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    // Each run is named rather than separated by a line: "pinned" and "not pinned"
                    // are what the order means, and a rule said nothing about which run was which.
                    // The labels carry the note count's own type and colour, so the list reads as
                    // one page, and each takes the full width of a row so the two-column layout
                    // puts no card beside it.
                    if (runsLabelled) {
                        item(key = PinnedLabelKey, span = StaggeredGridItemSpan.FullLine) {
                            RunLabel(
                                text = stringResource(R.string.editor_pin),
                                compact = gridLayout,
                            )
                        }
                    }

                    items(pinnedNotes, key = { it.id }) { note -> noteCard(note) }

                    if (runsLabelled) {
                        item(key = OtherLabelKey, span = StaggeredGridItemSpan.FullLine) {
                            RunLabel(
                                text = stringResource(R.string.home_unpinned),
                                compact = gridLayout,
                            )
                        }
                    }

                    items(otherNotes, key = { it.id }) { note -> noteCard(note) }
                }
            }
        }
    }
}

/**
 * The heading over one run of notes.
 *
 * It is the note count's own type and colour - the count says how many notes there are, these say
 * how the list is divided - and it begins where the titles of the notes underneath it begin, so a
 * heading reads as the first line of its run. It pads itself the same above and below, which with
 * the grid's own spacing is what leaves it equal gaps on both sides.
 *
 * @param compact the two-column grid, whose cards inset their titles by less than the list does.
 */
@Composable
private fun RunLabel(
    text: String,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            // A heading is drawn in the same grid slot as the cards of its run, so it carries the
            // inset a card gives its own title: without it the two left edges do not meet.
            .padding(start = cardSideInset(compact))
            .padding(vertical = RunLabelGap),
    )
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
