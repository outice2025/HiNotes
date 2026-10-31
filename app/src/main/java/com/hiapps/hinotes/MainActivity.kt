package com.hiapps.hinotes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hiapps.hinotes.data.AppLanguage
import com.hiapps.hinotes.data.CrashLogger
import com.hiapps.hinotes.ui.AppLocale
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.AppViewModelFactory
import com.hiapps.hinotes.ui.HiNotesApp
import com.hiapps.hinotes.ui.LockGate
import com.hiapps.hinotes.ui.applyScreenCapturePolicy
import com.hiapps.hinotes.ui.screens.PickedDocumentRelay
import com.hiapps.hinotes.ui.screens.PickedImageRelay
import com.hiapps.hinotes.ui.screens.REQUEST_OPEN_DOCUMENT
import com.hiapps.hinotes.ui.screens.REQUEST_PICK_IMAGE
import com.hiapps.hinotes.ui.screens.pickedImage
import com.hiapps.hinotes.ui.theme.HiNotesTheme
import com.hiapps.hinotes.widget.HiNotesWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * The app's own activity: everything except the note picker the launcher opens for a widget.
 *
 * Extends [FragmentActivity] because `androidx.biometric`'s prompt requires one; it is still a
 * `ComponentActivity`, so Compose, `enableEdgeToEdge` and the ViewModel store all behave the
 * same as before.
 */
class MainActivity : FragmentActivity() {

    /**
     * The app's state, owned by the activity rather than by the composition.
     *
     * Built here because one of the settings it holds is a window property: the screen-capture
     * switch has to be applied before the first frame is drawn, and the settings are read while
     * this view model is constructed (see [AppViewModel]).
     */
    private val viewModel: AppViewModel by viewModels { AppViewModelFactory(applicationContext) }

    /**
     * The note a widget asked to open, waiting for the navigation host to show it.
     *
     * A tap on a widget arrives as an intent for this activity: a cold start when the app is not
     * running ([onCreate]), and the activity already on screen when it is ([onNewIntent], which the
     * widget's flags ask for). Both publish the note here, and the host consumes it once - so
     * opening a note from the home screen and then rotating the phone does not open it twice.
     */
    private val pendingNoteId = MutableStateFlow<String?>(null)

    /**
     * The language this activity was built in.
     *
     * A language change writes the stored setting and the locale mirror together, and the two are
     * compared here to tell a real change from the settings flow re-emitting the value that is
     * already in force. Without it, every emission would restart the activity.
     */
    private lateinit var lastAppliedLocale: AppLanguage

    /**
     * Builds the activity in the chosen language, before anything resolves a string.
     *
     * Android's configurable resources can only be told what to draw while the context is being
     * attached. The mirror is read from the context handed in rather than from the property of the
     * same name, which would come straight back here.
     */
    override fun attachBaseContext(newBase: Context) {
        val base = AppLocale.localized(newBase)
        lastAppliedLocale = AppLocale.current(newBase)
        super.attachBaseContext(base)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the last-resort crash recorder before anything else can throw, so a crash
        // leaves a report the user can export from the About screen.
        CrashLogger.install(this)
        // Both bars are transparent, and the navigation bar is asked for by name: the edge-to-edge
        // helper's default style paints a translucent scrim behind the navigation buttons on API
        // 28 and below, and a scrim is exactly the band this removes. Behind the bar the app's own
        // surface shows through, painted to the bottom of the window.
        //
        // The icons over them follow the *app's* theme rather than the system's. The helper's own
        // detection reads the phone's setting, so an app switched to light on a phone in dark mode
        // drew white status-bar icons on its own white background. The theme in force is read here
        // from the settings the view model already holds - one blocking read, so it is in hand
        // before the first frame - and applied again from the composition whenever it changes.
        val appDark = viewModel.settings.value.darkMode.isDark(systemDark())
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { appDark },
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { appDark },
        )
        super.onCreate(savedInstanceState)
        // Screenshots and screen recording are blocked, or allowed, before anything is drawn: the
        // stored setting is already in hand, because the view model reads it as it is built.
        applyScreenCapturePolicy(viewModel.settings.value.screenCaptureDisabled)
        // Only a fresh launch opens a note. The extra stays on the activity's intent for as long
        // as the activity lives, so a rotation - which runs this again - must not open it twice.
        if (savedInstanceState == null) {
            pendingNoteId.value = intent?.getStringExtra(EXTRA_NOTE_ID)
        }
        // Anything that changed while the app was not running - a dark-mode switch, a setting, a
        // note edited from a widget - is drawn again as soon as the app is opened.
        HiNotesWidgetProvider.refreshAll(this)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()
            val noteToOpen by pendingNoteId.collectAsStateWithLifecycle()

            // The pieces of a language the running app cannot change by itself.
            //
            // The mirror is written and, from Android 13, the platform is told as well - so the
            // widget and anything else the system builds for this app follow the choice. The
            // on-screen strings are the other piece, and only a newly built activity picks them up:
            // `attachBaseContext` decides the language once, and this activity handles locale
            // changes itself (`android:configChanges`), so nothing else restarts it and the strings
            // would otherwise change only the next time the app is opened. The comparison is against
            // the language the activity was built in, which is what the mirror holds, so the
            // recreation runs once per change rather than once per emission.
            LaunchedEffect(settings.language) {
                val language = settings.language
                if (language == lastAppliedLocale) return@LaunchedEffect
                AppLocale.apply(this@MainActivity, language)
                recreate()
            }

            // The switch is inside this app, so the window follows it while the app is open rather
            // than only being told once at launch.
            LaunchedEffect(settings.screenCaptureDisabled) {
                applyScreenCapturePolicy(settings.screenCaptureDisabled)
            }

            val systemDark = isSystemInDarkTheme()
            val darkTheme = settings.darkMode.isDark(systemDark)

            // The bars' icons follow the theme the app is drawing in, whatever the phone is set to:
            // a switch to light mode has to reach the status bar as well as the window.
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        darkTheme
                    },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) {
                        darkTheme
                    },
                )
            }

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
     * Puts the stored policies on the window again whenever the activity comes forward.
     *
     * The screen-capture flag is a property of the window rather than of the composition, and the
     * window can outlive the code that set it - a start that never ran through [onCreate], or a flag
     * a device kept on a window it pooled and handed back. Asking again here, with the value the
     * store holds now, is what keeps the switch and the window from disagreeing however the activity
     * got back on screen.
     *
     * The notes are read again for the same reason: a widget writes them through its own repository
     * instance while the app is in the background, so the list this app is holding is a copy that can
     * be behind what the card has already written. Reading once on the way in is what makes a note
     * opened from a ticked card show the tick, and stops a save from writing the old text back.
     */
    override fun onResume() {
        super.onResume()
        applyScreenCapturePolicy(viewModel.settings.value.screenCaptureDisabled)
        lifecycleScope.launch { viewModel.reloadNotes() }
        // A card is drawn once and stays as it was drawn, so opening the app - by a tap on a card,
        // or any other way - asks for a drawing with the setting in force now. This is also the way
        // back after the process that would have heard a light/dark switch was gone.
        HiNotesWidgetProvider.refreshAll(this)
    }

    /**
     * The device's configuration changed, which with the app set to follow the system is what a
     * light/dark switch is.
     *
     * This activity declares `uiMode` in `configChanges`, so the switch does not restart it and no
     * other callback here would notice. The cards are painted from the setting in force, so this is
     * where a screen that is alive hears the change; the process hears the same change in
     * `HiNotesApplication`, and both requests are coalesced into one drawing.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        HiNotesWidgetProvider.refreshAll(this)
    }

    /** True while the device itself is in dark mode, which the app's own setting may override. */
    private fun systemDark(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES

    /**
     * Receives the file or picture a picker returned.
     *
     * The picks are started with `startActivityForResult` rather than through the activity-result
     * registry - see [PickedDocumentRelay] for why - so their results arrive here. Both request
     * codes are checked before anything is published, and a cancelled picker reports
     * `RESULT_CANCELED` rather than a URI.
     *
     * Every answer is logged, because a pick that never comes back and a pick that comes back empty
     * look the same from the screen: the app says nothing either way, and only this line tells the
     * two apart when the tap is reported as doing nothing.
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_DOCUMENT && requestCode != REQUEST_PICK_IMAGE) return
        val picked = pickedImage(data)
        // The address itself is not logged: a media id names a picture in the user's own gallery, and
        // these lines travel with the diagnostics export. The scheme and the authority are enough to
        // tell a document pick from a picker that answered with nothing.
        Log.i(
            PICK_TAG,
            "picker answer: request=$requestCode result=$resultCode data=${data != null} " +
                "clip=${data?.clipData?.itemCount ?: 0} " +
                "uri=${picked?.let { "${it.scheme}://${it.authority}" }}",
        )
        // A picture that came back without an address is reported as a failed pick rather than
        // dropped here: the editor is the only place that can tell the user, and a picker that
        // answers with nothing is a tap that would otherwise look like it did nothing at all.
        if (resultCode != Activity.RESULT_OK) {
            if (requestCode == REQUEST_PICK_IMAGE) PickedImageRelay.refuse()
            return
        }
        when (requestCode) {
            // The answer is read the same way for a file as for a picture: a picker may hand its
            // result over in a clip item rather than in the intent's data, and one that does would
            // otherwise be dropped in silence.
            REQUEST_OPEN_DOCUMENT -> picked?.let { PickedDocumentRelay.publish(it) }
            else -> picked?.let { PickedImageRelay.publish(it) }
                ?: PickedImageRelay.refuse()
        }
    }

    companion object {
        /** Extra a tapped widget carries: the id of the note it shows. */
        const val EXTRA_NOTE_ID = "com.hiapps.hinotes.extra.NOTE_ID"

        /** The tag a picked picture is reported under, on both sides of the handover. */
        private const val PICK_TAG = "HiNotesEditor"
    }
}
