package com.hiapps.hinotes.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.NoteImages
import com.hiapps.hinotes.data.countNoteCharacters
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.NEW_NOTE_ID
import com.hiapps.hinotes.ui.components.ConfirmDialog
import com.hiapps.hinotes.ui.components.ConfirmStyle
import com.hiapps.hinotes.ui.components.ConnectedIconButton
import com.hiapps.hinotes.ui.components.ConnectedIconButtonGroup
import com.hiapps.hinotes.ui.components.EditorActionButtons
import com.hiapps.hinotes.ui.components.EditorMenuItem
import com.hiapps.hinotes.ui.components.ExpressiveButtonSize
import com.hiapps.hinotes.ui.components.MarkdownTaskRow
import com.hiapps.hinotes.ui.components.MarkdownText
import com.hiapps.hinotes.ui.components.PropertiesSheet
import com.hiapps.hinotes.ui.editor.EditorState
import com.hiapps.hinotes.ui.editor.Markdown
import com.hiapps.hinotes.ui.icons.SymbolIcon
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.HiNotesCorners
import com.hiapps.hinotes.ui.theme.LocalDarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How long typing must pause before an edit becomes its own undo step. */
private const val UNDO_COALESCE_MS = 400L

/** Names this screen in the log when something the app cannot prevent goes wrong. */
private const val TAG = "HiNotesEditor"

/**
 * How long save-as-you-type waits after the last keystroke before it writes.
 *
 * Long enough that a burst of typing is one write rather than one per character, short enough
 * that the note on disk is never more than a moment behind the note on screen - which is what
 * lets the editor leave without asking anything.
 */
private const val SAVE_AS_YOU_TYPE_MS = 1_200L

/** How close to the canvas edge a dragged selection keeps the handle it is following. */
private val SelectionFollowMargin = 8.dp

/** How far the formatting toolbar is inset from the canvas' lower edge, on every side. */
private val ToolbarInset = 12.dp

/**
 * How far above that edge the note's text stops.
 *
 * The toolbar floats over the canvas rather than taking a row of its own, so the writing area has to
 * be kept off it. It ends one toolbar plus one [ToolbarInset] on each side of it above the canvas'
 * lower edge, which leaves the same 12dp between the text and the toolbar as between the toolbar and
 * the bottom of the note - and the toolbar then reads as sitting in the space left for it instead of
 * as a bar the text runs into.
 */
private val BodyBottomInset = ExpressiveButtonSize.Compact.height + ToolbarInset * 2

/**
 * How far a disabled control's content is dimmed.
 *
 * 38% of `onSurface` is the alpha Material 3 gives a disabled icon, and the one the editor's own
 * toolbar already uses for a disabled button ([ConnectedIconButtonGroup]), so the header's
 * greyed-out switch matches the controls below it.
 */
private const val DisabledAlpha = 0.38f

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
 * The container the title box and the canvas are painted with.
 *
 * The light scheme keeps both on `surfaceContainerHigh`. The dark scheme steps one container
 * level down, which is what "slightly deeper" means on a phone at low brightness: the note's own
 * surface still sits above the page, but the step between it and the text on it is wider.
 */
@Composable
private fun editorContainer(): Color = if (LocalDarkTheme.current) {
    MaterialTheme.colorScheme.surfaceContainer
} else {
    MaterialTheme.colorScheme.surfaceContainerHigh
}

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

    // The note the editor opens on.
    //
    // Read here rather than in the effect below, because a note opened from the list is already in
    // the repository's in-memory snapshot and the frame that slides in can therefore carry its
    // text. Loading it after that frame had been drawn left the editor empty for one frame and
    // then laid the whole note out again, in the middle of the push animation - which is what the
    // stutter on the way into a note was. `cachedNote` reads memory only, so nothing waits on disk.
    val incoming = remember(noteId) {
        if (noteId == NEW_NOTE_ID || noteId.isBlank()) {
            Note()
        } else {
            viewModel.notesRepository.cachedNote(noteId)
        }
    }

    // The note, so a different note opening in the same editor entry starts from its own text
    // instead of leaving the previous one's behind.
    val editorState = remember(noteId) { EditorState(TextFieldValue(incoming?.content.orEmpty())) }
    var title by remember(noteId) { mutableStateOf(incoming?.title.orEmpty()) }
    var loadedNote by remember(noteId) { mutableStateOf(incoming) }
    var previewMode by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var findOpen by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    // Which match the find bar is on, and whether it has located one at all: the note stays
    // unmarked until enter is pressed, because that press is the one the bar asks for.
    var findIndex by remember { mutableIntStateOf(0) }
    var findJumped by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }

    val untitled = stringResource(R.string.home_untitled)

    /**
     * Whether the note on screen differs from the note on disk.
     *
     * Derived from the two rather than tracked with a flag while the user types. A flag has to be
     * told when the initial load has finished, and the flow that reports typing fires while that
     * load is still in flight - which is how a note that had just been opened came to look
     * modified, and why leaving one asked about changes that had never been made.
     */
    // The title is compared trimmed, because that is how the repository stores it: comparing the
    // raw draft against the stored title left a title with a trailing space looking modified
    // forever, which asked about work that was already on disk and moved the note up a list
    // sorted by "last updated" for no reason.
    val modified = title.trim() != loadedNote?.title?.trim().orEmpty() ||
        editorState.value.text != loadedNote?.content.orEmpty()

    // ------------------------------------------------------------ load the note

    LaunchedEffect(noteId) {
        // A brand-new note is *not* written to the database here. Creating the row on open meant
        // that simply visiting the editor and backing out - or losing the app to the recents
        // screen - left an empty "Untitled" note behind, and every restore of the editor added
        // another one. The note is held in memory and only reaches the repository when the user
        // saves something worth keeping.
        //
        // The note itself is already on screen unless it was missing from the snapshot, in which
        // case it is read from the database here - off the main thread.
        val note = loadedNote ?: viewModel.notesRepository.note(noteId) ?: Note()
        viewModel.openNote(note.id)
        if (note !== loadedNote) {
            loadedNote = note
            title = note.title
            editorState.reset(TextFieldValue(note.content))
        }
        previewMode = settings.startInPreview
    }

    // Keep the bound note in step when the repository changes it (lock or pin toggled). The whole
    // note is the key, not just the flag that used to be the only one: a pin written to the
    // repository has to reach this copy too, or the next save would write the stale one back.
    val boundNote by viewModel.editingNote.collectAsStateWithLifecycle()
    LaunchedEffect(boundNote) {
        boundNote?.let { if (it.id == loadedNote?.id) loadedNote = it }
    }

    /**
     * Whether the note on screen is locked.
     *
     * A locked note is readable rather than writable: it is shown the way a preview of it is shown,
     * the formatting toolbar goes with the text field, and the edit/preview switch is drawn greyed
     * out rather than removed - while everything that only reads the note stays: share, properties,
     * and the menu entry that unlocks it again.
     */
    val locked = loadedNote?.isLocked == true

    /**
     * Whether the note is drawn as a rendered page rather than as a text field.
     *
     * The preview asks for that, and so does the lock. A locked note has nothing left to write
     * into, so it is shown the way the preview shows it - rendered, with no cursor and no text
     * input behind it - which is also why the switch in the header is drawn greyed out rather than
     * changing anything: the mode it would switch to is the one the lock has taken away.
     */
    val showingPreview = previewMode || locked

    /**
     * Writes the draft, and reports whether it reached the database.
     *
     * False means nothing was written: the note had not loaded yet, the draft was blank and the
     * repository discarded it, or the write itself failed. A failed write is reported rather than
     * thrown, so a storage problem cannot take the editor - and the text still in it - down; a
     * cancelled one is not a failure and is passed on, because swallowing it would leave the caller
     * acting on an answer that never came.
     */
    suspend fun persist(): Boolean {
        val note = loadedNote ?: return false
        return try {
            val stored = viewModel.saveNote(
                note.copy(title = title, content = editorState.value.text),
            ) ?: return false
            loadedNote = stored
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            false
        }
    }

    /** True while neither the title nor the body holds a single character. */
    fun isBlankNote(): Boolean = title.isBlank() && editorState.value.text.isBlank()

    // ------------------------------------------------------------ undo coalescing

    LaunchedEffect(editorState) {
        snapshotFlow { editorState.value.text }
            .collect {
                delay(UNDO_COALESCE_MS)
                editorState.commitPending()
            }
    }

    // --------------------------------------------------------- save as you type

    // The text is part of the key, so every keystroke restarts the delay: a burst of typing is one
    // write, and what is on disk is never more than a moment behind what is on screen. Nothing is
    // announced - a note that writes itself should not interrupt the writing.
    LaunchedEffect(settings.autoSave, title, editorState.value.text) {
        if (!settings.autoSave || !modified || isBlankNote()) return@LaunchedEffect
        delay(SAVE_AS_YOU_TYPE_MS)
        persist()
    }

    fun leave() {
        // A note with nothing in it has nothing to lose, so it is not worth a question: leaving
        // an empty editor just leaves. It is also not written on the way out - an emptied note
        // keeps whatever the database already holds rather than being deleted by a back press.
        //
        // Leaving is also the only thing that writes on the way out: there is no dispose-time save,
        // because a write racing the note's own deletion would put the note back.
        val unsaved = modified && !isBlankNote()
        when {
            // Save-as-you-type already holds the note, so the last write only picks up the
            // keystrokes since it ran and the user is not asked about work that is on disk.
            settings.autoSave && unsaved -> scope.launch {
                persist()
                onBack()
            }
            // With it off, that same work is worth one question.
            !settings.autoSave && unsaved -> confirmDiscard = true
            // Nothing to write and nothing to ask about: a note that was opened and read, or one
            // that was emptied while it was open. Writing here would move it up a list sorted by
            // "last updated" for no reason at all.
            else -> onBack()
        }
    }

    // Back gesture and system back run the same guard as the header button.
    BackHandler(enabled = modified || findOpen) {
        when {
            findOpen -> findOpen = false
            else -> leave()
        }
    }

    // Entering preview - by the switch or by the lock - withdraws every editing affordance, so the
    // keyboard goes with them: a text field that is merely read-only would still hold focus and
    // keep the IME up. The find bar goes too, because the toolbar that opens it is not drawn.
    LaunchedEffect(showingPreview) {
        if (showingPreview) {
            findOpen = false
            focusManager.clearFocus()
        }
    }

    // ---------------------------------------------------------------- find in note

    /**
     * Every occurrence of the find query, in the order they appear.
     *
     * Kept as ranges rather than as a count: the two things the bar does with a match - put the
     * caret on it and mark it - both need to know where it is. The search ignores case, which is
     * what a reader expects of a note search.
     */
    val findMatches = remember(findQuery, editorState.value.text) {
        if (findQuery.isBlank()) {
            emptyList()
        } else {
            Regex(Regex.escape(findQuery), RegexOption.IGNORE_CASE)
                .findAll(editorState.value.text)
                .map { it.range }
                .toList()
        }
    }

    /** The match the bar is on, kept inside the list the note currently has. */
    val currentFindIndex = if (findMatches.isEmpty()) {
        0
    } else {
        findIndex.coerceIn(0, findMatches.lastIndex)
    }

    /**
     * Locates the match at [index], wrapping round to the first one after the last.
     *
     * Locating is two visible things and nothing more: the caret goes onto the match, which is what
     * the canvas follows when it scrolls it into view, and the match is painted with the scheme's
     * highlight while it is the current one. The text is not touched, so a search never becomes an
     * undo step.
     */
    fun locate(index: Int) {
        val match = findMatches.getOrNull(index) ?: return
        findIndex = index
        findJumped = true
        editorState.select(TextRange(match.first, match.last + 1))
    }

    /**
     * The press of enter: the first match, then the next one, and after the last the first again.
     *
     * The wrap is what makes the key useful at the end of a note: a search that stops responding
     * once it has reached the bottom gives no sign that it is finished rather than broken.
     */
    fun jumpToNextMatch() {
        if (findMatches.isEmpty()) return
        locate(if (findJumped) (currentFindIndex + 1) % findMatches.size else 0)
    }

    // Closing the bar puts the note back the way it was: nothing stays marked once the search it
    // belonged to has gone.
    LaunchedEffect(findOpen) {
        if (!findOpen) {
            findJumped = false
            findIndex = 0
        }
    }

    // ------------------------------------------------------------- insert a picture

    /**
     * Copies a picture into the note.
     *
     * The copy into the app's own storage is a file, sometimes a large one, so it happens off the
     * main thread; the note is edited once the address that copy lives at is in hand. A picture
     * that could not be read or written says so, because a button that appears to do nothing is
     * worse than one that says it failed.
     */
    suspend fun importPicture(uri: Uri) {
        val url = withContext(Dispatchers.IO) { NoteImages.import(context, uri) }
        if (url == null) {
            Log.e(TAG, "the picked picture could not be copied into the note")
            snackbarHostState.showSnackbar(context.getString(R.string.editor_image_failed))
        } else {
            Log.i(TAG, "picture copied to $url")
            editorState.applyEdit(Markdown.insertImage(editorState.value, url))
            // Stored at once rather than at the end of the auto-save pause: a picture the user has
            // just imported is the one thing on screen they could not type again, and a screen that
            // is rebuilt inside that pause would lose it.
            persist()
            // The page turns to the note as a reader sees it, which is the only place a picture is
            // drawn: the writing area is the note's Markdown source, so a picture imported there
            // would otherwise arrive as the address it is written with and look like nothing
            // happened. Readers who want to keep writing switch back with the mode button.
            previewMode = true
        }
    }

    /**
     * Asks the user for a picture.
     *
     * The pick is started with `Activity.startActivityForResult` and a request code this app
     * chooses, for the reason [PickedImageRelay] gives: the activity-result registry hands a launch
     * a request code wider than 16 bits, and this app's activity is a `FragmentActivity` - the lock
     * screen's prompt requires one - which refuses such a code outright. That refusal is what made
     * the picture button look dead: no picker opened, and nothing was reported.
     *
     * Each picker is tried in turn and every attempt is logged with what it was asked for, because a
     * device that answers for none of them is a report this app cannot guess at from the outside. A
     * tap that none of them can answer says it failed rather than doing nothing; see
     * [pickImageIntents].
     */
    fun pickImage() {
        val activity = context.findActivity()
        if (activity != null) {
            for (intent in pickImageIntents()) {
                try {
                    Log.i(TAG, "asking for a picture with ${intent.action}")
                    activity.startActivityForResult(intent, REQUEST_PICK_IMAGE)
                    return
                } catch (t: Throwable) {
                    Log.e(TAG, "could not open the picture picker for ${intent.action}", t)
                }
            }
            Log.e(TAG, "no installed app answered for a picture")
        } else {
            Log.e(TAG, "there is no activity to ask for a picture")
        }
        scope.launch {
            snackbarHostState.showSnackbar(context.getString(R.string.editor_image_failed))
        }
    }

    /**
     * Takes the picture the picker returned, if one came back.
     *
     * The picker is started with the activity, so its answer arrives there and is handed over
     * through this relay. A picker that answered without a picture is reported as a failed try,
     * because it is the one case that would otherwise leave the tap unanswered.
     */
    val pickedImage by PickedImageRelay.answer
    LaunchedEffect(pickedImage) {
        val answer = pickedImage ?: return@LaunchedEffect
        // Taken before the copy starts: the relay holds one value, and a second pick must not be
        // lost because the first was still being copied when it arrived.
        PickedImageRelay.consume()
        // The work runs on the screen's own scope, not inside this effect. Clearing the relay is a
        // recomposition of this very `pickedImage`, which is this effect's key - so anything that
        // suspends in here would be cancelled by the clear, half way through copying the picture,
        // and neither the insertion nor the failure would ever happen. That is exactly what made an
        // imported picture disappear without a word.
        scope.launch {
            when (answer) {
                PickedImage.Refused -> snackbarHostState.showSnackbar(
                    context.getString(R.string.editor_image_failed),
                )
                is PickedImage.Imported -> importPicture(answer.uri)
            }
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
                //
                // A locked note is the one case where the switch is drawn but cannot be used: the
                // lock says the note cannot be written, so the button that offers to write in it
                // goes grey rather than disappearing - the header keeps its shape, and the state
                // that took the control away stays visible.
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        previewMode = !previewMode
                    },
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(percent = 50),
                    enabled = !locked,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = DisabledAlpha,
                        ),
                    ),
                ) {
                    SymbolIcon(
                        codepoint = if (showingPreview) Symbols.Edit else Symbols.SpaceDashboard,
                        contentDescription = stringResource(
                            if (showingPreview) {
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

                // Find in note, immediately left of Save.
                //
                // It used to be the first button of the formatting toolbar, which made searching a
                // note something the toolbar had to be on screen for - and the toolbar is an
                // editing control that is not drawn for a note being read. Up here it belongs to
                // the note itself, next to the other things that act on it. It stays out of the
                // preview for the same reason it always was: a rendered page has no text field to
                // mark a match in.
                if (!showingPreview) {
                    IconButton(
                        onClick = { findOpen = !findOpen },
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(percent = 50),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        SymbolIcon(
                            codepoint = Symbols.Search,
                            contentDescription = stringResource(R.string.editor_find),
                            size = 24.dp,
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }

                // Save and the menu of everything else, as two buttons of the same size and shape
                // as the find button beside them. The save half is left out while the note saves
                // itself: with auto-save on, leaving the note is the save, and a button that repeats
                // it is a button that asks to be pressed for nothing.
                EditorActionButtons(
                    saveContentDescription = stringResource(R.string.editor_save),
                    showSave = !settings.autoSave,
                    onSave = {
                        // Save and leave. No snackbar on this path: `showSnackbar` suspends until
                        // the message has been on screen for its full duration, so announcing
                        // "saved" here held the editor open for four seconds after the tap. The
                        // note appearing in the list behind is the feedback.
                        scope.launch {
                            // A blank draft is left alone rather than saved: the repository
                            // discards blank notes, so writing here would delete an existing note
                            // without asking. Deleting is the menu's job, where it is confirmed.
                            if (isBlankNote()) {
                                onBack()
                            } else if (persist()) {
                                onBack()
                            } else {
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.editor_save_failed),
                                )
                            }
                        }
                    },
                    menuItems = buildList {
                        add(
                            EditorMenuItem(
                                icon = Symbols.Info,
                                label = stringResource(R.string.editor_properties),
                            ) { showProperties = true },
                        )
                        add(
                            EditorMenuItem(
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
                        // Pin comes before lock: it is the state a reader changes while keeping
                        // the note, and the one the note's place in the list depends on. The icon
                        // and the label both follow the note's own state, so the menu never offers
                        // to pin a note that is already pinned.
                        add(
                            EditorMenuItem(
                                icon = if (loadedNote?.pinned == true) {
                                    Symbols.KeepOff
                                } else {
                                    Symbols.Keep
                                },
                                label = stringResource(
                                    if (loadedNote?.pinned == true) {
                                        R.string.editor_unpin
                                    } else {
                                        R.string.editor_pin
                                    },
                                ),
                            ) {
                                scope.launch {
                                    // Persist first, for the same reason locking does: a pin must
                                    // not discard what the user has typed since the last save.
                                    val note = loadedNote
                                    if (note != null && persist()) {
                                        viewModel.setNotePinned(note.id, !note.pinned)
                                    }
                                }
                            },
                        )
                        // The label follows the note's state, so the menu never offers to lock a
                        // note that is already locked - and the icon moves with the label, an open
                        // padlock for the action that takes the lock off.
                        add(
                            EditorMenuItem(
                                icon = if (loadedNote?.isLocked == true) {
                                    Symbols.LockOpen
                                } else {
                                    Symbols.Lock
                                },
                                label = stringResource(
                                    if (loadedNote?.isLocked == true) {
                                        R.string.editor_unlock
                                    } else {
                                        R.string.editor_lock
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
                            EditorMenuItem(
                                icon = Symbols.Delete,
                                label = stringResource(R.string.editor_delete),
                                onClick = {
                                    // Gone at once, with the window the note list offers to bring it
                                    // back. A question here would be the third answer the user has
                                    // already given for one deletion - the note was opened, the menu
                                    // was opened, the entry was pressed - and a window can be taken
                                    // back where a question can only be answered once.
                                    //
                                    // What is on screen is stored first, so a note brought back is
                                    // the one that was being read rather than its last save.
                                    val note = loadedNote
                                    scope.launch {
                                        // A blank draft is discarded rather than held: the store
                                        // drops it on the save, so there would be nothing to bring
                                        // back and an offer that could not keep its word.
                                        val stored = note?.let {
                                            viewModel.saveNote(
                                                it.copy(
                                                    title = title,
                                                    content = editorState.value.text,
                                                ),
                                            )
                                        }
                                        if (stored != null) viewModel.stageDelete(listOf(stored))
                                        onBack()
                                    }
                                },
                                destructive = true,
                            ),
                        )
                    },
                    moreContentDescription = stringResource(R.string.editor_more_actions),
                )
            }

            Spacer(Modifier.height(HEADER_GAP))

            // Title. A filled box rather than an outlined field, per the design: the same
            // container colour as the canvas below it, and its lower corners pulled in so the two
            // boxes read as one connected surface. Read-only in preview mode, because the preview
            // promises that the note cannot be changed.
            //
            // Built like the body underneath it: a bare text field on the container, with the hint
            // drawn inside the box. A Material label would instead float up to the top-left corner
            // of the box as soon as the field was focused, which is not what the body's hint does
            // and not what a title's hint should do - it should disappear once there is a title.
            val titleStyle = LocalTextStyle.current.copy(
                fontSize = (17f * settings.noteFontScale.multiplier).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // The title box, with the find bar laid over it while a search is open. The bar belongs
            // to the note's own header rather than to the page: a search box drawn over the text it
            // is searching hides the matches it has just found, while the title is not part of what
            // the search looks through.
            Box {
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    // Read-only in either way of reading the note, because both promise that what is
                    // on screen cannot be changed by typing into it.
                    readOnly = showingPreview,
                    textStyle = titleStyle,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(TITLE_HEIGHT)
                        .background(color = editorContainer(), shape = TitleShape)
                        .padding(horizontal = 16.dp),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.editor_title_label),
                                    style = titleStyle.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    ),
                                )
                            }
                            inner()
                        }
                    },
                )

                if (findOpen) {
                    FindBar(
                        query = findQuery,
                        onQueryChange = { query ->
                            findQuery = query
                            // A new query is a new search: the match to be on is the first one,
                            // and nothing is located until enter is pressed again.
                            findIndex = 0
                            findJumped = false
                        },
                        status = when {
                            findQuery.isBlank() -> stringResource(R.string.editor_find)
                            findMatches.isEmpty() -> stringResource(R.string.editor_find_no_match)
                            else -> stringResource(
                                R.string.editor_find_matches,
                                currentFindIndex + 1,
                                findMatches.size,
                            )
                        },
                        onSearch = { jumpToNextMatch() },
                    )
                }
            }

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
                        color = editorContainer(),
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

                if (showingPreview) {
                    // The note is split into prose and task lines so a checkbox in the preview is
                    // a real Material 3 checkbox that writes back into the note, rather than the
                    // ☐ glyph the renderer draws. With Markdown off the body is plain text and
                    // `[ ]` is only ever text, so it is left alone.
                    val linkColor = MaterialTheme.colorScheme.primary
                    val blocks = remember(editorState.value.text, settings.markdownEnabled, linkColor) {
                        if (settings.markdownEnabled) {
                            Markdown.previewBlocks(editorState.value.text, linkColor)
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
                                        MarkdownText(text = block.text, style = bodyStyle)

                                    // A picture is drawn where its line is: the address the note
                                    // holds for it is not what a reader came for.
                                    is Markdown.PreviewBlock.Image -> PreviewImage(
                                        url = block.url,
                                        description = block.alt,
                                        style = bodyStyle,
                                        modifier = Modifier.padding(vertical = 4.dp),
                                    )

                                    // A rule is a drawn line rather than a run of dashes: it spans
                                    // the note's own width at any font size, and it cannot wrap the
                                    // way a fixed number of glyphs could.
                                    Markdown.PreviewBlock.Divider -> HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant,
                                    )

                                    is Markdown.PreviewBlock.Table -> PreviewTable(
                                        block = block,
                                        style = bodyStyle,
                                    )

                                    is Markdown.PreviewBlock.Task -> MarkdownTaskRow(
                                        checked = block.checked,
                                        text = block.text,
                                        style = bodyStyle,
                                        // Ticking a box is an edit, and a locked note is not
                                        // editable: the box still shows the state the text records,
                                        // it just cannot be turned into a change.
                                        enabled = !locked,
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
                    val bodyScroll = rememberScrollState()

                    // The note as the field should draw it: the same characters with the current
                    // find match painted. Built once per change of text or match and reused for
                    // every frame in between, because the field draws through it each frame.
                    val findHighlight = MaterialTheme.colorScheme.primaryContainer
                    val markedBody = remember(
                        editorState.value.text,
                        findMatches,
                        currentFindIndex,
                        findJumped,
                        findHighlight,
                    ) {
                        if (findJumped) {
                            highlightedText(
                                text = editorState.value.text,
                                range = findMatches.getOrNull(currentFindIndex),
                                color = findHighlight,
                            )
                        } else {
                            AnnotatedString(editorState.value.text)
                        }
                    }
                    var bodyLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
                    val followedHandle = remember { mutableStateOf(editorState.value.selection) }
                    val followMargin = with(LocalDensity.current) {
                        SelectionFollowMargin.toPx()
                    }

                    // Keep the handle that is being dragged inside the canvas.
                    //
                    // A text field that scrolls its own content does this by itself; this one is
                    // scrolled by the canvas it sits in, and the platform's selection gestures only
                    // move the handles. Without this, dragging a handle past the lower edge went on
                    // selecting text that had scrolled out of sight, so the selection grew somewhere
                    // the user could not see it. The end that moved is the one to follow: dragging
                    // the far handle moves the end, dragging the near one moves the start.
                    LaunchedEffect(editorState.value.selection, bodyScroll.viewportSize) {
                        val layout = bodyLayout ?: return@LaunchedEffect
                        val viewport = bodyScroll.viewportSize
                        if (viewport == 0) return@LaunchedEffect
                        val selection = editorState.value.selection
                        val previous = followedHandle.value
                        followedHandle.value = selection
                        // Clamped against the layout rather than the text: the layout this runs
                        // against can be one frame behind a paste or an undo.
                        val followed = (
                            if (selection.end != previous.end) selection.end else selection.start
                            ).coerceIn(0, layout.layoutInput.text.length)
                        val caret = layout.getCursorRect(followed)
                        val top = caret.top - bodyScroll.value
                        val bottom = caret.bottom - bodyScroll.value
                        when {
                            top < followMargin ->
                                bodyScroll.scrollTo((bodyScroll.value + top - followMargin).toInt())
                            bottom > viewport - followMargin -> bodyScroll.scrollTo(
                                (bodyScroll.value + bottom + followMargin - viewport).toInt(),
                            )
                        }
                    }

                    BasicTextField(
                        value = editorState.value,
                        onValueChange = editorState::onUserInput,
                        visualTransformation = remember(markedBody) { MarkedText(markedBody) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = BodyBottomInset,
                            )
                            .verticalScroll(bodyScroll),
                        textStyle = bodyStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        onTextLayout = { bodyLayout = it },
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

                // Connected formatting toolbar, drawn over the lower edge of the canvas.
                //
                // It is an editing control, so a note shown as a page does not get one: there is
                // nothing in a rendered note for it to act on, and leaving it up would suggest the
                // note can still be changed. That covers both ways of reading - the preview and the
                // lock without exception: every button on it belongs to working on the note rather
                // than to reading it. In the edit mode it is simply there: the writing area keeps a
                // toolbar's worth of room free at its lower edge, so the bar sits in the space left
                // for it from the moment the note is opened to be written in.
                if (!showingPreview) {
                    ConnectedIconButtonGroup(
                        buttons = listOf(
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
                            // Last, and the only button that brings something in from outside:
                            // the formatting buttons change what is already written, this one
                            // adds to it.
                            ConnectedIconButton(
                                icon = Symbols.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.editor_insert_image),
                            ) { pickImage() },
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(ToolbarInset),
                        size = ExpressiveButtonSize.Compact,
                        // The same container as the note behind it, so the buttons read as
                        // controls lying on the page rather than as a second surface with an
                        // edge of its own. Only their glyphs and their pressed states show.
                        containerColor = editorContainer(),
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
            dismissLabel = stringResource(R.string.common_cancel),
            // A question rather than a warning: the labels carry the palette's accent colour,
            // which is what every other button in the app does - so this one follows the accent
            // palette and the wallpaper along with them.
            style = ConfirmStyle.Accent,
            onConfirm = {
                confirmDiscard = false
                // Leaves with the note written, which is what the button says; a write that fails
                // says so and keeps the text on screen instead of leaving without it.
                scope.launch {
                    if (persist()) {
                        onBack()
                    } else {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.editor_save_failed),
                        )
                    }
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
}

/**
 * The find-in-note bar, laid over the note's title box.
 *
 * It shares the title box's own footprint - its height, its shape and its rounded lower corners -
 * so the search reads as taking over that field for as long as it is open rather than as a second
 * surface arriving above the page. The box underneath promises a tap it no longer has, so the bar
 * takes the taps that land on it; the title is not being searched and does not need to be reached
 * while it is covered.
 *
 * @param status which match the search is on, or why there is none.
 * @param onSearch what pressing enter does: locate a match, and after that the next one.
 */
@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    status: String,
    onSearch: () -> Unit,
) {
    // The bar covers the title field, so it takes the typing as soon as it opens: a keystroke that
    // landed in the field underneath would change a title the reader cannot even see.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(TITLE_HEIGHT)
            .pointerInput(Unit) {
                // The field below is not visible and not the one being typed into, so a tap here
                // must not reach it.
                awaitEachGesture { awaitFirstDown().consume() }
            },
        shape = TitleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.editor_find_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                // Enter is the whole interaction: it locates a match, and each press after that
                // locates the next one.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            )
            Text(
                text = status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * A picture in the preview.
 *
 * The picture is decoded at the width it is drawn at rather than at the size it was taken at: a
 * phone's camera is several thousand pixels across, the note's canvas a few hundred, and decoding
 * the file whole to draw it small is how a note with a few pictures runs a phone out of memory. It
 * is decoded off the main thread for the same reason - reading and scaling a large file is not
 * something to do in the frame it is first drawn in.
 *
 * A picture that cannot be read - one whose file has gone, or an address this app cannot open - is
 * shown as the address itself, which is what the note holds and what a reader can still act on.
 *
 * @param description what the note says the picture shows, read out in place of it.
 */
@Composable
private fun PreviewImage(
    url: String,
    description: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val maxWidth = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.roundToPx()
    }
    // Three states rather than two, because a picture that is still being read is not one that could
    // not be read: the address is the answer to a picture that is missing, and showing it for the
    // frame a decode takes would flash it over every picture in the note.
    var picture by remember(url) { mutableStateOf<Bitmap?>(null) }
    var missing by remember(url) { mutableStateOf(false) }
    LaunchedEffect(url, maxWidth) {
        val decoded = withContext(Dispatchers.IO) { NoteImages.decode(context, url, maxWidth) }
        if (decoded == null) {
            // A picture the app cannot draw is the difference between "the import worked" and "the
            // note shows an address": silence here is what made the two indistinguishable before.
            Log.w(TAG, "a picture in the note could not be read: $url")
        }
        picture = decoded
        missing = decoded == null
    }

    val drawn = picture
    when {
        drawn != null -> Image(
            bitmap = drawn.asImageBitmap(),
            contentDescription = description.ifBlank { null },
            contentScale = ContentScale.FillWidth,
            modifier = modifier.fillMaxWidth(),
        )

        missing -> Text(
            text = url,
            style = style,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
}

/**
 * A note drawn with one range painted.
 *
 * A text field cannot be styled from the outside, but it can be handed a transformation of what it
 * draws - and this one changes nothing about the text: the same characters at the same offsets
 * ([OffsetMapping.Identity]), so the caret, the selection and every gesture stay where they were.
 * The marked text is built by the caller and reused, because a transformation runs on every frame
 * the field draws and copying a long note on each of them would be work for nothing.
 */
private class MarkedText(private val marked: AnnotatedString) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(marked, OffsetMapping.Identity)
}

/** [text] with [range] painted in [color]; [text] itself when there is no range to paint. */
private fun highlightedText(text: String, range: IntRange?, color: Color): AnnotatedString {
    if (range == null || range.first >= text.length) return AnnotatedString(text)
    val end = (range.last + 1).coerceAtMost(text.length)
    return buildAnnotatedString {
        append(text)
        addStyle(SpanStyle(background = color), range.first, end)
    }
}

/**
 * One pipe table in the preview.
 *
 * Every column gets an equal share of the note's width: the source says how a cell's text is
 * aligned inside its column, but not how wide any column should be, and on a phone the honest
 * answer is "as wide as everything else". A rule under the header and between the lines is what
 * makes a run of cells read as a table rather than as a paragraph with pipes in it.
 */
@Composable
private fun PreviewTable(
    block: Markdown.PreviewBlock.Table,
    style: TextStyle,
) {
    Column(Modifier.fillMaxWidth()) {
        PreviewTableRow(
            cells = block.header,
            alignments = block.alignments,
            style = style,
            header = true,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        block.rows.forEachIndexed { index, cells ->
            PreviewTableRow(
                cells = cells,
                alignments = block.alignments,
                style = style,
                header = false,
            )
            if (index != block.rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/** One line of a table: a [MarkdownText] per cell, weighted equally and aligned as the source asked. */
@Composable
private fun PreviewTableRow(
    cells: List<AnnotatedString>,
    alignments: List<Markdown.ColumnAlignment>,
    style: TextStyle,
    header: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        cells.forEachIndexed { index, cell ->
            MarkdownText(
                text = cell,
                style = if (header) style.copy(fontWeight = FontWeight.SemiBold) else style,
                textAlign = when (alignments.getOrNull(index)) {
                    Markdown.ColumnAlignment.Center -> TextAlign.Center
                    Markdown.ColumnAlignment.End -> TextAlign.End
                    else -> TextAlign.Start
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}
