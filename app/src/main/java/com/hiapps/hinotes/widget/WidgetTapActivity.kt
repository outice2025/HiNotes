package com.hiapps.hinotes.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.hiapps.hinotes.MainActivity

/**
 * What a tap on one of a card's rows means.
 *
 * A widget is drawn by the launcher, and from Android 12 a view inside a collection item can carry a
 * click response of its own - but not every launcher honours one there. A tap on the body is
 * therefore delivered the way every launcher has always delivered it: the card's list has a
 * *template*, every row fills it in with what it wants (`setOnClickFillInIntent`, set in
 * `WidgetItems`), and the filled-in intent is this activity.
 *
 * It is the whole of the handler: it reads what the row filled in, and either asks the card's
 * provider to write the tick a task's box asked for, or starts the editor for the note the card
 * shows; then it finishes. It draws nothing on purpose - a tick is not a visit to the app - so its
 * window is transparent and animates nothing. Starting the editor from here is also the one activity
 * start this app is allowed to make from a home-screen tap: the launcher's tap opens this activity,
 * and an activity of ours that is on screen may open another.
 */
class WidgetTapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = intent
        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // A task's box fills the line it stands for in; nothing else does, and a row that fills
        // nothing in is a request to read the note.
        val line = intent?.getIntExtra(HiNotesWidgetProvider.EXTRA_TASK_LINE, -1) ?: -1
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID && line >= 0) {
            val checked = intent?.getBooleanExtra(
                HiNotesWidgetProvider.EXTRA_TASK_CHECKED,
                false,
            ) ?: false
            Log.i(TAG, "a task's box was tapped: widget=$widgetId line=$line checked=$checked")
            // The write is handed to the card's receiver rather than started here: a receiver
            // holds the process alive with goAsync() until the tick and the redraw have landed,
            // while this activity is finished the moment the tap has been read. A write started
            // from the activity would outlive it by a moment and could be reaped with the process
            // before it arrived - which a user reads as a box that does nothing.
            sendBroadcast(
                Intent(this, HiNotesWidgetProvider::class.java).apply {
                    action = HiNotesWidgetProvider.ACTION_TICK_TASK
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    putExtra(HiNotesWidgetProvider.EXTRA_TASK_LINE, line)
                    putExtra(HiNotesWidgetProvider.EXTRA_TASK_CHECKED, checked)
                },
            )
            finish()
            return
        }

        val noteId = intent?.getStringExtra(MainActivity.EXTRA_NOTE_ID)
        val open = Intent(this, MainActivity::class.java).apply {
            if (noteId != null) putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        runCatching { startActivity(open) }
            .onFailure { Log.w(TAG, "could not open the note a card was showing", it) }
        finish()
    }

    private companion object {
        const val TAG = "HiNotesWidget"
    }
}
