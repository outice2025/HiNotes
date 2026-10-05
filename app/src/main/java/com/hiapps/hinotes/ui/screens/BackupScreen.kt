package com.hiapps.hinotes.ui.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Backup
import com.hiapps.hinotes.data.ImportResult
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.NotesArchive
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.ConfirmDialog
import com.hiapps.hinotes.ui.components.SettingsRow
import com.hiapps.hinotes.ui.components.SettingsScaffold
import com.hiapps.hinotes.ui.components.SettingsSection
import com.hiapps.hinotes.ui.components.SettingsSectionGap
import com.hiapps.hinotes.ui.icons.Symbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

private const val TAG = "HiNotesBackup"

/** Which payload a pending import will replace. */
private enum class ImportKind { Settings, Notes }

/**
 * Backup settings: export and import both settings and notes, plus restoring defaults.
 *
 * Files go through the Storage Access Framework, so the user picks the destination and the app
 * needs no storage permission. Every import is confirmed first and every outcome is reported in
 * a snackbar; a failed operation reports the reason instead of taking the process down.
 */
@Composable
fun BackupScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var pendingImport by remember { mutableStateOf<ImportKind?>(null) }
    var pendingRestore by remember { mutableStateOf(false) }

    /**
     * Runs a backup operation and reports its outcome.
     *
     * The file work and the message are deliberately decoupled: the work runs in its own
     * try/catch so an expected failure (no permission, provider refused, disk full) becomes a
     * message rather than a crash, and showing that message has its own guard so a UI failure
     * can never reach the crash handler either.
     */
    fun runBackup(block: suspend () -> String) {
        scope.launch {
            val text = try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "backup operation failed", t)
                context.getString(R.string.backup_failed, t.javaClass.simpleName)
            }
            try {
                snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "could not show snackbar", t)
            }
        }
    }

    /**
     * Launches a system file picker, reporting rather than crashing if none can handle it.
     *
     * `launch()` runs synchronously on the click, outside any coroutine, so an
     * `ActivityNotFoundException` (or a provider that rejects the request) would otherwise take
     * the process down before the operation's own error handling ever runs.
     */
    fun launchPicker(action: () -> Unit) {
        try {
            action()
        } catch (t: Throwable) {
            Log.e(TAG, "could not open the system file picker", t)
            scope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.backup_failed, t.javaClass.simpleName),
                        duration = SnackbarDuration.Short,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (inner: Throwable) {
                    Log.e(TAG, "could not show snackbar", inner)
                }
            }
        }
    }

    // ---------------------------------------------------------------- exporters

    val exportSettingsLauncher = rememberLauncherForActivityResult(
        CreateDocumentContract("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runBackup {
            val snapshot = viewModel.settings.value
            val payload = Backup.encodeSettings(snapshot)
            if (writeDocument(context, uri, payload.toByteArray(Charsets.UTF_8))) {
                context.getString(
                    R.string.backup_export_settings_done,
                    snapshot.nonDefaultCount(),
                )
            } else {
                context.getString(R.string.backup_failed, "write error")
            }
        }
    }

    /**
     * Notes leave as a zip of Markdown files rather than as one JSON blob: the result is a
     * folder of readable documents that any editor can open, and nothing about the app has to
     * be installed to use them.
     */
    val exportNotesLauncher = rememberLauncherForActivityResult(
        CreateDocumentContract("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runBackup {
            val notes = viewModel.allNotes()
            if (notes.isEmpty()) {
                context.getString(R.string.backup_empty)
            } else {
                val archive = NotesArchive.encode(notes)
                if (writeDocument(context, uri, archive)) {
                    context.getString(R.string.backup_export_notes_done, notes.size)
                } else {
                    context.getString(R.string.backup_failed, "write error")
                }
            }
        }
    }

    // ------------------------------------------------------------------ importers

    val importSettingsLauncher = rememberLauncherForActivityResult(
        OpenDocumentContract(SETTINGS_MIME_TYPES),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runBackup {
            when (val result = importSettings(context, uri, viewModel)) {
                is ImportResult.Success ->
                    context.getString(R.string.backup_import_settings_done, result.count)
                is ImportResult.Failure ->
                    context.getString(R.string.backup_failed, result.message)
            }
        }
    }

    val importNotesLauncher = rememberLauncherForActivityResult(
        OpenDocumentContract(NOTES_MIME_TYPES),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runBackup {
            when (val result = importNotes(context, uri, viewModel)) {
                is ImportResult.Success ->
                    context.getString(R.string.backup_import_notes_done, result.count)
                is ImportResult.Failure ->
                    context.getString(R.string.backup_failed, result.message)
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.backup_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        SettingsSection {
            SettingsRow(
                icon = Symbols.Download,
                headline = stringResource(R.string.backup_export_settings),
                supporting = stringResource(R.string.backup_export_settings_support),
                onClick = {
                    launchPicker { exportSettingsLauncher.launch(Backup.fileName("settings")) }
                },
                isFirst = true,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Upload,
                headline = stringResource(R.string.backup_import_settings),
                supporting = stringResource(R.string.backup_import_settings_support),
                onClick = { pendingImport = ImportKind.Settings },
                isFirst = false,
                isLast = true,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.Download,
                headline = stringResource(R.string.backup_export_notes),
                supporting = stringResource(R.string.backup_export_notes_support),
                onClick = {
                    launchPicker { exportNotesLauncher.launch(Backup.fileName("notes", "zip")) }
                },
                isFirst = true,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Upload,
                headline = stringResource(R.string.backup_import_notes),
                supporting = stringResource(R.string.backup_import_notes_support),
                onClick = { pendingImport = ImportKind.Notes },
                trailing = { Chevron() },
                isFirst = false,
                isLast = true,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.Close,
                headline = stringResource(R.string.backup_restore_defaults),
                supporting = stringResource(R.string.backup_restore_defaults_support),
                onClick = { pendingRestore = true },
            )
        }
    }

    val importing = pendingImport
    if (importing != null) {
        ConfirmDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            message = stringResource(R.string.backup_import_confirm_message),
            confirmLabel = stringResource(R.string.backup_import_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                pendingImport = null
                when (importing) {
                    ImportKind.Settings -> launchPicker { importSettingsLauncher.launch(Unit) }
                    ImportKind.Notes -> launchPicker { importNotesLauncher.launch(Unit) }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }

    if (pendingRestore) {
        ConfirmDialog(
            title = stringResource(R.string.backup_restore_defaults),
            message = stringResource(R.string.backup_import_confirm_message),
            confirmLabel = stringResource(R.string.backup_import_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                pendingRestore = false
                runBackup {
                    viewModel.settingsRepository.restoreDefaults()
                    context.getString(R.string.backup_restore_done)
                }
            },
            onDismiss = { pendingRestore = false },
        )
    }
}

// ------------------------------------------------------------------------ helpers

/** MIME types offered when importing a settings file. */
private val SETTINGS_MIME_TYPES = arrayOf("application/json", "text/plain")

/** MIME types offered when importing notes: a JSON backup, or a zip of Markdown files. */
private val NOTES_MIME_TYPES = arrayOf(
    "application/zip",
    "application/json",
    "text/plain",
    "application/octet-stream",
)

/**
 * Writes [bytes] to the document the user chose, then reads the document back to prove the
 * bytes landed.
 *
 * The read-back matters: a provider can accept a stream, report no error and still keep nothing
 * (the wrong mode, a document that was never committed), and a report of "exported" over a file
 * that is not there is worse than an error. The verification is a length comparison, so it costs
 * one read of a file the app just produced.
 *
 * Returns false - rather than throwing - for the expected failure modes, so the caller can report
 * them; only a genuinely unexpected error reaches the crash handler.
 */
private suspend fun writeDocument(context: Context, uri: Uri, bytes: ByteArray): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openOutputStream(uri, "wt")
                ?: context.contentResolver.openOutputStream(uri)
                ?: return@withContext false
            stream.use { out ->
                out.write(bytes)
                out.flush()
            }
        } catch (e: IOException) {
            Log.e(TAG, "could not write export", e)
            return@withContext false
        } catch (e: SecurityException) {
            Log.e(TAG, "no permission to write export", e)
            return@withContext false
        }

        // Read the document back and compare lengths. A provider that reports no error yet keeps
        // nothing is a real failure mode, and reporting "exported" over an empty file is worse
        // than reporting an error. A provider that simply cannot be read back (some cloud
        // documents) is not treated as a failure: the write itself already succeeded.
        val written = try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().size }
        } catch (e: IOException) {
            Log.w(TAG, "could not verify export", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "no permission to verify export", e)
            null
        }
        if (written != null && written != bytes.size) {
            Log.e(TAG, "export verification failed: wrote ${bytes.size}, read back $written")
            return@withContext false
        }
        true
    }

private suspend fun readDocument(context: Context, uri: Uri): ByteArray? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: IOException) {
            Log.e(TAG, "could not read import", e)
            null
        } catch (e: SecurityException) {
            Log.e(TAG, "no permission to read import", e)
            null
        }
    }

private suspend fun importSettings(
    context: Context,
    uri: Uri,
    viewModel: AppViewModel,
): ImportResult {
    val bytes = readDocument(context, uri)
        ?: return ImportResult.Failure(context.getString(R.string.backup_empty))
    val decoded = Backup.decodeSettings(
        raw = bytes.toString(Charsets.UTF_8),
        fallback = viewModel.settings.value,
        reader = viewModel.settingsRepository,
    ) ?: return ImportResult.Failure("not a settings backup")
    viewModel.settingsRepository.replaceAll(decoded)
    return ImportResult.Success(decoded.nonDefaultCount())
}

/**
 * Reads notes from either shape the app can produce: the older JSON backup, or the zip of
 * Markdown files the export writes now. The zip is recognised by its signature rather than by
 * the file name, so a renamed or re-extensioned file still imports.
 */
private suspend fun importNotes(
    context: Context,
    uri: Uri,
    viewModel: AppViewModel,
): ImportResult {
    val bytes = readDocument(context, uri)
        ?: return ImportResult.Failure(context.getString(R.string.backup_empty))
    val notes: List<Note> = if (NotesArchive.looksLikeArchive(bytes)) {
        NotesArchive.decode(bytes)
            ?: return ImportResult.Failure("not a readable notes archive")
    } else {
        Backup.decodeNotes(bytes.toString(Charsets.UTF_8))
            ?: return ImportResult.Failure("not a notes backup")
    }
    if (notes.isEmpty()) return ImportResult.Failure(context.getString(R.string.backup_empty))
    val written = viewModel.replaceAllNotes(notes)
    return ImportResult.Success(written)
}
