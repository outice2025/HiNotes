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

    private val appContext = context.applicationContext

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

    /** The note with [id] when the in-memory snapshot holds it; never touches the database. */
    fun cachedNote(id: String): Note? = _notes.value.firstOrNull { it.id == id }

    /**
     * The note with [id], falling back to the database when the snapshot does not hold it.
     *
     * The fallback runs on [Dispatchers.IO]. Callers include the editor as it opens, and a
     * synchronous query there costs a frame in the middle of the push animation.
     */
    suspend fun note(id: String): Note? =
        cachedNote(id) ?: withContext(Dispatchers.IO) { database.byId(id) }

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
            // A note that is thrown away this way is removed exactly as a deleted one is, pictures
            // and all: the row goes, so nothing is left that could ever show them again.
            _notes.value = sortNotes(deleteNoteRow(note.id), _sort.value)
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
        _notes.value = sortNotes(deleteNoteRow(id), _sort.value)
    }

    /**
     * Writes [content] into the note stored as [id], leaving its modified time alone.
     *
     * Ticking a task on a card is an edit of what the note says and is stored like any other, but the
     * list that card stands in is ordered by that same modified time: updating it would jump the card
     * to the top of the list under the finger that ticked it. The content is written and the time is
     * kept, which is what lets a checklist be worked through from the home screen.
     *
     * @return the note as it is now stored, or null when there is no such note.
     */
    suspend fun replaceContent(id: String, content: String): Note? = withContext(Dispatchers.IO) {
        val existing = database.byId(id) ?: return@withContext null
        if (existing.content == content) return@withContext existing
        val stored = existing.copy(content = content)
        database.upsert(stored)
        _notes.value = sortNotes(database.all(), _sort.value)
        stored
    }

    /**
     * Removes the row for [id] together with the picture files only that note held.
     *
     * Every delete in the app comes through here - the editor's single note, the home screen's
     * selection, and the note the editor throws away for being blank - because the pictures have to
     * go in the same breath as the row: the note's text is the only record of which files it holds,
     * so a row that is gone is a set of files nothing can ever name again. The addresses are read
     * from the stored note rather than from whatever copy the caller has, since the working copy may
     * have moved on since it was written and the file belongs to the text that is being removed.
     *
     * The file work is kept off the main thread by the callers, all of which already run on
     * [Dispatchers.IO], and it is never allowed to fail the delete: a picture that cannot be removed
     * is a picture left behind, and the renderer draws the address of a picture it cannot read - the
     * note is what the user asked to be rid of.
     *
     * @return the notes that are left, in the active sort order.
     */
    private fun deleteNoteRow(id: String): List<Note> {
        val removed = database.byId(id)
        database.delete(id)
        val remaining = database.all()
        if (removed != null) {
            // Whatever the removed note held, less whatever a note that is left still names: two
            // notes can hold one address - duplicating a note copies its text, and the text is all
            // there is - so the picture belongs to the note that is still there.
            NoteImages.delete(
                context = appContext,
                addresses = NoteImages.addresses(removed.content),
                keep = remaining.flatMap { NoteImages.addresses(it.content) },
            )
        }
        return remaining
    }

    suspend fun setLocked(id: String, locked: Boolean) = withContext(Dispatchers.IO) {
        val existing = database.byId(id) ?: return@withContext
        database.upsert(existing.copy(isLocked = locked, updatedAt = System.currentTimeMillis()))
        _notes.value = sortNotes(database.all(), _sort.value)
    }

    /**
     * Pins or unpins [id]. A pinned note is listed ahead of every other note, in any order.
     *
     * Pinning is not an edit of what the note says, so the modified time is deliberately left
     * alone: a note must not jump to the top of "last updated" merely because it was pinned.
     */
    suspend fun setPinned(id: String, pinned: Boolean) = setPinned(listOf(id), pinned)

    /**
     * Pins or unpins every note in [ids] as one change.
     *
     * A selection is pinned by one press, so it is one write and one new list: a write per note
     * would re-read and re-sort the whole list once for each of them, and the home screen shows
     * that as a list that re-flows a dozen times under the finger that pressed the button.
     *
     * Pinning is not an edit of what a note says, so no note's modified time is touched - see
     * [database].
     */
    suspend fun setPinned(ids: Collection<String>, pinned: Boolean) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        database.setPinned(ids, pinned)
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

    /**
     * The order the list is shown in.
     *
     * Pinned notes come first whatever the order is - that is what pinning is for - and the chosen
     * order decides how each of the two groups is sorted inside itself.
     */
    private fun sortNotes(list: List<Note>, sort: NoteSort): List<Note> {
        val order = when (sort) {
            NoteSort.UpdatedDesc -> compareByDescending<Note> { it.updatedAt }
            NoteSort.CreatedDesc -> compareByDescending<Note> { it.createdAt }
            // By title: written titles first in alphabetical order, untitled notes after them, where
            // an empty title cannot pretend to be a sort key.
            NoteSort.TitleAsc -> compareBy<Note> { it.title.isBlank() }
                .thenBy { it.title.trim().lowercase() }
        }
        return list.sortedWith(compareByDescending<Note> { it.pinned }.then(order))
    }
}
