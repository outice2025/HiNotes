package com.hinotes.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hinotes.app.R
import com.hinotes.app.data.Note
import java.io.File

/**
 * Shares a note as text.
 *
 * The note is sent as `text/plain` rather than as a file, because a note is prose the user
 * usually wants in a message body; when the note is long, a Markdown attachment is offered
 * instead so nothing is truncated.
 */
internal fun shareNote(context: Context, note: Note?, title: String, untitled: String) {
    val current = note ?: return
    val heading = title.ifBlank { current.displayTitle(untitled) }

    val intent = if (current.content.length > MAX_INLINE_CHARS) {
        val uri = writeShareFile(context, heading, current.content)
        if (uri == null) {
            textShareIntent(heading, current.content)
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, heading)
                putExtra(Intent.EXTRA_TEXT, heading)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    } else {
        textShareIntent(heading, current.content)
    }

    runCatching {
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.editor_share_chooser))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun textShareIntent(title: String, content: String): Intent =
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, if (content.isBlank()) title else "$title\n\n$content")
    }

/** Writes the note to the share cache and returns a content URI, or null if that failed. */
private fun writeShareFile(context: Context, title: String, content: String): android.net.Uri? =
    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }

        val safeName = title
            .replace(Regex("[^\\p{L}\\p{N}\\-_ ]"), "")
            .trim()
            .take(48)
            .ifBlank { "note" }
        val file = File(dir, "$safeName.md")
        file.writeText(content)

        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull()

/** Notes longer than this are attached as a file instead of inlined into the message body. */
private const val MAX_INLINE_CHARS = 2000
