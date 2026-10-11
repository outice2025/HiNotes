package com.hiapps.hinotes

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
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
import com.hiapps.hinotes.widget.HiNotesWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Single activity host.
 *
 * Extends [FragmentActivity] because `androidx.biometric`'s prompt requires one; it is still a
 * `ComponentActivity`, so Compose, `enableEdgeToEdge` and the ViewModel store all behave the
 * same as before.
 */
class MainActivity : FragmentActivity() {

    /**
     * The note a widget asked to open, waiting for the navigation host to show it.
     *
     * A tap on a widget arrives as an intent for this activity: a cold start when the app is not
     * running ([onCreate]), and the activity already on screen when it is ([onNewIntent], which the
     * widget's flags ask for). Both publish the note here, and the host consumes it once - so
     * opening a note from the home screen and then rotating the phone does not open it twice.
     */
    private val pendingNoteId = MutableStateFlow<String?>(null)

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
        // Only a fresh launch opens a note. The extra stays on the activity's intent for as long
        // as the activity lives, so a rotation - which runs this again - must not open it twice.
        if (savedInstanceState == null) {
            pendingNoteId.value = intent?.getStringExtra(EXTRA_NOTE_ID)
        }
        // Anything that changed while the app was not running - a dark-mode switch, a setting, a
        // note edited from a widget - is drawn again as soon as the app is opened.
        HiNotesWidgetProvider.refreshAll(this)
        setContent {
            val context = LocalContext.current
            val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(context))
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()
            val noteToOpen by pendingNoteId.collectAsStateWithLifecycle()

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
                    // The lock gate comes first: a note opened from a widget waits behind the
                    // password like every other way into the app.
                    LockGate(viewModel = viewModel)
                } else {
                    HiNotesApp(
                        viewModel = viewModel,
                        startNoteId = noteToOpen,
                        onStartNoteShown = {
                            // The request is spent: dropped from the intent as well as from the
                            // flow, so no later recreation can replay it.
                            intent?.removeExtra(EXTRA_NOTE_ID)
                            pendingNoteId.value = null
                        },
                    )
                }
            }
        }
    }

    /** A widget was tapped while the app was already on screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNoteId.value = intent.getStringExtra(EXTRA_NOTE_ID)
    }

    /**
     * The device's configuration changed, which with the app set to follow the system is what a
     * dark-mode switch is.
     *
     * The widgets are painted from that setting, so they are redrawn here whenever this activity is
     * alive - which is the common case, because the app is usually still in the background when the
     * theme flips. With the app gone, the card is redrawn the next time the app is opened, a note is
     * saved, or the host measures it again: `hi_notes_widget_info.xml` asks the platform for no
     * periodic updates, because the app does no work in the background.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        HiNotesWidgetProvider.refreshAll(this)
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

    companion object {
        /** Extra a tapped widget carries: the id of the note it shows. */
        const val EXTRA_NOTE_ID = "com.hiapps.hinotes.extra.NOTE_ID"
    }
}
