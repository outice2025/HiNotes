package com.hiapps.hinotes.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import java.io.File
import java.io.FileInputStream

private const val TAG = "HiNotesBackup"

/** Which payload a pending import will replace, and how that import reports success. */
private enum class ImportKind(val doneMessage: Int) {
    Settings(R.string.backup_import_settings_done),
    Notes(R.string.backup_import_notes_done),
}

/** A file the picker returned, with the import it was picked for and how it is named on screen. */
private data class PickedDocument(val uri: Uri, val kind: ImportKind, val name: String)

/**
 * Backup settings: export and import both settings and notes, plus restoring defaults.
 *
 * Exports are written into the app cache and handed to the system share sheet, which is how the
 * About screen's diagnostics export has always worked - see [DocumentExport] for why that route
 * is used rather than a document picker. Imports read a file the user picks through the Storage
 * Access Framework, so the app needs no storage permission either way.
 *
 * Picking comes first and the question comes second: the row opens the system picker, and only a
 * file that actually came back is asked about - by name. A question asked before the picker had
 * even opened talked about "the selected file" when nothing had been selected.
 *
 * Every outcome is reported in a snackbar; a failed operation reports the reason instead of taking
 * the process down.
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

    /** The file the picker returned, waiting for its overwrite to be confirmed. */
    var pickedFile by remember { mutableStateOf<PickedDocument?>(null) }

    /**
     * Turns a returned file into a question about that file.
     *
     * The relay is consumed first so the same pick cannot be handled twice, and the file's own
     * name goes into the question: a confirmation that cannot say what it is about to replace the
     * data with is a confirmation nobody can answer.
     */
    val handlePickedDocument: (Uri) -> Unit = { uri ->
        PickedDocumentRelay.consume()
        pickedFile = PickedDocument(
            uri = uri,
            kind = pendingImport ?: ImportKind.Settings,
            name = displayName(context, uri),
        )
        pendingImport = null
    }

    // The picker's result arrives at the activity, so it is watched for here and turned into the
    // question the moment it lands. The key never changes: the handler clears the value it read.
    val relayedPick by PickedDocumentRelay.uri
    LaunchedEffect(relayedPick) {
        val picked = relayedPick ?: return@LaunchedEffect
        handlePickedDocument(picked)
    }

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
                context.getString(R.string.backup_failed, describe(t))
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

    // ------------------------------------------------------------------ importers

    // ------------------------------------------------------------------- pickers

    /**
     * Opens the system file picker for one of the two imports, reporting rather than crashing
     * when no app on the device can handle the request.
     *
     * The pick is started on the activity directly rather than through
     * `rememberLauncherForActivityResult`: see [PickedDocumentRelay] for the Android 16 device that
     * refused the registry's request code. The result comes back through that relay, which
     * [handlePickedDocument] picks up.
     */
    fun pickDocument(kind: ImportKind) {
        val activity = context.findActivity()
        if (activity == null) {
            // Not hosted by an activity, so there is nothing to start a picker from.
            scope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.backup_failed, context.getString(R.string.backup_no_app)),
                        duration = SnackbarDuration.Short,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (inner: Throwable) {
                    Log.e(TAG, "could not show snackbar", inner)
                }
            }
            return
        }
        try {
            pendingImport = kind
            activity.startActivityForResult(openDocumentIntent(), REQUEST_OPEN_DOCUMENT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            // A device with nothing registered for ACTION_OPEN_DOCUMENT throws here, on the tap.
            Log.e(TAG, "could not open the system file picker", t)
            pendingImport = null
            scope.launch {
                try {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.backup_failed, describe(t)),
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

    /**
     * Turns a returned file into a question about that file.
     *
     * The relay is consumed first so the same pick cannot be handled twice, and the file's own
     * name goes into the question: a confirmation that cannot say what it is about to replace the
     * data with is a confirmation nobody can answer.
     */
    fun handlePickedDocument(uri: Uri) {
        PickedDocumentRelay.consume()
        pickedFile = PickedDocument(
            uri = uri,
            kind = pendingImport ?: ImportKind.Settings,
            name = displayName(context, uri),
        )
        pendingImport = null
    }

    // ---------------------------------------------------------------- exporters

    /**
     * Settings leave as a JSON file, notes leave as a zip of Markdown documents, and both are
     * written into the app cache and handed to the share sheet - the same route the About
     * screen's diagnostics export takes. A document picker would be the tidier API, but on a
     * device whose provider does not answer `ACTION_CREATE_DOCUMENT` it fails without ever
     * telling anyone, which is exactly the "the button does nothing" the export used to be.
     */
    fun exportSettings() {
        runBackup {
            val snapshot = viewModel.settings.value
            val payload = Backup.encodeSettings(snapshot).toByteArray(Charsets.UTF_8)
            // The write stays off the main thread even though these files are small.
            val opened = withContext(Dispatchers.IO) {
                DocumentExport.share(
                    context = context,
                    fileName = Backup.fileName("settings"),
                    mimeType = "application/json",
                    chooserTitle = context.getString(R.string.backup_export_settings),
                    bytes = payload,
                )
            }
            if (opened) {
                // "Exported settings" rather than "exported N settings": the file holds every
                // setting, and counting only the ones that differ from the defaults made the
                // number look wrong next to what the user remembers having changed.
                context.getString(R.string.backup_export_settings_done)
            } else {
                context.getString(
                    R.string.backup_failed,
                    context.getString(R.string.backup_no_app),
                )
            }
        }
    }

    /** Notes leave as one Markdown document per note, zipped. */
    fun exportNotes() {
        runBackup {
            val notes = viewModel.allNotes()
            if (notes.isEmpty()) {
                context.getString(R.string.backup_empty)
            } else {
                // Zipping and writing stay off the main thread: a long note list is still small,
                // but there is no reason to build it in the frame the user is looking at.
                val opened = withContext(Dispatchers.IO) {
                    DocumentExport.share(
                        context = context,
                        fileName = Backup.fileName("notes", "zip"),
                        mimeType = "application/zip",
                        chooserTitle = context.getString(R.string.backup_export_notes),
                        bytes = NotesArchive.encode(notes),
                    )
                }
                if (opened) {
                    context.getString(R.string.backup_export_notes_done, notes.size)
                } else {
                    context.getString(
                        R.string.backup_failed,
                        context.getString(R.string.backup_no_app),
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ importers

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
                onClick = { exportSettings() },
                isFirst = true,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Upload,
                headline = stringResource(R.string.backup_import_settings),
                supporting = stringResource(R.string.backup_import_settings_support),
                onClick = { pickDocument(ImportKind.Settings) },
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
                onClick = { exportNotes() },
                isFirst = true,
                isLast = false,
            )
            // No trailing chevron: only the rows that open another screen or dialog carry one,
            // and this one starts a file picker directly.
            SettingsRow(
                icon = Symbols.Upload,
                headline = stringResource(R.string.backup_import_notes),
                supporting = stringResource(R.string.backup_import_notes_support),
                onClick = { pickDocument(ImportKind.Notes) },
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

    val chosen = pickedFile
    if (chosen != null) {
        ConfirmDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            message = stringResource(R.string.backup_import_confirm_message, chosen.name),
            confirmLabel = stringResource(R.string.backup_import_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                pickedFile = null
                runBackup {
                    val result = when (chosen.kind) {
                        ImportKind.Settings -> importSettings(context, chosen.uri, viewModel)
                        ImportKind.Notes -> importNotes(context, chosen.uri, viewModel)
                    }
                    when (result) {
                        is ImportResult.Success -> context.getString(chosen.kind.doneMessage, result.count)
                        is ImportResult.Failure -> context.getString(R.string.backup_failed, result.message)
                    }
                }
            },
            onDismiss = { pickedFile = null },
        )
    }

    if (pendingRestore) {
        ConfirmDialog(
            title = stringResource(R.string.backup_restore_defaults),
            // Restoring defaults replaces the same data an import does, so it asks the same
            // question - but there is no file involved, hence the file-less wording.
            message = stringResource(R.string.backup_restore_confirm_message),
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

/**
 * The activity this composition is hosted by, or null when there is none.
 *
 * `LocalContext` is a [ContextWrapper] around the activity, not the activity itself, so the
 * wrappers are unwrapped one at a time and anything else - a service, a test host, a preview - is
 * reported as "no activity" instead of being cast and crashing.
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * The name a picked document is shown under.
 *
 * A `content://` URI carries no file name of its own, so the provider that issued it is asked;
 * when even that has nothing to say, the last path segment is the best remaining guess, and
 * failing that the whole URI - a confirmation that cannot name the file is worse than one that
 * names it clumsily.
 */
private fun displayName(context: Context, uri: Uri): String {
    val fromProvider = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    } catch (t: Throwable) {
        // A provider that refuses the query is not a reason to refuse the import.
        Log.w(TAG, "could not read the display name of $uri", t)
        null
    }
    return fromProvider?.takeIf { it.isNotBlank() }
        ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        ?: uri.toString()
}

/** MIME types offered when importing notes: a JSON backup, or a zip of Markdown files. */

/** What reading a picked document produced: its bytes, or the reason there are none. */
private sealed interface ReadResult {
    data class Ok(val bytes: ByteArray) : ReadResult
    data class Failed(val reason: String) : ReadResult
}

/** One line of diagnostics: the failure's type, and as much of its message as is readable. */
private fun describe(t: Throwable): String =
    t.javaClass.simpleName + (t.message?.take(100)?.let { ": $it" } ?: "")

/**
 * Reads the document the user picked.
 *
 * Three strategies, because the URI a picker hands back is not ours to choose: a stream through
 * the content resolver, a file descriptor, and - for the `file://` URIs some file managers still
 * return - the path itself. Whichever one works, works; and when none does, the reason is handed
 * back instead of being swallowed, because a file that cannot be opened and an import button that
 * does nothing look identical from the outside. Every attempt is logged with its URI as well.
 */
private suspend fun readDocument(context: Context, uri: Uri): ReadResult =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val attempts: List<Pair<String, () -> ByteArray?>> = listOf(
            "openInputStream" to { resolver.openInputStream(uri)?.use { it.readBytes() } },
            "openFileDescriptor" to {
                resolver.openFileDescriptor(uri, "r")?.use { fd ->
                    FileInputStream(fd.fileDescriptor).use { it.readBytes() }
                }
            },
            "path" to { uri.path?.let { path -> File(path).takeIf { it.isFile }?.readBytes() } },
        )

        val reasons = ArrayList<String>()
        attempts.forEach { (label, open) ->
            val bytes = try {
                open()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                Log.e(TAG, "$label could not read $uri", t)
                reasons += describe(t)
                null
            }
            if (bytes != null) return@withContext ReadResult.Ok(bytes)
        }
        val detail = reasons.distinct().joinToString("; ")
        ReadResult.Failed(
            detail.ifEmpty { context.getString(R.string.backup_import_unreadable_unknown) },
        )
    }

private suspend fun importSettings(
    context: Context,
    uri: Uri,
    viewModel: AppViewModel,
): ImportResult {
    val bytes = when (val read = readDocument(context, uri)) {
        is ReadResult.Ok -> read.bytes
        is ReadResult.Failed ->
            return ImportResult.Failure(context.getString(R.string.backup_import_unreadable, read.reason))
    }
    val decoded = Backup.decodeSettings(
        raw = bytes.toString(Charsets.UTF_8),
        fallback = viewModel.settings.value,
        reader = viewModel.settingsRepository,
    ) ?: return ImportResult.Failure(
        context.getString(R.string.backup_import_invalid_settings),
    )
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
    val bytes = when (val read = readDocument(context, uri)) {
        is ReadResult.Ok -> read.bytes
        is ReadResult.Failed ->
            return ImportResult.Failure(context.getString(R.string.backup_import_unreadable, read.reason))
    }
    val notes: List<Note> = if (NotesArchive.looksLikeArchive(bytes)) {
        NotesArchive.decode(bytes)
            ?: return ImportResult.Failure(context.getString(R.string.backup_import_invalid_notes))
    } else {
        Backup.decodeNotes(bytes.toString(Charsets.UTF_8))
            ?: return ImportResult.Failure(context.getString(R.string.backup_import_invalid_notes))
    }
    if (notes.isEmpty()) return ImportResult.Failure(context.getString(R.string.backup_empty))
    val written = viewModel.replaceAllNotes(notes)
    return ImportResult.Success(written)
}
