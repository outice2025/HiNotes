package com.hiapps.hinotes.ui.screens

import android.content.Context
import android.util.Log
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Backup
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.NoteImages
import com.hiapps.hinotes.data.NotesArchive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "HiNotesExport"

/**
 * Hands [notes] to the system share sheet as one zip of documents, one per note, together with the
 * pictures those notes refer to.
 *
 * This is the route the backup screen's "Export notes" takes, and the one the home screen's
 * selection export takes with the notes the user picked: the same file name, the same MIME type
 * and the same share sheet, so a note leaves the app the same way whichever list it came from - and
 * a picture leaves with it, because a note that arrives anywhere without the pictures it names is a
 * note full of addresses. The pictures come from this app's own copies, under the names their
 * addresses hold, which is what lets the import put a note back exactly as it was.
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
            // Zipping and writing stay off the main thread: the notes alone are small, but the
            // pictures that now ride with them are not, and there is no reason to build either in
            // the frame the user is looking at.
            val opened = DocumentExport.share(
                context = context,
                fileName = Backup.fileName("notes", "zip"),
                mimeType = "application/zip",
                chooserTitle = context.getString(R.string.backup_export_notes),
                // A picture is read as the archive asks for it rather than all of them up front, so
                // a backup of a notebook full of photographs spends one picture's worth of memory on
                // pictures at a time. A picture whose file has gone comes back as null and is simply
                // not in the archive.
                bytes = NotesArchive.encode(notes, markdown) { name -> NoteImages.read(context, name) },
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
