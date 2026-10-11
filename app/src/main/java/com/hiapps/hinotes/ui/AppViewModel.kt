package com.hiapps.hinotes.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hiapps.hinotes.data.HiNotesSettings
import com.hiapps.hinotes.data.LockStore
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.NoteSort
import com.hiapps.hinotes.data.NotesRepository
import com.hiapps.hinotes.data.SettingsRepository
import com.hiapps.hinotes.widget.HiNotesWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Single source of truth for the running app: note list, the active search query and sort,
 * the persisted settings snapshot, and the file that the editor is currently working on.
 *
 * Screens read state from here and call the mutators below; none of them touch storage
 * directly. Everything is scoped to the Activity through [AppViewModelFactory], which is
 * enough for a single-Activity app and keeps note state alive across configuration changes.
 */
class AppViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext

    val notesRepository = NotesRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val lockStore = LockStore(appContext)

    /** The stored settings, read before the first frame so the app starts in the right theme. */
    private val initialSettings: HiNotesSettings = settingsRepository.currentBlocking()

    /**
     * Persisted settings, ready before the first frame.
     *
     * The repository is asked for its stored values up front rather than letting this start at the
     * defaults and catch up: the theme is derived from this flow, and a frame drawn before the
     * stored values arrive shows the wrong one - a light screen in front of a dark app whenever the
     * system is set the other way round. One blocking read, before anything is drawn, is the price
     * of the app opening in the colour it was left in.
     */
    val settings: StateFlow<HiNotesSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialSettings)

    /**
     * The notes the home screen shows, read before the first frame.
     *
     * Filled by the read in the `init` block below rather than starting empty and catching up: a
     * list that arrives a frame late paints the "no notes" screen in front of someone who has
     * notes. It is empty here only until that read has happened, which is before anything is drawn.
     */
    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Starts at the stored order, which is the order the notes are read in below. */
    private val _sort = MutableStateFlow(initialSettings.sortOrder)
    val sort: StateFlow<NoteSort> = _sort.asStateFlow()

    /** The note the editor is bound to; set when the editor is opened. */
    private val _editingNote = MutableStateFlow<Note?>(null)
    val editingNote: StateFlow<Note?> = _editingNote.asStateFlow()

    /** True while the app-level lock screen must be shown. */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /**
     * Notes currently selected on the home screen.
     *
     * An empty set means "not selecting", so multi-select mode is derived from the selection
     * rather than tracked separately where the two could disagree.
     */
    private val _selectedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNoteIds: StateFlow<Set<String>> = _selectedNoteIds.asStateFlow()

    init {
        // The notes are read here, while the app is still starting, the way the settings above are:
        // a list that starts empty and is filled a frame later shows "no notes" to someone who has
        // notes. One blocking read before anything is drawn is the price of opening on the list the
        // user left - and it is the same read the app would otherwise make a frame afterwards.
        // Nothing else is drawn in the meantime, because this runs before the first composition
        // returns, so the wait is invisible and the first frame is already the real list.
        runBlocking { refreshNotes() }
        // Keep the sort preference and the in-memory sort in step. The sort the list was just read
        // with is the stored one, so this has nothing to do until the setting itself changes.
        viewModelScope.launch {
            settingsRepository.settings.collect { snapshot ->
                if (_sort.value != snapshot.sortOrder) {
                    _sort.value = snapshot.sortOrder
                    notesRepository.setSort(snapshot.sortOrder)
                    refreshNotes()
                }
            }
        }
        _locked.value = lockStore.isConfigured
    }

    // ------------------------------------------------------------------- notes

    private suspend fun refreshNotes() {
        notesRepository.refresh()
        notesRepository.setSort(_sort.value)
        recomputeVisible()
    }

    private fun recomputeVisible() {
        _notes.value = notesRepository.visibleNotes()
    }

    /** Creates an empty note and binds it to the editor. */
    suspend fun startNewNote(): Note {
        val note = notesRepository.createNote()
        _editingNote.value = note
        recomputeVisible()
        return note
    }

    /** Binds an existing note to the editor. */
    suspend fun openNote(id: String) {
        _editingNote.value = notesRepository.note(id)
    }

    fun clearEditingNote() {
        _editingNote.value = null
    }

    /** Saves the editor's working copy. Returns the stored note, or null if it was discarded. */
    suspend fun saveNote(note: Note): Note? {
        val stored = notesRepository.saveNote(note)
        _editingNote.value = stored
        recomputeVisible()
        // Only the widgets that show this note are redrawn, and only when one exists: a save that
        // no widget is watching costs a lookup of the widget table and nothing else.
        HiNotesWidgetProvider.refreshNote(appContext, note.id)
        return stored
    }

    suspend fun deleteNote(id: String) {
        notesRepository.deleteNote(id)
        if (_editingNote.value?.id == id) _editingNote.value = null
        recomputeVisible()
        HiNotesWidgetProvider.refreshNote(appContext, id)
    }

    suspend fun setNoteLocked(id: String, locked: Boolean) {
        notesRepository.setLocked(id, locked)
        if (_editingNote.value?.id == id) {
            _editingNote.value = notesRepository.note(id)
        }
        recomputeVisible()
        HiNotesWidgetProvider.refreshNote(appContext, id)
    }

    fun setQuery(value: String) {
        _query.value = value
        notesRepository.setQuery(value)
        recomputeVisible()
    }

    fun setSort(value: NoteSort) {
        _sort.value = value
        notesRepository.setSort(value)
        recomputeVisible()
        viewModelScope.launch { settingsRepository.setSortOrder(value) }
    }

    // --------------------------------------------------------------- selection

    /**
     * Whether the note with [id] is locked.
     *
     * A locked note keeps its lock until it is unlocked in the editor, so it is never part of a
     * selection and can therefore never be deleted from the list.
     */
    private fun isLocked(id: String): Boolean =
        _notes.value.firstOrNull { it.id == id }?.isLocked == true

    /** Adds or removes [id]; used by long-press and by a tap while a selection exists. */
    fun toggleSelection(id: String) {
        if (isLocked(id)) return
        _selectedNoteIds.value = _selectedNoteIds.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    /** Selects every unlocked note in [ids]. */
    fun selectAllNotes(ids: List<String>) {
        _selectedNoteIds.value = ids.filterNot(::isLocked).toSet()
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
    }

    /**
     * Drops ids that are no longer present, so a selection cannot outlive the notes it refers
     * to - after a delete, after a search filters one away, or after an import replaces the list.
     */
    fun retainSelection(available: Set<String>) {
        val current = _selectedNoteIds.value
        if (current.isEmpty()) return
        val retained = current intersect available
        if (retained != current) _selectedNoteIds.value = retained
    }

    /** Deletes every selected note and leaves multi-select. Locked notes are never among them. */
    suspend fun deleteSelectedNotes() {
        val ids = _selectedNoteIds.value.filterNot(::isLocked)
        if (ids.isEmpty()) return
        ids.forEach { notesRepository.deleteNote(it) }
        if (_editingNote.value?.id in ids) _editingNote.value = null
        _selectedNoteIds.value = emptySet()
        recomputeVisible()
        // One redraw per widget, however many of the deleted notes one of them was showing.
        HiNotesWidgetProvider.refreshNotes(appContext, ids)
    }

    suspend fun noteCount(): Int = notesRepository.count()

    suspend fun allNotes(): List<Note> = notesRepository.allForExport()

    suspend fun replaceAllNotes(notes: List<Note>): Int {
        val written = notesRepository.replaceAll(notes)
        recomputeVisible()
        // An import can have changed every note at once, so every widget is redrawn.
        HiNotesWidgetProvider.refreshAll(appContext)
        return written
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { applySettings(block) }
    }

    /**
     * Applies a settings write, and redraws the widgets when it changed something they draw with.
     *
     * Importing settings and restoring the defaults write the whole snapshot at once; both come
     * through here, so a widget never keeps painting a scheme the user has just replaced.
     */
    suspend fun applySettings(block: suspend SettingsRepository.() -> Unit) {
        val before = settingsRepository.current()
        settingsRepository.block()
        if (widgetSettings(before) != widgetSettings(settingsRepository.current())) {
            HiNotesWidgetProvider.refreshAll(appContext)
        }
    }

    /**
     * The settings a widget is drawn with: its palette and theme, and whether notes are rendered as
     * Markdown. A dragged font-size slider writes settings on every step and none of those steps
     * change what a widget looks like, which is what this comparison keeps out of the widget's way.
     */
    private fun widgetSettings(settings: HiNotesSettings) = listOf(
        settings.dynamicColor,
        settings.accentPalette,
        settings.darkMode,
        settings.oledDark,
        settings.markdownEnabled,
        settings.widgetFontScale,
        settings.widgetFontWeight,
    )

    /**
     * Redraws every placed widget.
     *
     * For a change a widget draws that is not a setting: the unlock password, whose presence decides
     * whether the card shows anything at all.
     */
    fun refreshWidgets() {
        HiNotesWidgetProvider.refreshAll(appContext)
    }

    // -------------------------------------------------------------------- lock

    /** Unlocks the app for this session after the password has been verified by the caller. */
    fun unlock() {
        _locked.value = false
    }

    /** Re-evaluates whether the app should be locked, e.g. after removing the password. */
    fun refreshLockState() {
        if (!lockStore.isConfigured) _locked.value = false
    }
}

/** Builds [AppViewModel] with the application context. */
class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(AppViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }
        return AppViewModel(context) as T
    }
}
