package com.hinotes.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hinotes.app.data.HiNotesSettings
import com.hinotes.app.data.LockStore
import com.hinotes.app.data.Note
import com.hinotes.app.data.NoteSort
import com.hinotes.app.data.NotesRepository
import com.hinotes.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    /** Persisted settings, defaulted until DataStore has emitted. */
    val settings: StateFlow<HiNotesSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, HiNotesSettings())

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(NoteSort.UpdatedDesc)
    val sort: StateFlow<NoteSort> = _sort.asStateFlow()

    /** The note the editor is bound to; set when the editor is opened. */
    private val _editingNote = MutableStateFlow<Note?>(null)
    val editingNote: StateFlow<Note?> = _editingNote.asStateFlow()

    /** True while the app-level lock screen must be shown. */
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    init {
        viewModelScope.launch { refreshNotes() }
        // Keep the sort preference and the in-memory sort in step.
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
    fun openNote(id: String) {
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
        return stored
    }

    suspend fun deleteNote(id: String) {
        notesRepository.deleteNote(id)
        if (_editingNote.value?.id == id) _editingNote.value = null
        recomputeVisible()
    }

    suspend fun setNoteLocked(id: String, locked: Boolean) {
        notesRepository.setLocked(id, locked)
        if (_editingNote.value?.id == id) {
            _editingNote.value = notesRepository.note(id)
        }
        recomputeVisible()
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

    suspend fun noteCount(): Int = notesRepository.count()

    suspend fun allNotes(): List<Note> = notesRepository.allForExport()

    suspend fun replaceAllNotes(notes: List<Note>): Int {
        val written = notesRepository.replaceAll(notes)
        recomputeVisible()
        return written
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }

    // -------------------------------------------------------------------- lock

    /** Unlocks the app for this session after the password has been verified by the caller. */
    fun unlock() {
        _locked.value = false
    }

    fun lockNow() {
        if (lockStore.isConfigured) _locked.value = true
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
