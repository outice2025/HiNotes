package com.hiapps.hinotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hiapps.hinotes.R
import com.hiapps.hinotes.ui.AppViewModel
import com.hiapps.hinotes.ui.AppViewModelFactory
import com.hiapps.hinotes.ui.LockGate
import com.hiapps.hinotes.ui.applyScreenCapturePolicy
import com.hiapps.hinotes.ui.components.NoteCard
import com.hiapps.hinotes.ui.components.SettingsScaffold
import com.hiapps.hinotes.ui.theme.HiNotesTheme

/**
 * The picker the launcher opens when a widget is placed: which note should this one show.
 *
 * The screen is the app's own list, drawn in the app's own theme, because the choice is a note and
 * a note is best recognised by what it says. Choosing one records it against the widget ([WidgetNotes])
 * and answers the host, which then places the widget and asks for its first drawing.
 *
 * An app password is asked for first: this screen shows note titles and their opening lines, and
 * being reached from the launcher rather than from the app is no reason to skip the gate the rest
 * of the app is behind. [FragmentActivity] because the gate's biometric prompt needs one.
 *
 * The answer is `RESULT_CANCELED` until a note is actually chosen, so backing out, swiping away or
 * being killed leaves nothing behind on the home screen.
 */
class WidgetConfigActivity : FragmentActivity() {

    private val viewModel: AppViewModel by viewModels { AppViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(Activity.RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // This screen shows note titles and their opening lines, so it is behind the same
        // screen-capture setting the rest of the app is, applied before it draws anything.
        applyScreenCapturePolicy(viewModel.settings.value.screenCaptureDisabled)

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val locked by viewModel.locked.collectAsStateWithLifecycle()

            HiNotesTheme(
                darkTheme = settings.darkMode.isDark(isSystemInDarkTheme()),
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
                    NotePicker(
                        viewModel = viewModel,
                        onCancel = { finish() },
                        onPick = { noteId -> choose(widgetId, noteId) },
                    )
                }
            }
        }
    }

    /** Records the choice and answers the host, which places the widget and draws it. */
    private fun choose(widgetId: Int, noteId: String) {
        WidgetNotes.bind(this, widgetId, noteId)
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        // The card is drawn here as well as left to the host: every launcher is supposed to ask for
        // a drawing once the picker has answered, and the ones that do not leave a freshly placed
        // widget blank until something else happens to redraw it. Started after the result, so the
        // host has the widget id by the time the drawing arrives.
        HiNotesWidgetProvider.refresh(this, widgetId)
        finish()
    }
}

/** The note list, in the same cards the home screen uses. */
@Composable
private fun NotePicker(
    viewModel: AppViewModel,
    onCancel: () -> Unit,
    onPick: (String) -> Unit,
) {
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val untitled = stringResource(R.string.home_untitled)
    val lockedLabel = stringResource(R.string.home_locked_badge)
    val pinnedLabel = stringResource(R.string.home_pinned_badge)

    SettingsScaffold(
        title = stringResource(R.string.widget_pick_title),
        onBack = onCancel,
    ) {
        if (notes.isEmpty()) {
            Text(
                text = stringResource(R.string.widget_pick_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            notes.forEach { note ->
                NoteCard(
                    title = note.displayTitle(untitled),
                    content = note.content,
                    markdownEnabled = settings.markdownEnabled,
                    locked = note.isLocked,
                    lockedLabel = lockedLabel,
                    pinned = note.pinned,
                    pinnedLabel = pinnedLabel,
                    onClick = { onPick(note.id) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}
