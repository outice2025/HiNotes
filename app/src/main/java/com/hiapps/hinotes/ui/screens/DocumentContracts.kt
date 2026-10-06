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

/** `ACTION_OPEN_DOCUMENT`: ask the user for an existing file. */
internal class OpenDocumentContract(private val mimeTypes: Array<String>) :
    ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

    override fun getSynchronousResult(context: Context, input: Unit): SynchronousResult<Uri?>? = null

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}
