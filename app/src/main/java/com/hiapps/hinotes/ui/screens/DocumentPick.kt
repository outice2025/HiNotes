package com.hiapps.hinotes.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * How a file the user picked gets from the activity back to the backup screen.
 *
 * The pick is started with `Activity.startActivityForResult` and [REQUEST_OPEN_DOCUMENT] rather
 * than through the activity-result registry. The registry hands out request codes from a randomly
 * seeded range it manages itself, and one Android 16 device refused the launch outright with
 * `IllegalArgumentException: Can only use lower 16 bits for requestCode` - the picker never
 * opened, so the import looked broken. A request code this app chooses is one this app can vouch
 * for, and the cost of choosing it is that the result comes back to the activity rather than to
 * the composable that launched it. This object is that handover: the activity puts the URI in,
 * the backup screen takes it out.
 */
internal object PickedDocumentRelay {
    /** The URI of the file the user just picked, or null when there is nothing waiting. */
    val uri: MutableState<Uri?> = mutableStateOf(null)

    /** Called by the activity when a pick returns a file. */
    fun publish(picked: Uri) {
        uri.value = picked
    }

    /** Called by the backup screen once it has taken the value, so it is not handled twice. */
    fun consume() {
        uri.value = null
    }
}

/**
 * The intent that asks the user for an existing file.
 *
 * `ACTION_OPEN_DOCUMENT` with `CATEGORY_OPENABLE` is what the platform asks for, and the category
 * is what guarantees the returned URI can actually be opened through a `ContentResolver` - a
 * picker that returns a URI nobody can read is indistinguishable from a broken import.
 *
 * The type is the wildcard, so every file is selectable. Filtering on a MIME type is tempting for
 * an import that expects a `.json`, but the type a phone reports for an exported file depends on
 * where it has been (a download, a chat app, a file manager of its own), and a picker that greys
 * the file out is what "the import does nothing" looks like from the outside. The contents are
 * validated after reading instead, which is the only check a file name cannot defeat.
 *
 * The read grant is asked for by name: a few providers only hand the URI over readable when the
 * request carries it.
 */
internal fun openDocumentIntent(): Intent =
    Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

/**
 * The request code the picker's result arrives under.
 *
 * Fixed and small: the platform requires a request code that fits in 16 bits, so it is chosen
 * here rather than taken from a counter this app cannot see.
 */
internal const val REQUEST_OPEN_DOCUMENT = 0x1001
