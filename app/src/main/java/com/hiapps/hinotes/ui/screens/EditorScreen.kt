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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
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
import com.hiapps.hinotes.ui.components.SplitButton
import com.hiapps.hinotes.ui.components.SplitButtonMenuItem
import com.hiapps.hinotes.ui.editor.EditorState
import com.hiapps.hinotes.ui.editor.Markdown
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.HiNotesCorners
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long typing must pause before an edit becomes its own undo step. */
private const val UNDO_COALESCE_MS = 400L

/** Autosave cadence, matching the interval quoted in the editor settings screen. */
private const val AUTOSAVE_INTERVAL_MS = 20_000L

/** Height of the filled title box, from the design. */
private val TITLE_HEIGHT = 68.dp

/** Gap between the header row and the title box. */
private val HEADER_GAP = 24.dp

/**
 * The two shapes the title box and the canvas are drawn with.
 *
 * They are the grouped-list treatment the settings screens use: the two containers are separated
 * by the same 3dp seam as two neighbouring settings rows, and the corners that meet across that
 * seam are drawn at the small inner radius while the corners facing outwards keep the large one.
 * That is what makes the title and the body read as one connected surface rather than as two
 * separate boxes.
 */
private val TitleShape = RoundedCornerShape(
    topStart = HiNotesCorners.Canvas,
    topEnd = HiNotesCorners.Canvas,
    bottomStart = HiNotesCorners.GroupInner,
    bottomEnd = HiNotesCorners.GroupInner,
)

private val CanvasShape = RoundedCornerShape(
    topStart = HiNotesCorners.GroupInner,
    topEnd = HiNotesCorners.GroupInner,
    bottomStart = HiNotesCorners.Canvas,
    bottomEnd = HiNotesCorners.Canvas,
)

/**
 * The note editor.
 *
 * Layout follows the design: a top row with the back button and the split button (save plus its
 * menu), a filled 68dp title box, and the canvas (`surfaceContainerHigh`) with the connected
 * formatting toolbar drawn over its lower edge. The title box and the canvas share the grouped
 * treatment - a 3dp seam, small corners facing each other, large corners facing outwards - so the
 * note's title and its body read as one surface.
 *
 * The canvas takes the height that is left over instead of being pinned to the design's 712dp.
 * Those two are the same number on the design's own frame - 44 header + 24 + 68 title + 12 leaves
 * 712 of an 820dp frame - but pinning it means that on any shorter viewport the toolbar at the
 * bottom of the canvas is pushed below the fold, and the one control the note cannot be written
 * without is the one that gets cut. Taking the remainder also means the outer column never needs
 * to scroll: the canvas scrolls its own content, and the keyboard simply shortens the canvas
 * instead of covering the toolbar.
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
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var showProperties by remember { mutableStateOf(false) }

    val untitled = stringResource(R.string.home_untitled)

    // ------------------------------------------------------------ load the note

    LaunchedEffect(noteId) {
        loadComplete = false
        // A brand-new note is *not* written to the database here. Creating the row on open meant
        // that simply visiting the editor and backing out - or losing the app to the recents
        // screen - left an empty "Untitled" note behind, and every restore of the editor added
        // another one. The note is held in memory and only reaches the repository when the user
        // saves something worth keeping.
        val note = when {
            noteId == NEW_NOTE_ID || noteId.isBlank() -> Note()
            else -> viewModel.notesRepository.note(noteId) ?: Note()
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

    /** True while neither the title nor the body holds a single character. */
    fun isBlankNote(): Boolean = title.isBlank() && editorState.value.text.isBlank()

    fun leave() {
        // A note with nothing in it has nothing to lose, so it is not worth a question: leaving
        // an empty editor just leaves. It is also not written on the way out - an emptied note
        // keeps whatever the database already holds rather than being deleted by a back press.
        if (dirty && !isBlankNote()) confirmDiscard = true else onBack()
    }

    // Back gesture and system back run the same guard as the toolbar button.
    BackHandler(enabled = dirty || findOpen) {
        when {
            findOpen -> findOpen = false
            else -> leave()
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

                // Edit / preview, one button, both directions.
                //
                // It lives in the header rather than in the toolbar because the toolbar is an
                // editing control and preview mode takes it away: a switch drawn there could only
                // ever be pressed one way, leaving the overflow menu to do the actual coming back.
                // Up here it is present in both modes, which is what makes it a switch. The glyph
                // shows the mode the tap leads to.
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { previewMode = !previewMode },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(percent = 50),
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    SymbolIcon(
                        codepoint = if (previewMode) Symbols.Edit else Symbols.Visibility,
                        contentDescription = stringResource(
                            if (previewMode) {
                                R.string.editor_mode_edit
                            } else {
                                R.string.editor_mode_preview
                            },
                        ),
                        size = 24.dp,
                    )
                }

                // Pushes the actions to the right edge.
                Spacer(Modifier.weight(1f))

                // Save, plus the menu of secondary actions, as one split control: the same 44dp
                // height and the same 16dp corners as before, split in two by a 2dp seam.
                SplitButton(
                    primaryIcon = Symbols.Check,
                    primaryContentDescription = stringResource(R.string.editor_save),
                    onPrimaryClick = {
                        // Save and leave. No snackbar on this path: `showSnackbar` suspends until
                        // the message has been on screen for its full duration, so announcing
                        // "saved" here held the editor open for four seconds after the tap. The
                        // note appearing in the list behind is the feedback.
                        scope.launch {
                            persist()
                            onBack()
                        }
                    },
                    menuItems = buildList {
                        add(
                            SplitButtonMenuItem(
                                icon = Symbols.Info,
                                label = stringResource(R.string.editor_properties),
                            ) { showProperties = true },
                        )
                        add(
                            SplitButtonMenuItem(
                                icon = Symbols.Share,
                                label = stringResource(R.string.editor_share),
                            ) {
                                // Shares what is on screen, saved or not: a note the user is
                                // still writing is exactly the one they may want to send.
                                // Nothing is persisted by this action, and a blank note is
                                // still shareable (it goes out as its title), so the share
                                // sheet always appears - a silent no-op here is what made this
                                // button look broken.
                                val body = editorState.value.text
                                val heading = title.ifBlank {
                                    body.lineSequence().firstOrNull { it.isNotBlank() }
                                        ?.trim().orEmpty()
                                }.ifBlank { untitled }
                                scope.launch {
                                    if (!shareNote(context, heading, body)) {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.editor_share_failed),
                                        )
                                    }
                                }
                            },
                        )
                        // The label follows the note's state, so the menu never offers to lock a
                        // note that is already locked.
                        add(
                            SplitButtonMenuItem(
                                icon = Symbols.Lock,
                                label = stringResource(
                                    if (loadedNote?.isLocked == true) {
                                        R.string.unlock_make_public
                                    } else {
                                        R.string.unlock_make_private
                                    },
                                ),
                            ) {
                                scope.launch {
                                    // Persist first: locking a note must not discard what the
                                    // user has typed since the last save.
                                    val note = loadedNote
                                    if (note != null && persist()) {
                                        viewModel.setNoteLocked(note.id, !note.isLocked)
                                    }
                                }
                            },
                        )
                        add(
                            SplitButtonMenuItem(
                                icon = Symbols.Delete,
                                label = stringResource(R.string.editor_delete),
                                onClick = { confirmDeleteNote = true },
                                destructive = true,
                            ),
                        )
                    },
                    menuContentDescription = stringResource(R.string.editor_more_actions),
                    // Dressed like the exit button on the other side of the row: no container,
                    // and one content colour for both segments - including the save tick, which
                    // used to be the only primary-tinted control in the header.
                    containerColor = Color.Transparent,
                    primaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    menuContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(HEADER_GAP))

            // Title. A filled box rather than an outlined field, per the design: the same
            // container colour as the canvas below it, with the label acting as its placeholder
            // and its lower corners pulled in so the two boxes read as one connected surface.
            // Read-only in preview mode, because the preview promises that the note cannot be
            // changed.
            TextField(
                value = title,
                onValueChange = {
                    title = it
                    dirty = true
                },
                label = { Text(stringResource(R.string.editor_title_label)) },
                singleLine = true,
                readOnly = previewMode,
                shape = TitleShape,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = (17f * settings.noteFontScale.multiplier).sp,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(TITLE_HEIGHT),
            )

            // The 3dp seam between the two boxes, the same one that separates two settings rows.
            Spacer(Modifier.height(HiNotesCorners.GroupGap))

            // Canvas: the editor body sits on this container and the toolbar is drawn over it.
            // It takes the height left over by everything above it, which is the design's 712dp
            // on the design's own frame and a little less on a shorter screen.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = CanvasShape,
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
                    // The note is split into prose and task lines so a checkbox in the preview is
                    // a real Material 3 checkbox that writes back into the note, rather than the
                    // ☐ glyph the renderer draws. With Markdown off the body is plain text and
                    // `[ ]` is only ever text, so it is left alone.
                    val blocks = remember(editorState.value.text, settings.markdownEnabled) {
                        if (settings.markdownEnabled) {
                            Markdown.previewBlocks(editorState.value.text)
                        } else {
                            listOf(
                                Markdown.PreviewBlock.Prose(AnnotatedString(editorState.value.text)),
                            )
                        }
                    }
                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column {
                            blocks.forEach { block ->
                                when (block) {
                                    is Markdown.PreviewBlock.Prose ->
                                        Text(text = block.text, style = bodyStyle)

                                    // A rule is a drawn line rather than a run of dashes: it spans
                                    // the note's own width at any font size, and it cannot wrap the
                                    // way a fixed number of glyphs could.
                                    Markdown.PreviewBlock.Divider -> HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )

                                    is Markdown.PreviewBlock.Task -> PreviewTaskRow(
                                        block = block,
                                        style = bodyStyle,
                                        onToggle = { checked ->
                                            val text = editorState.value.text
                                            // A discrete edit, so it is one undo step - and the
                                            // line index is re-checked inside against the text
                                            // that is current now, not the one this row drew.
                                            editorState.applyEdit(
                                                editorState.value.copy(
                                                    text = Markdown.setTaskChecked(
                                                        source = text,
                                                        line = block.line,
                                                        checked = checked,
                                                    ),
                                                ),
                                            )
                                        },
                                    )
                                }
                            }
                        }
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
                                // A bare "[ ] " rather than the GitHub task-list "- [ ] ": the
                                // button is pressed at the start of a line to make a checkbox,
                                // and the renderer understands both forms.
                                editorState.applyEdit(
                                    Markdown.toggleLinePrefix(editorState.value, "[ ] "),
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
                        size = ExpressiveButtonSize.Compact,
                    )
                }
            }

            // Nothing is printed under the canvas in preview mode: the mode already reads from
            // the rendered note and the toolbar's absence, and a line of chrome under a page the
            // user is reading was one line too many.
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

/**
 * Touch target for a checkbox inside a note.
 *
 * The Material 3 default is 48dp, which is right for a settings switch and heavy for a line of a
 * note: a checklist of ten items would be 480dp of scrolling. 36dp keeps the target comfortably
 * above the 24dp a finger needs while letting the preview still read as prose.
 */
private val PreviewCheckboxTarget = 36.dp

/**
 * One task-list line in the preview: a real [Checkbox] beside the item's text.
 *
 * The checkbox is the control the note itself asked for, so it is drawn by Material 3 rather than
 * by the renderer's ☐ glyph, and tapping it edits the note. Merging the row's semantics keeps a
 * screen reader announcing the item's text together with its state instead of reading a bare
 * "checkbox" with no idea what it belongs to.
 */
@Composable
private fun PreviewTaskRow(
    block: Markdown.PreviewBlock.Task,
    style: TextStyle,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides PreviewCheckboxTarget,
        ) {
            Checkbox(checked = block.checked, onCheckedChange = onToggle)
        }
        Spacer(Modifier.width(4.dp))
        Text(text = block.text, style = style, modifier = Modifier.weight(1f))
    }
}
