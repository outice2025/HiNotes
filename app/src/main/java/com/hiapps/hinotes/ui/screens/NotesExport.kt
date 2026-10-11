package com.hiapps.hinotes.ui.screens

import android.content.Context
import android.util.Log
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Backup
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.NotesArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "HiNotesExport"

/**
 * Hands [notes] to the system share sheet as one zip of documents, one per note.
 *
 * This is the route the backup screen's "Export notes" takes, and the one the home screen's
 * selection export takes with the notes the user picked: the same file name, the same MIME type
 * and the same share sheet, so a note leaves the app the same way whichever list it came from.
 *
 * @param markdown whether the notes are written as Markdown, which is what decides whether each
 *   document leaves as a `.md` file or a `.txt` one.
 *
 * Nothing here throws for an expected failure - a zip that cannot be written, or a device with no
 * app that will take the file, comes back as the text to show.
 */
internal suspend fun shareNotes(
    context: Context,
    notes: List<Note>,
    markdown: Boolean,
): String = try {
    withContext(Dispatchers.IO) {
        if (notes.isEmpty()) {
            context.getString(R.string.backup_empty)
        } else {
            // Zipping and writing stay off the main thread: a long note list is still small, but
            // there is no reason to build it in the frame the user is looking at.
            val opened = DocumentExport.share(
                context = context,
                fileName = Backup.fileName("notes", "zip"),
                mimeType = "application/zip",
                chooserTitle = context.getString(R.string.backup_export_notes),
                bytes = NotesArchive.encode(notes, markdown),
            )
            if (opened) {
                context.getString(R.string.backup_export_notes_done, notes.size)
            } else {
                context.getString(R.string.backup_failed, context.getString(R.string.backup_no_app))
            }
        }
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (t: Throwable) {
    Log.e(TAG, "could not export notes", t)
    context.getString(R.string.backup_failed, describe(t))
}

/** One line of diagnostics: the failure's type, and as much of its message as is readable. */
internal fun describe(t: Throwable): String =
    t.javaClass.simpleName + (t.message?.take(100)?.let { ": $it" } ?: "")
