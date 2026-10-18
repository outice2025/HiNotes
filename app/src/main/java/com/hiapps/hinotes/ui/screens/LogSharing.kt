package com.hiapps.hinotes.ui.screens

import android.content.Context
import com.hiapps.hinotes.data.Backup

/**
 * Writes a diagnostics report and hands it to the system share sheet.
 *
 * The mechanism itself lives in [DocumentExport], which the settings and notes exports now use as
 * well: one implementation of "put a generated file in front of the user" means one place to get
 * right, and one place that can be fixed when a device disagrees with it.
 */
internal object LogSharing {

    /** Returns true when a share sheet could be opened. */
    fun share(context: Context, report: String): Boolean = DocumentExport.share(
        context = context,
        // A text file, because that is what the report is: it is meant to be read, and a phone
        // opens a .txt in whatever app can show it, where a .json only opens in an editor.
        fileName = Backup.fileName("logs", "txt"),
        mimeType = "text/plain",
        chooserTitle = null,
        bytes = report.toByteArray(Charsets.UTF_8),
    )
}
