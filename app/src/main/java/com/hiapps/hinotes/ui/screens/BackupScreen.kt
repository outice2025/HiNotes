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
 * Exports are written into the app cache and handed to the system share sheet, which is how the
 * About screen's diagnostics export has always worked - see [DocumentExport] for why that route
 * is used rather than a document picker. Imports read a file the user picks through the Storage
 * Access Framework, so the app needs no storage permission either way.
 *
 * Every import is confirmed first and every outcome is reported in a snackbar; a failed operation
 * reports the reason instead of taking the process down.
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
     * The file the user picked, waiting for its overwrite to be confirmed.
     *
     * Choosing the file comes first and the question comes second: the user sees the system picker
     * the moment the row is tapped, and only then is asked whether the file they are looking at
     * should replace everything.
     */
    var pickedFile by remember { mutableStateOf<Uri?>(null) }

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
                context.getString(
                    R.string.backup_export_settings_done,
                    snapshot.nonDefaultCount(),
                )
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

    /**
     * The picker contracts are remembered, not rebuilt per composition.
     *
     * `rememberLauncherForActivityResult` keys its registration on the contract instance, so a
     * contract constructed inline - which is a fresh object on every recomposition - re-registers
     * the launcher every time the screen recomposes. When that happens between the tap and the
     * file being chosen, the result of the pick can be dropped, which looks exactly like an import
     * button that does nothing. Remembering the contract keeps one registration for the screen's
     * whole life.
     */
    val settingsPicker = remember { OpenDocumentContract() }
    val notesPicker = remember { OpenDocumentContract(NOTES_MIME_TYPES) }

    val importSettingsLauncher = rememberLauncherForActivityResult(settingsPicker) { uri ->
        if (uri != null) pickedFile = uri
    }

    val importNotesLauncher = rememberLauncherForActivityResult(notesPicker) { uri ->
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
                onClick = { exportSettings() },
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
                onClick = { pendingImport = ImportKind.Notes },
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
                    // Tapping the row opens the system picker straight away; the question above
                    // is asked once a file is actually in hand.
                    ImportKind.Settings -> launchPicker { importSettingsLauncher.launch(Unit) }
                    ImportKind.Notes -> launchPicker { importNotesLauncher.launch(Unit) }
                }
            },
            onDismiss = { pendingImport = null },
        )
    }

    val chosen = pickedFile
    if (chosen != null) {
        ConfirmDialog(
            title = stringResource(R.string.backup_import_confirm_title),
            message = stringResource(R.string.backup_import_confirm_message),
            confirmLabel = stringResource(R.string.backup_import_confirm),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                pickedFile = null
                runBackup {
                    when (val result = importSettings(context, chosen, viewModel)) {
                        is ImportResult.Success ->
                            context.getString(R.string.backup_import_settings_done, result.count)
                        is ImportResult.Failure ->
                            context.getString(R.string.backup_failed, result.message)
                    }
                }
            },
            onDismiss = { pickedFile = null },
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

/** MIME types offered when importing notes: a JSON backup, or a zip of Markdown files. */
private val NOTES_MIME_TYPES = arrayOf(
    "application/zip",
    "application/json",
    "text/plain",
    "application/octet-stream",
)

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
