package com.hiapps.hinotes.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/**
 * The two Storage Access Framework contracts the backup screen needs.
 *
 * These are written out rather than taken from `ActivityResultContracts` so the intents carry
 * `CATEGORY_OPENABLE`, which is what the platform asks for and what guarantees the returned URI
 * can actually be opened through a `ContentResolver`. A picker that returns a URI nobody can
 * write to is indistinguishable from a broken export, so the extra category is not left out.
 *
 * Both are also explicit about failure: a cancelled picker returns null instead of throwing, and
 * the caller reports anything else instead of dropping it.
 */

/** `ACTION_CREATE_DOCUMENT`: ask the user where to save a new file. */
internal class CreateDocumentContract(private val mimeType: String) :
    ActivityResultContract<String, Uri?>() {

    override fun createIntent(context: Context, input: String): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_TITLE, input)

    override fun getSynchronousResult(context: Context, input: String): SynchronousResult<Uri?>? =
        null

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

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
