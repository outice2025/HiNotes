package com.hinotes.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hinotes.app.data.CrashLogger
import com.hinotes.app.ui.AppViewModel
import com.hinotes.app.ui.AppViewModelFactory
import com.hinotes.app.ui.HiNotesApp
import com.hinotes.app.ui.LockGate
import com.hinotes.app.ui.theme.HiNotesTheme

/**
 * Single activity host.
 *
 * Extends [FragmentActivity] because `androidx.biometric`'s prompt requires one; it is still a
 * `ComponentActivity`, so Compose, `enableEdgeToEdge` and the ViewModel store all behave the
 * same as before.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the last-resort crash recorder before anything else can throw, so a crash
        // leaves a report the user can export from the About screen.
        CrashLogger.install(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(context))
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()

            val systemDark = isSystemInDarkTheme()
            val darkTheme = settings.darkMode.isDark(systemDark)

            HiNotesTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                oledDark = settings.oledDark,
                fontScale = settings.appFontScale,
                fontVariationWeight = settings.appFontWeight,
                contentScale = settings.noteFontScale,
                contentWeight = settings.noteFontWeight,
            ) {
                if (locked && viewModel.lockStore.isConfigured) {
                    LockGate(viewModel = viewModel)
                } else {
                    HiNotesApp(viewModel = viewModel)
                }
            }
        }
    }
}
