package com.hiapps.hinotes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.Biometrics
import com.hiapps.hinotes.ui.components.AppLogoPlaceholder

/**
 * The app lock screen, shown instead of the app whenever a password is configured.
 *
 * Offers the password field and, when the user has switched face unlock on and the device
 * supports it, a biometric prompt that runs immediately on appearance.
 */
@Composable
fun LockGate(viewModel: AppViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    val wrongError = stringResource(R.string.unlock_password_error_wrong)
    val activity = context as? FragmentActivity

    val biometricOffered: Boolean = remember(settings.biometricFace) {
        settings.biometricFace && Biometrics.isAvailable(context)
    }

    fun tryBiometric() {
        val host = activity ?: return
        Biometrics.authenticate(
            activity = host,
            title = context.getString(R.string.unlock_enter_title),
            subtitle = context.getString(R.string.unlock_enter_support),
            negativeLabel = context.getString(R.string.common_cancel),
            onSuccess = { viewModel.unlock() },
            onFailure = { /* Fall through to the password field. */ },
        )
    }

    // Offer the prompt as soon as the gate appears, when the user asked for it.
    LaunchedEffect(biometricOffered) {
        if (biometricOffered) tryBiometric()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    AppLogoPlaceholder(size = 72.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.unlock_enter_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.unlock_enter_support),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            error = false
                        },
                        label = { Text(stringResource(R.string.unlock_password)) },
                        singleLine = true,
                        isError = error,
                        supportingText = if (error) {
                            { Text(wrongError) }
                        } else {
                            null
                        },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                if (viewModel.lockStore.verify(password)) {
                                    viewModel.unlock()
                                } else {
                                    error = true
                                    password = ""
                                }
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (viewModel.lockStore.verify(password)) {
                                viewModel.unlock()
                            } else {
                                error = true
                                password = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.unlock_action))
                    }

                    if (biometricOffered) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { tryBiometric() }) {
                            Text(
                                text = stringResource(R.string.unlock_use_biometric),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
