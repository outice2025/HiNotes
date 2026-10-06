package com.hiapps.hinotes.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * The Storage Access Framework contract the backup screen needs for reading a file back.
 *
 * Written out rather than taken from `ActivityResultContracts` so the intent carries
 * `CATEGORY_OPENABLE`, which is what the platform asks for and what guarantees the returned URI
 * can actually be opened through a `ContentResolver`. A picker that returns a URI nobody can read
 * is indistinguishable from a broken import, so the extra category is not left out.
 *
 * A cancelled picker returns null instead of throwing; the caller reports anything else rather
 * than dropping it.
 *
 * Exports deliberately do not come through here: see [DocumentExport] for why they are handed to
 * the share sheet instead of to a create-document picker.
 */

/**
 * `ACTION_OPEN_DOCUMENT`: ask the user for an existing file.
 *
 * The intent is always typed as the wildcard type, so every file is selectable. [mimeTypes] only
 * narrows the list when the caller genuinely knows what the file will be; the settings importer
 * passes null, because the MIME type a phone reports for an exported `.json` depends on where the
 * file came from (a download, a chat app, a file manager of its own) and a picker that filters on
 * it greys the file out - which is what "the import does nothing" looks like from the outside.
 * The content is validated after reading instead, which is the only check a file name cannot
 * defeat.
 */
internal class OpenDocumentContract(private val mimeTypes: Array<String>? = null) :
    ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .apply { if (mimeTypes != null) putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes) }

    override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Uri?>? = null

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
