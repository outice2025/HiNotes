package com.hiapps.hinotes.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.BuildConfig
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Backup
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.AlertMessage
import com.hiapps.hinotes.ui.components.AppLogoPlaceholder
import com.hiapps.hinotes.ui.components.SettingsRow
import com.hiapps.hinotes.ui.components.SettingsScaffold
import com.hiapps.hinotes.ui.components.SettingsSection
import com.hiapps.hinotes.ui.components.SettingsSectionGap
import com.hiapps.hinotes.ui.icons.Symbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Project home page, used by the repository row. */
private const val REPOSITORY_URL = "https://github.com/outice2025/HiNotes"

/** Releases page, opened by the "Check for updates" row. */
private const val RELEASES_URL = "$REPOSITORY_URL/releases"

/**
 * About: the app mark, name, tagline, version, and the updates / repository / logs actions.
 *
 * All three rows do real work: the updates row opens the project's releases page in a browser,
 * the repository row opens the project page, and the logs row writes a diagnostics file and hands
 * it to the share sheet. None of them are placeholders.
 *
 * "Check for updates" is a link rather than a version comparison against a release feed, which
 * means the app now makes no network request of its own at all and needs no INTERNET permission.
 */
@Composable
fun AboutScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var notice by remember { mutableStateOf<String?>(null) }

    suspend fun message(text: String) {
        snackbarHostState.showSnackbar(text)
    }

    /** Opens [url] in whatever app handles links, reporting it when nothing does. */
    fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { scope.launch { message(context.getString(R.string.about_no_browser)) } }
    }

    SettingsScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        // Mark, name and tagline, centred, treated as one block: the vertical padding is the
        // block's own margin, so the whole lock-up sits clear of the title above it and the
        // first settings group below.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AppLogoPlaceholder(size = 95.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.about_tagline),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.Info,
                headline = stringResource(R.string.about_version),
                supporting = stringResource(R.string.about_version_support, BuildConfig.VERSION_NAME),
                onClick = null,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.Update,
                headline = stringResource(R.string.about_check_updates),
                supporting = stringResource(R.string.about_check_updates_support),
                onClick = { openLink(RELEASES_URL) },
                trailing = { OpenInNewBadge() },
                isFirst = true,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Box,
                headline = stringResource(R.string.about_repository),
                supporting = stringResource(R.string.about_repository_support),
                onClick = { openLink(REPOSITORY_URL) },
                trailing = { OpenInNewBadge() },
                isFirst = false,
                isLast = false,
            )
            SettingsRow(
                icon = Symbols.Bookmarks,
                headline = stringResource(R.string.about_export_logs),
                supporting = stringResource(R.string.about_export_logs_support),
                onClick = {
                    scope.launch {
                        val report = withContext(Dispatchers.IO) {
                            Backup.buildLogReport(
                                context = context,
                                settings = viewModel.settings.value,
                                noteCount = viewModel.noteCount(),
                            )
                        }
                        val shared = LogSharing.share(context, report)
                        message(
                            context.getString(
                                if (shared) R.string.about_logs_exported else R.string.about_no_browser,
                            ),
                        )
                    }
                },
                trailing = { Chevron() },
                isFirst = false,
                isLast = true,
            )
        }

        SettingsSectionGap()

        SettingsSection {
            SettingsRow(
                icon = Symbols.Info,
                headline = stringResource(R.string.about_licenses),
                supporting = "Apache-2.0",
                onClick = { notice = LICENSE_NOTICE },
            )
        }
    }

    if (notice != null) {
        AlertMessage(
            title = stringResource(R.string.about_title),
            message = notice!!,
            confirmLabel = stringResource(R.string.common_ok),
            onDismiss = { notice = null },
        )
    }
}

/** The `link_2`-style affordance marking a row that leaves the app. */
@Composable
private fun OpenInNewBadge() {
    com.hiapps.hinotes.ui.icons.SymbolIcon(
        codepoint = Symbols.OpenInNew,
        contentDescription = null,
        size = 24.dp,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val LICENSE_NOTICE =
    "HiNotes is free software under the Apache License 2.0.\n\n" +
        "Its icons are vector artwork derived from Material Symbols Rounded (Apache License " +
        "2.0), and it builds on Jetpack Compose, AndroidX and Kotlin (Apache License 2.0). " +
        "Text is drawn with the device's own font, so no typeface is redistributed.\n\n" +
        "No analytics, and no network access of its own: the app declares no INTERNET " +
        "permission, and every link here is opened by another app."
