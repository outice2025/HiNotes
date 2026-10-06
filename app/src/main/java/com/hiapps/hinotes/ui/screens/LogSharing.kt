package com.hiapps.hinotes.ui.screens

import android.content.Context

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
        fileName = "hinotes-logs-${System.currentTimeMillis()}.json",
        mimeType = "application/json",
        chooserTitle = null,
        bytes = report.toByteArray(Charsets.UTF_8),
    )
}
