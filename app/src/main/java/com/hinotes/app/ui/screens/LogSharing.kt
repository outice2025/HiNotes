package com.hinotes.app.ui.screens

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Writes a diagnostics report to the app cache and hands it to the system share sheet.
 *
 * The file is exposed through a [FileProvider] scoped to the cache directory (see
 * `res/xml/file_paths.xml`), so it can be attached to a mail or chat without granting anyone
 * access to the app's private storage.
 */
internal object LogSharing {

    private const val EXPORTS_DIR = "exports"

    /** Returns true when a share sheet could be opened. */
    fun share(context: Context, report: String): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, EXPORTS_DIR).apply { mkdirs() }
            // Keep the cache tidy: only the newest reports are of any use.
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }

            val file = File(dir, "hinotes-logs-${System.currentTimeMillis()}.json")
            file.writeText(report)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "HiNotes diagnostics")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(
                Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
    }
}
