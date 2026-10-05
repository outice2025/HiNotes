package com.hiapps.hinotes.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Biometrics
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.components.AlertMessage
import com.hiapps.hinotes.ui.components.SettingsRow
import com.hiapps.hinotes.ui.components.SettingsScaffold
import com.hiapps.hinotes.ui.components.SettingsSection
import com.hiapps.hinotes.ui.components.SwitchRow
import com.hiapps.hinotes.ui.icons.Symbols
import com.hiapps.hinotes.ui.theme.HiNotesCorners

/** Minimum password length; short enough to be convenient, long enough to be worth hashing. */
private const val MIN_PASSWORD_LENGTH = 4

/**
 * Unlock settings: the app password plus the two biometric switches.
 *
 * A password is a prerequisite for biometrics - there has to be a fallback the user controls -
 * so turning a biometric switch on without a password first explains that instead of enabling a
 * lock the user could be shut out of.
 */
@Composable
fun UnlockScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var passwordDialog by remember { mutableStateOf(false) }
    var removeDialog by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var hasPassword by remember { mutableStateOf(viewModel.lockStore.isConfigured) }

    val fingerprintAvailable = remember { Biometrics.isAvailable(context, Biometrics.Kind.Fingerprint) }
    val faceAvailable = remember { Biometrics.isAvailable(context, Biometrics.Kind.Face) }

    SettingsScaffold(
        title = stringResource(R.string.unlock_title),
        onBack = onBack,
    ) {
        SettingsSection {
            SettingsRow(
                icon = Symbols.Password,
                headline = stringResource(R.string.unlock_password),
                supporting = stringResource(R.string.unlock_password_support),
                onClick = { passwordDialog = true },
                trailing = { Chevron() },
                isFirst = true,
                isLast = false,
            )
            SwitchRow(
                icon = Symbols.Fingerprint,
                headline = stringResource(R.string.unlock_fingerprint),
                supporting = stringResource(R.string.unlock_fingerprint_support),
                checked = settings.biometricFingerprint && fingerprintAvailable,
                enabled = fingerprintAvailable,
                onCheckedChange = { checked ->
                    when {
                        !hasPassword ->
                            notice = context.getString(R.string.unlock_no_password_warning)
                        checked -> requestBiometric(
                            viewModel = viewModel,
                            kind = Biometrics.Kind.Fingerprint,
                            activity = context as? FragmentActivity,
                            onResult = { ok ->
                                if (ok) {
                                    viewModel.updateSettings { setBiometricFingerprint(true) }
                                }
                            },
                        )
                        else -> viewModel.updateSettings { setBiometricFingerprint(false) }
                    }
                },
                isFirst = false,
                isLast = false,
            )
            SwitchRow(
                icon = Symbols.Face,
                headline = stringResource(R.string.unlock_face),
                supporting = stringResource(R.string.unlock_face_support),
                checked = settings.biometricFace && faceAvailable,
                enabled = faceAvailable,
                onCheckedChange = { checked ->
                    when {
                        !hasPassword ->
                            notice = context.getString(R.string.unlock_no_password_warning)
                        checked -> requestBiometric(
                            viewModel = viewModel,
                            kind = Biometrics.Kind.Face,
                            activity = context as? FragmentActivity,
                            onResult = { ok ->
                                if (ok) {
                                    viewModel.updateSettings { setBiometricFace(true) }
                                }
                            },
                        )
                        else -> viewModel.updateSettings { setBiometricFace(false) }
                    }
                },
                isFirst = false,
                isLast = true,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (hasPassword) {
            SettingsSection {
                SettingsRow(
                    icon = Symbols.Close,
                    headline = stringResource(R.string.unlock_remove_password_title),
                    supporting = null,
                    onClick = { removeDialog = true },
                )
            }
        }
    }

    if (passwordDialog) {
        PasswordDialog(
            isChange = hasPassword,
            verify = { viewModel.lockStore.verify(it) },
            onSubmit = { newPassword ->
                viewModel.lockStore.setPassword(newPassword)
                hasPassword = true
                passwordDialog = false
                notice = context.getString(R.string.unlock_password_set)
            },
            onDismiss = { passwordDialog = false },
        )
    }

    if (removeDialog) {
        com.hiapps.hinotes.ui.components.ConfirmDialog(
            title = stringResource(R.string.unlock_remove_password_title),
            message = stringResource(R.string.backup_import_confirm_message),
            confirmLabel = stringResource(R.string.common_delete),
            dismissLabel = stringResource(R.string.common_cancel),
            onConfirm = {
                viewModel.lockStore.clear()
                viewModel.updateSettings {
                    setBiometricFingerprint(false)
                    setBiometricFace(false)
                }
                hasPassword = false
                removeDialog = false
                viewModel.refreshLockState()
                notice = context.getString(R.string.unlock_password_removed)
            },
            onDismiss = { removeDialog = false },
        )
    }

    notice?.let { text ->
        AlertMessage(
            title = stringResource(R.string.unlock_title),
            message = text,
            confirmLabel = stringResource(R.string.common_ok),
            onDismiss = { notice = null },
        )
    }
}

/** Runs the system biometric prompt before a biometric switch is allowed to turn on. */
private fun requestBiometric(
    viewModel: AppViewModel,
    kind: Biometrics.Kind,
    activity: FragmentActivity?,
    onResult: (Boolean) -> Unit,
) {
    if (activity == null) return
    Biometrics.authenticate(
        activity = activity,
        kind = kind,
        title = activity.getString(R.string.unlock_title),
        subtitle = activity.getString(
            if (kind == Biometrics.Kind.Fingerprint) {
                R.string.unlock_fingerprint_support
            } else {
                R.string.unlock_face_support
            },
        ),
        negativeLabel = activity.getString(R.string.common_cancel),
        onSuccess = { onResult(true) },
        onFailure = { onResult(false) },
    )
}

/**
 * Sets, changes, or explains the app password.
 *
 * When a password already exists the current one must be verified first, otherwise anyone
 * holding an unlocked phone could silently replace it.
 */
@Composable
private fun PasswordDialog(
    isChange: Boolean,
    verify: (String) -> Boolean,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val shortError = stringResource(R.string.unlock_password_error_short)
    val mismatchError = stringResource(R.string.unlock_password_error_mismatch)
    val wrongError = stringResource(R.string.unlock_password_error_wrong)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isChange) {
                        R.string.unlock_change_password_title
                    } else {
                        R.string.unlock_set_password_title
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isChange) {
                    OutlinedTextField(
                        value = current,
                        onValueChange = {
                            current = it
                            error = null
                        },
                        label = { Text(stringResource(R.string.unlock_current_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = next,
                    onValueChange = {
                        next = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.unlock_new_password)) },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = {
                        confirm = it
                        error = null
                    },
                    label = { Text(stringResource(R.string.unlock_confirm_password)) },
                    singleLine = true,
                    isError = error != null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    error = when {
                        isChange && !verify(current) -> wrongError
                        next.length < MIN_PASSWORD_LENGTH -> shortError
                        next != confirm -> mismatchError
                        else -> null
                    }
                    if (error == null) onSubmit(next)
                },
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
        shape = RoundedCornerShape(HiNotesCorners.GroupOuter),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
