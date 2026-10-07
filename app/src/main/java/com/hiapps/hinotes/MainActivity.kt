package com.hiapps.hinotes

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiapps.hinotes.data.CrashLogger
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.AppViewModelFactory
import com.hiapps.hinotes.ui.HiNotesApp
import com.hiapps.hinotes.ui.LockGate
import com.hiapps.hinotes.ui.screens.PickedDocumentRelay
import com.hiapps.hinotes.ui.screens.REQUEST_OPEN_DOCUMENT
import com.hiapps.hinotes.ui.theme.HiNotesTheme

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
        // Both bars are transparent, and the navigation bar is asked for by name: the edge-to-edge
        // helper's default style paints a translucent scrim behind the navigation buttons on API
        // 28 and below, and a scrim is exactly the band this removes. Behind the bar the app's own
        // surface shows through, painted to the bottom of the window.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
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
                accent = settings.accentPalette,
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

    /**
     * Receives the file the backup screen's picker returned.
     *
     * The pick is started with `startActivityForResult` rather than through the activity-result
     * registry - see [PickedDocumentRelay] for why - so its result arrives here. Only
     * [REQUEST_OPEN_DOCUMENT] is ours, and a cancelled picker reports `RESULT_CANCELED` rather
     * than a URI, so both are checked before anything is published.
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_DOCUMENT) return
        if (resultCode != Activity.RESULT_OK) return
        data?.data?.let { PickedDocumentRelay.publish(it) }
    }
}
