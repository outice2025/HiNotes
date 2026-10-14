package com.hiapps.hinotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.hiapps.hinotes.MainActivity
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.HiNotesSettings
import com.hiapps.hinotes.data.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "HiNotesWidget"

/**
 * The home-screen widget: one rounded card showing one note.
 *
 * Each placed widget remembers the note it was given (see [WidgetNotes]), so two widgets on the
 * same home screen can show two different notes. The title stays at the top of the card and the
 * body scrolls underneath it - see [HiNotesWidgetService] for why the body is a list rather than a
 * scrolling view - and the card is painted in the app's own scheme, in the roles an app card uses:
 * see [widgetStyle].
 *
 * Nothing polls. A widget is drawn when the host asks for it ([onUpdate], and again once the host
 * has measured it), when the app changes something it is showing - an edited, deleted or locked note
 * redraws only the widgets that show it ([refreshNote]), while an appearance setting or the unlock
 * password redraws them all ([refreshAll]) - and once by the picker itself, because not every
 * launcher asks for that first drawing (see [WidgetConfigActivity]).
 */
class HiNotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { refresh(context, it) }
    }

    /**
     * The host has measured the widget.
     *
     * This arrives when a widget is placed and whenever it is resized, which makes it the one signal
     * that a newly added card can rely on: a widget drawn here cannot be left showing the layout's
     * own placeholder colours because its first update went missing.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        refresh(context, appWidgetId)
    }

    /**
     * The system's configuration changed, which may mean the scheme did.
     *
     * A widget follows the app's appearance settings, and one of those options is "follow the
     * system": with it on, switching the phone to dark mode has to repaint the card even though
     * nothing in the app changed. The platform restricts implicit broadcasts, so this is best
     * effort - when it arrives, everything is repainted; when it does not, the next note save, app
     * launch or resize does the same job.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CONFIGURATION_CHANGED) {
            refreshAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        // The host owns the id from here on; nothing may be left pointing at a widget that is gone.
        ids.forEach { WidgetNotes.unbind(context, it) }
    }

    companion object {

        /**
         * Where a redraw is prepared.
         *
         * A redraw reads the note from the database and the appearance from the settings store, and
         * neither belongs on a broadcast receiver's main thread or in the frame the user is
         * looking at when the app itself asks for one.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Redraws every placed widget; the appearance settings change all of them at once. */
        fun refreshAll(context: Context) {
            val widgetIds = ids(context)
            if (widgetIds.isEmpty()) return
            scope.launch {
                val app = context.applicationContext
                // The appearance is read once for the whole sweep rather than once per widget.
                val settings = readSettings(app)
                widgetIds.forEach { draw(app, it, settings) }
            }
        }

        /** Redraws the widgets showing any of [noteIds], each of them once. */
        fun refreshNotes(context: Context, noteIds: Collection<String>) {
            val widgetIds = noteIds.flatMap { WidgetNotes.widgetsShowing(context, it) }.toSet()
            widgetIds.forEach { refresh(context, it) }
        }

        /** Redraws the widgets showing [noteId], and only those. */
        fun refreshNote(context: Context, noteId: String) = refreshNotes(context, listOf(noteId))

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, HiNotesWidgetProvider::class.java))

        /** Draws one widget from what is stored now. */
        internal fun refresh(context: Context, widgetId: Int) {
            scope.launch {
                val app = context.applicationContext
                draw(app, widgetId, readSettings(app))
            }
        }

        /**
         * Reads and draws one widget.
         *
         * The service-backed collection API is deprecated in favour of `RemoteCollectionItems`,
         * which arrived in API 31 and builds every item up front - neither of which suits an app
         * that still supports 24 or a note long enough to be worth scrolling lazily. The calls
         * below are the ones every supported version honours, so they stay.
         *
         * A failure is logged rather than thrown: this runs on a scope nobody awaits, and the user
         * is looking at their home screen, not at the app that would otherwise be taken down.
         */
        @Suppress("DEPRECATION")
        private suspend fun draw(app: Context, widgetId: Int, settings: HiNotesSettings) {
            runCatching {
                val note = WidgetContent.note(app, widgetId)
                AppWidgetManager.getInstance(app).apply {
                    updateAppWidget(widgetId, card(app, widgetId, note, settings))
                    // The body is a collection, so its items are asked for separately - and a new
                    // note, or a longer one, is a different number of them.
                    notifyAppWidgetViewDataChanged(intArrayOf(widgetId), android.R.id.list)
                }
            }.onFailure { Log.w(TAG, "could not draw widget $widgetId", it) }
        }

        /** The appearance a card is drawn with, or the defaults when the store cannot be read. */
        private suspend fun readSettings(app: Context): HiNotesSettings =
            runCatching { WidgetRepositories.settings(app).current() }
                .getOrDefault(HiNotesSettings())

        /** The card: the note's title over its scrolling body, or an empty card. */
        // The two-argument `setRemoteAdapter` is deprecated in favour of a form that takes the
        // app widget id, and that form arrived in API 31 while this app still supports 24. The
        // deprecated call is the one every supported version honours, so it stays.
        @Suppress("DEPRECATION")
        private fun card(
            app: Context,
            widgetId: Int,
            note: Note?,
            settings: HiNotesSettings,
        ): RemoteViews {
            val style = widgetStyle(app, settings, systemDark(app))
            val views = RemoteViews(app.packageName, R.layout.widget_card)
            // The palette arrives as a filter on the card's surface, which is the reason that
            // surface is a view of its own: a RemoteViews can hand a colour to a background only
            // from API 31 on, and the one background setter older platforms have replaces the
            // rounded shape with a plain fill. A colour filter keeps the shape - and it blends with
            // that shape's own alpha, so the corners stay antialiased rather than squared off. It is
            // also one of the methods RemoteViews may call from API 8 on, so every supported version
            // draws a card the same way rather than two of them differently.
            views.setInt(R.id.widget_surface, "setColorFilter", style.container)

            if (note == null) {
                // Nothing is bound, the note it was bound to is gone, or the app has an unlock
                // password: the card is drawn in the scheme's colour and holds nothing at all.
                views.setViewVisibility(R.id.widget_title, View.GONE)
                views.setViewVisibility(android.R.id.list, View.GONE)
                // The tap is taken off the card rather than only left unset. The card was drawn a
                // moment ago with a tap on it, so the drawing that follows a deletion says the tap
                // is gone instead of leaving that to be inferred from an action that is not there.
                // A press on an empty card therefore opens nothing, which leaves the user on the
                // home screen they pressed it from rather than in the note that has just gone.
                views.setOnClickPendingIntent(R.id.widget_card, null)
                return views
            }

            views.setViewVisibility(R.id.widget_title, View.VISIBLE)
            views.setViewVisibility(android.R.id.list, View.VISIBLE)
            views.setTextViewText(
                R.id.widget_title,
                note.displayTitle(app.getString(R.string.home_untitled)),
            )
            views.setTextColor(R.id.widget_title, style.title)

            val open = openNote(app, note.id, widgetId)
            views.setRemoteAdapter(android.R.id.list, adapter(app, widgetId))
            // The list scrolls, so its items are what a finger lands on: the template makes every
            // one of them open the note, and the tap on the card covers the title and the margins.
            views.setPendingIntentTemplate(android.R.id.list, open)
            views.setOnClickPendingIntent(R.id.widget_card, open)
            return views
        }

        /**
         * The service intent the list asks for its items with.
         *
         * The data URI is what makes it this widget's own: without one, every widget would share
         * the first intent's extras and they would all show the same note.
         */
        private fun adapter(app: Context, widgetId: Int): Intent =
            Intent(app, HiNotesWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }

        /** Opens [noteId] in the editor, over whatever the app is already showing. */
        private fun openNote(app: Context, noteId: String, widgetId: Int): PendingIntent {
            val intent = Intent(app, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            // A list item's tap is delivered by filling this intent in, and filling in needs a
            // mutable PendingIntent from Android 12 on; before that every PendingIntent is mutable
            // and the flag does not exist. The intent itself is explicit and carries no data the
            // fill-in could redirect, so nothing about it is open to another app.
            val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            return PendingIntent.getActivity(
                app,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or mutability,
            )
        }
    }
}
