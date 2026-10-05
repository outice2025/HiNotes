package com.hiapps.hinotes.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.countNoteCharacters
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.NEW_NOTE_ID
import com.hiapps.hinotes.ui.components.ConfirmDialog
import com.hiapps.hinotes.ui.components.ConnectedIconButton
import com.hiapps.hinotes.ui.components.ConnectedIconButtonGroup
import com.hiapps.hinotes.ui.components.ExpressiveButtonSize
import com.hiapps.hinotes.ui.components.PropertiesSheet
import com.hiapps.hinotes.ui.editor.EditorState
import com.hiapps.hinotes.ui.editor.Markdown
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long typing must pause before an edit becomes its own undo step. */
private const val UNDO_COALESCE_MS = 400L

/** Autosave cadence, matching the interval quoted in the editor settings screen. */
private const val AUTOSAVE_INTERVAL_MS = 20_000L

/** Editor canvas height from the design; taller than most phones, so the screen scrolls. */
private val CANVAS_HEIGHT = 740.dp

/**
 * The note editor.
 *
 * Layout follows the design: a top row with the back button and the elevated split button, the
 * outlined "Title" field, and the 380x740dp canvas (`surfaceContainerHigh`, 28dp radius) with
 * the connected formatting toolbar drawn over its lower edge.
 *
 * The canvas is taller than a phone screen, so its content scrolls inside a fixed-height frame
 * rather than being clipped - the geometry the design asks for is kept, and the note stays
 * fully reachable.
 */
@Composable
fun EditorScreen(
    viewModel: AppViewModel,
    noteId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val editorState = remember { EditorState() }
    var title by remember { mutableStateOf("") }
    var loadedNote by remember { mutableStateOf<Note?>(null) }
    var previewMode by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    // Guards the dirty flag against the initial load: the text-change flow emits once as soon
    // as it starts collecting, which would otherwise mark a freshly opened note as modified.
    var loadComplete by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var confirmDeleteNote by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var showProperties by remember { mutableStateOf(false) }

    val untitled = stringResource(R.string.home_untitled)

    // ------------------------------------------------------------ load the note

    LaunchedEffect(noteId) {
        loadComplete = false
        val note = when {
            noteId == NEW_NOTE_ID || noteId.isBlank() -> viewModel.notesRepository.createNote()
            else -> viewModel.notesRepository.note(noteId) ?: viewModel.notesRepository.createNote()
        }
        viewModel.openNote(note.id)
        loadedNote = note
        title = note.title
        editorState.reset(TextFieldValue(note.content))
        previewMode = settings.startInPreview
        dirty = false
        loadComplete = true
    }

    // Keep the bound note in step when the repository changes it (e.g. lock toggled).
    val boundNote by viewModel.editingNote.collectAsStateWithLifecycle()
    LaunchedEffect(boundNote?.isLocked) {
        boundNote?.let { if (it.id == loadedNote?.id) loadedNote = it }
    }

    suspend fun persist(): Boolean {
        val note = loadedNote ?: return false
        val stored = viewModel.saveNote(
            note.copy(title = title, content = editorState.value.text),
        )
        return if (stored == null) {
            // Everything was blank: the note was discarded rather than stored.
            false
        } else {
            loadedNote = stored
            dirty = false
            true
        }
    }

    // ------------------------------------------------------------ undo coalescing

    LaunchedEffect(editorState) {
        snapshotFlow { editorState.value.text }
            .collect {
                if (loadComplete) dirty = true
                delay(UNDO_COALESCE_MS)
                editorState.commitPending()
            }
    }

    // -------------------------------------------------------------------- autosave

    LaunchedEffect(settings.autoSave, dirty) {
        if (!settings.autoSave) return@LaunchedEffect
        while (dirty) {
            delay(AUTOSAVE_INTERVAL_MS)
            if (dirty) {
                if (persist()) {
                    snackbarHostState.showSnackbar(context.getString(R.string.editor_autosaved))
                }
            }
        }
    }

    fun leave() {
        if (dirty) confirmDiscard = true else onBack()
    }

    // Back gesture and system back run the same guard as the toolbar button.
    BackHandler(enabled = dirty || findOpen) {
        when {
            findOpen -> findOpen = false
            dirty -> confirmDiscard = true
            else -> onBack()
        }
    }

    // Entering preview withdraws every editing affordance, so the keyboard goes with them: a
    // text field that is merely read-only would still hold focus and keep the IME up.
    LaunchedEffect(previewMode) {
        if (previewMode) {
            findOpen = false
            focusManager.clearFocus()
        }
    }

    // ------------------------------------------------------------------- rendering

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top row: back button pinned left, the save split button pinned right.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { leave() },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    SymbolIcon(
                        codepoint = Symbols.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        size = 24.dp,
                    )
                }

                // Pushes the actions to the right edge.
                Spacer(Modifier.weight(1f))

                // Two plain icon buttons, matching the back button on the left: the same 44dp
                // circular target and the same icon size, so the row reads as one set of
                // controls rather than a pill glued to a button.
                IconButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    SymbolIcon(
                        codepoint = if (previewMode) Symbols.Visibility else Symbols.Edit,
                        contentDescription = stringResource(
                            if (previewMode) R.string.editor_mode_preview else R.string.editor_mode_edit,
                        ),
                        size = 24.dp,
                    )
                }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = {
                        scope.launch {
                            if (persist()) {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.editor_saved),
                                )
                                onBack()
                            } else {
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    SymbolIcon(
                        codepoint = Symbols.Check,
                        contentDescription = stringResource(R.string.editor_save),
                        size = 24.dp,
                    )
                }

                // Overflow menu for the secondary editor actions. Anchored to this Box so it
                // drops from the button rather than the screen corner.
                Box {
                    Spacer(Modifier.size(1.dp))
                    DropdownMenu(
                        expanded = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false },
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ) {
                        EditorMenuItem(Symbols.Edit, R.string.editor_mode_edit) {
                            previewMode = false
                            sortMenuOpen = false
                        }
                        EditorMenuItem(Symbols.Visibility, R.string.editor_mode_preview) {
                            previewMode = true
                            sortMenuOpen = false
                        }
                        EditorMenuItem(Symbols.Info, R.string.editor_properties) {
                            showProperties = true
                            sortMenuOpen = false
                        }
                        EditorMenuItem(Symbols.Share, R.string.editor_share) {
                            sortMenuOpen = false
                            // Shares what is on screen, saved or not: a note the user is still
                            // writing is exactly the one they may want to send. Nothing is
                            // persisted by this action, and a blank note is still shareable
                            // (it goes out as its title), so the share sheet always appears -
                            // a silent no-op here is what made this button look broken.
                            val body = editorState.value.text
                            val heading = title.ifBlank {
                                body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                            }.ifBlank { untitled }
                            scope.launch {
                                if (!shareNote(context, heading, body)) {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.editor_share_failed),
                                    )
                                }
                            }
                        }
                        // The label follows the note's state, so the row never offers to lock
                        // a note that is already locked.
                        EditorMenuItem(
                            icon = Symbols.Lock,
                            labelRes = if (loadedNote?.isLocked == true) {
                                R.string.unlock_make_public
                            } else {
                                R.string.unlock_make_private
                            },
                        ) {
                            sortMenuOpen = false
                            scope.launch {
                                // Persist first: locking a note must not discard what the user
                                // has typed since the last save.
                                val note = loadedNote
                                if (note != null && persist()) {
                                    viewModel.setNoteLocked(note.id, !note.isLocked)
                                }
                            }
                        }
                        EditorMenuItem(Symbols.Delete, R.string.editor_delete) {
                            sortMenuOpen = false
                            confirmDeleteNote = true
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Title field. Read-only in preview mode: the preview promises that the note cannot
            // be changed, so the title has to be locked down with the body.
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    dirty = true
                },
                label = { Text(stringResource(R.string.editor_title_label)) },
                singleLine = true,
                readOnly = previewMode,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
            )

            Spacer(Modifier.height(12.dp))

            // Canvas: the editor body sits on this container and the toolbar is drawn over it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CANVAS_HEIGHT)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(28.dp),
                    ),
            ) {
                val bodyStyle = LocalTextStyle.current.copy(
                    // The device's own font family, matching the rest of the app. Size and
                    // weight come from the note typography sliders.
                    fontSize = (16f * settings.noteFontScale.multiplier).sp,
                    lineHeight = (24f * settings.noteFontScale.multiplier).sp,
                    fontWeight = settings.noteFontWeight.weight,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                if (previewMode) {
                    val rendered = remember(editorState.value.text, settings.markdownEnabled) {
                        if (settings.markdownEnabled) {
                            Markdown.render(editorState.value.text)
                        } else {
                            AnnotatedString(editorState.value.text)
                        }
                    }
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(text = rendered, style = bodyStyle)
                    }
                } else {
                    BasicTextField(
                        value = editorState.value,
                        onValueChange = editorState::onUserInput,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)
                            .verticalScroll(rememberScrollState()),
                        textStyle = bodyStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            Box {
                                if (editorState.value.text.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.editor_body_placeholder),
                                        style = bodyStyle.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        ),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                }

                // Find-in-note bar, shown above the toolbar while searching.
                if (findOpen) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            OutlinedTextField(
                                value = findQuery,
                                onValueChange = { findQuery = it },
                                placeholder = {
                                    Text(stringResource(R.string.editor_find_placeholder))
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val matchCount = remember(findQuery, editorState.value.text) {
                                if (findQuery.isBlank()) {
                                    0
                                } else {
                                    Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE)
                                        .findAll(editorState.value.text)
                                        .count()
                                }
                            }
                            Text(
                                text = if (findQuery.isBlank()) {
                                    stringResource(R.string.editor_find)
                                } else if (matchCount == 0) {
                                    stringResource(R.string.editor_find_no_match)
                                } else {
                                    stringResource(R.string.editor_find_matches, 1, matchCount)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            )
                        }
                    }
                }

                // Connected formatting toolbar, drawn over the lower edge of the canvas.
                // It is an editing control, so preview mode takes it away entirely: there is
                // nothing in a preview for it to act on, and leaving it up would suggest the
                // note can still be changed.
                if (!previewMode) {
                    ConnectedIconButtonGroup(
                        buttons = listOf(
                            ConnectedIconButton(
                                icon = Symbols.Search,
                                contentDescription = stringResource(R.string.editor_find),
                            ) { findOpen = !findOpen },
                            ConnectedIconButton(
                                icon = Symbols.Undo,
                                contentDescription = stringResource(R.string.editor_undo),
                                enabled = editorState.canUndo,
                            ) { editorState.undo() },
                            ConnectedIconButton(
                                icon = Symbols.Redo,
                                contentDescription = stringResource(R.string.editor_redo),
                                enabled = editorState.canRedo,
                            ) { editorState.redo() },
                            ConnectedIconButton(
                                icon = Symbols.FormatBold,
                                contentDescription = stringResource(R.string.editor_bold),
                            ) {
                                editorState.applyEdit(
                                    Markdown.toggleInline(editorState.value, "**"),
                                )
                            },
                            ConnectedIconButton(
                                icon = Symbols.FormatItalic,
                                contentDescription = stringResource(R.string.editor_italic),
                            ) {
                                editorState.applyEdit(
                                    Markdown.toggleInline(editorState.value, "*"),
                                )
                            },
                            ConnectedIconButton(
                                icon = Symbols.CheckBoxOutlineBlank,
                                contentDescription = stringResource(R.string.editor_checkbox),
                            ) {
                                editorState.applyEdit(
                                    Markdown.toggleLinePrefix(editorState.value, "- [ ] "),
                                )
                            },
                            ConnectedIconButton(
                                icon = Symbols.List,
                                contentDescription = stringResource(R.string.editor_bullet_list),
                            ) {
                                editorState.applyEdit(
                                    Markdown.toggleLinePrefix(editorState.value, "- "),
                                )
                            },
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        size = ExpressiveButtonSize.Medium,
                    )
                }
            }

            // The mode is stated under the canvas as well as by the toolbar's absence, so
            // "preview" can never be mistaken for "the editor stopped responding".
            if (previewMode) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.editor_preview_badge),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (settings.markdownEnabled && !previewMode) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.editor_markdown_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            message = stringResource(R.string.editor_discard_message),
            confirmLabel = stringResource(R.string.editor_discard_confirm),
            dismissLabel = stringResource(R.string.editor_keep_editing),
            onConfirm = {
                confirmDiscard = false
                // Keep whatever the user typed rather than losing it silently.
                scope.launch {
                    persist()
                    onBack()
                }
            },
            onDismiss = { confirmDiscard = false },
        )
    }

    if (showProperties) {
        PropertiesSheet(
            wordCount = countNoteCharacters(editorState.value.text),
            createdAt = loadedNote?.createdAt,
            updatedAt = loadedNote?.updatedAt,
            onDismiss = { showProperties = false },
        )
    }

    if (confirmDeleteNote) {
        ConfirmDialog(
            title = stringResource(R.string.home_delete_title),
            message = stringResource(
                R.string.home_delete_message,
                title.ifBlank { stringResource(R.string.home_untitled) },
            ),
            confirmLabel = stringResource(R.string.home_delete_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                confirmDeleteNote = false
                val id = loadedNote?.id
                scope.launch {
                    if (id != null) viewModel.deleteNote(id)
                    onBack()
                }
            },
            onDismiss = { confirmDeleteNote = false },
        )
    }

    // Persist when the editor leaves the composition, so nothing typed is lost.
    DisposableEffect(Unit) {
        onDispose {
            // Deliberately not saving here: `onBack` paths already persist, and saving from a
            // dispose scope could race with the note being deleted.
        }
    }
}

/** One row of the editor's overflow menu. */
@Composable
private fun EditorMenuItem(icon: Int, labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        onClick = onClick,
        leadingIcon = {
            SymbolIcon(codepoint = icon, contentDescription = null, size = 24.dp)
        },
    )
}
