package com.hiapps.hinotes.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a generated document to the system.
 *
 * Exports are written into the app's cache and offered through the share sheet rather than pushed
 * into a Storage Access Framework `ACTION_CREATE_DOCUMENT` picker. The picker is the tidier API
 * on paper, but on a device whose documents provider does not answer it the export silently goes
 * nowhere, and the user is left with a button that appears to do nothing. The share sheet is the
 * path the diagnostics export has always used and is the one that demonstrably works: it opens
 * the system UI, and "Save to Files" inside it is the same destination picker by another route.
 *
 * Files are pruned to the newest few so the cache cannot grow without bound, and they are exposed
 * through the app's [FileProvider], so nothing outside is granted access to app storage.
 *
 * @return true when the share sheet was opened, false when no app would take the file.
 */
internal object DocumentExport {

    /** Cache directory shared with the diagnostics export. */
    private const val EXPORTS_DIR = "exports"

    /** How many generated files to keep; older ones are the ones already dealt with. */
    private const val KEEP = 6

    fun share(
        context: Context,
        fileName: String,
        mimeType: String,
        chooserTitle: String?,
        bytes: ByteArray,
    ): Boolean = runCatching {
        val dir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEEP)
            ?.forEach { it.delete() }

        val file = File(dir, fileName)
        file.writeBytes(bytes)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)
}
