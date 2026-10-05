package com.hiapps.hinotes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Owns note persistence and exposes an observable in-memory snapshot for the UI.
 *
 * All database work happens on [Dispatchers.IO]; the UI reads [notes] (or [visibleNotes])
 * and never touches SQLite directly.
 */
class NotesRepository(context: Context) {

    private val database = NotesDatabase(context)

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(NoteSort.UpdatedDesc)
    val sort: StateFlow<NoteSort> = _sort.asStateFlow()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _notes.value = database.all()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun setSort(value: NoteSort) {
        _sort.value = value
        _notes.value = sortNotes(_notes.value, value)
    }

    fun note(id: String): Note? = _notes.value.firstOrNull { it.id == id }
        ?: database.byId(id)

    /**
     * Notes matching the active search query, in the active sort order.
     * Locked notes stay in the list (with a badge) so the user can still reach them.
     */
    fun visibleNotes(): List<Note> {
        val q = _query.value.trim()
        val base = if (q.isEmpty()) {
            _notes.value
        } else {
            _notes.value.filter { note ->
                note.title.contains(q, ignoreCase = true) ||
                    note.content.contains(q, ignoreCase = true)
            }
        }
        return sortNotes(base, _sort.value)
    }

    /** Creates and persists an empty note, returning it so the editor can open it. */
    suspend fun createNote(): Note = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val note = Note(createdAt = now, updatedAt = now)
        database.upsert(note)
        _notes.value = sortNotes(database.all(), _sort.value)
        note
    }

    /**
     * Persists [note]. Blank notes (no title and no body) are removed instead of stored,
     * which keeps abandoned taps on "Create note" from littering the list.
     */
    suspend fun saveNote(note: Note): Note? = withContext(Dispatchers.IO) {
        val trimmedTitle = note.title.trim()
        val isEmpty = trimmedTitle.isEmpty() && note.content.isBlank()
        if (isEmpty) {
            database.delete(note.id)
            _notes.value = sortNotes(database.all(), _sort.value)
            return@withContext null
        }
        val stored = note.copy(
            title = trimmedTitle,
            updatedAt = System.currentTimeMillis(),
        )
        database.upsert(stored)
        _notes.value = sortNotes(database.all(), _sort.value)
        stored
    }

    suspend fun deleteNote(id: String) = withContext(Dispatchers.IO) {
        database.delete(id)
        _notes.value = sortNotes(database.all(), _sort.value)
    }

    suspend fun setLocked(id: String, locked: Boolean) = withContext(Dispatchers.IO) {
        val existing = database.byId(id) ?: return@withContext
        database.upsert(existing.copy(isLocked = locked, updatedAt = System.currentTimeMillis()))
        _notes.value = sortNotes(database.all(), _sort.value)
    }

    suspend fun count(): Int = withContext(Dispatchers.IO) { database.count() }

    suspend fun allForExport(): List<Note> = withContext(Dispatchers.IO) { database.all() }

    /** Replaces every note, used by notes import. Returns how many notes were written. */
    suspend fun replaceAll(notes: List<Note>): Int = withContext(Dispatchers.IO) {
        database.replaceAll(notes)
        _notes.value = sortNotes(database.all(), _sort.value)
        notes.size
    }

    private fun sortNotes(list: List<Note>, sort: NoteSort): List<Note> = when (sort) {
        NoteSort.UpdatedDesc -> list.sortedByDescending { it.updatedAt }
        NoteSort.CreatedDesc -> list.sortedByDescending { it.createdAt }
        NoteSort.TitleAsc -> list.sortedBy { it.displayTitle("").lowercase() }
    }
}
