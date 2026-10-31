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
import androidx.annotation.RequiresApi
import com.hiapps.hinotes.MainActivity
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.HiNotesSettings
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.ui.editor.Markdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "HiNotesWidget"

/**
 * The home-screen widget: one rounded card showing one note.
 *
 * Each placed widget remembers the note it was given (see [WidgetNotes]), so two widgets on the
 * same home screen can show two different notes. The title stays at the top of the card and the
 * body scrolls underneath it - as a collection of items, see [WidgetItems] - and the card is painted
 * in the app's own scheme: its fill is the scheme's `onSecondary` and its text roles come from the
 * same scheme: see [widgetStyle].
 *
 * Nothing polls. A widget is drawn when the host asks for it ([onUpdate], and again once the host
 * has measured it), when the app changes something it is showing - an edited, deleted or locked note
 * redraws only the widgets that show it ([refreshNote]), while an appearance setting or the unlock
 * password redraws them all ([refreshAll]) - and once by the picker itself, because not every
 * launcher asks for that first drawing (see [WidgetConfigActivity]). A light/dark switch of the
 * phone is a change of appearance too, but this receiver cannot hear it: the application registers
 * the receiver the platform delivers that broadcast to and asks for [refreshAll] from there - see
 * `HiNotesApplication`.
 */
class HiNotesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        drawNow(context, ids)
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
        drawNow(context, intArrayOf(appWidgetId))
    }

    /**
     * The broadcasts the widget's own receiver answers: the host's updates and a box's tick.
     *
     * A tap on a card's rows does not arrive here: those taps open [WidgetTapActivity], which asks
     * this class to write the tick; see [tickTask].
     *
     * Neither does the phone's configuration change. `ACTION_CONFIGURATION_CHANGED` is never
     * delivered to a receiver declared in the manifest, so a light/dark switch is heard by the
     * process and by the screen instead, and both ask for the repaint from there - see
     * `HiNotesApplication` and `MainActivity`.
     */
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TICK_TASK) {
            tickTask(context, intent)
            return
        }
        super.onReceive(context, intent)
    }

    /**
     * Draws [ids] before the broadcast that asked for it ends.
     *
     * This is the drawing a host's own request asks for - a card placed, measured or resized - and it
     * is not something the app can put off: the broadcast is over when this method returns, and a
     * device that reclaims an empty process (which the launcher home screens of some makers are
     * known to do) would leave the card on its placeholder layout. [goAsync] is what keeps the
     * process alive until every card of this request has been drawn, and the pending result is
     * finished in every case, including a failed drawing.
     */
    private fun drawNow(context: Context, ids: IntArray) {
        if (ids.isEmpty()) return
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                val settings = readSettings(app)
                ids.forEach { draw(app, it, settings) }
            } catch (failure: Throwable) {
                Log.w(TAG, "could not draw widgets", failure)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Writes the tick a tapped box asked for.
     *
     * The tap reaches the app through [WidgetTapActivity], which finishes as soon as it has sent
     * this broadcast, so this receiver is the last thing of the app's that is running while the
     * write is made. [goAsync] is what keeps the process alive until then: without it the process
     * is empty and can be reaped between the write being started and the note being stored, which
     * a user reads from the home screen as a box that does nothing.
     */
    private fun tickTask(context: Context, intent: Intent) {
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val line = intent.getIntExtra(EXTRA_TASK_LINE, -1)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID || line < 0) return
        val checked = intent.getBooleanExtra(EXTRA_TASK_CHECKED, false)
        val pending = goAsync()
        scope.launch {
            try {
                tick(context.applicationContext, widgetId, line, checked)
            } catch (failure: Throwable) {
                Log.w(TAG, "could not tick a task on widget $widgetId", failure)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        // The host owns the id from here on; nothing may be left pointing at a widget that is gone.
        ids.forEach { WidgetNotes.unbind(context, it) }
    }

    companion object {

        /** The action a tapped box sends the receiver when it asks for its line to be ticked. */
        const val ACTION_TICK_TASK = "com.hiapps.hinotes.action.TICK_TASK"

        /** The source line of the task a tapped box stands for. */
        const val EXTRA_TASK_LINE = "com.hiapps.hinotes.extra.TASK_LINE"

        /** The state the tapped box asked for: true for a tick, false for clearing it. */
        const val EXTRA_TASK_CHECKED = "com.hiapps.hinotes.extra.TASK_CHECKED"

        /**
         * Where a redraw is prepared.
         *
         * A redraw reads the note from the database and the appearance from the settings store, and
         * neither belongs on a broadcast receiver's main thread or in the frame the user is looking
         * at when the app itself asks for one.
         *
         * One drawing runs at a time: the host re-applies a card's views rather than inflating a
         * new set for every update, so two drawings of the same widget running over each other is
         * what a card whose rows change under the finger looks like.
         */
        private val scope = CoroutineScope(
            SupervisorJob() + Dispatchers.Default.limitedParallelism(1),
        )

        /**
         * The drawings waiting for their window to start, by widget id.
         *
         * Several triggers arrive for one card within a frame or two - the host's update after the
         * picker, the picker's own drawing, an app save, a configuration change - and every one of
         * them used to rebuild the card and re-set its list. Nothing in a burst like that has
         * changed what the card shows by the time the last one runs, so the window collapses them
         * into one drawing and the list is replaced once instead of once per trigger.
         */
        private val pending = HashMap<Int, Job>()

        /** How long a drawing waits for the rest of a burst; short enough to still look immediate. */
        private const val COALESCE_MS = 150L

        /**
         * Ticks, or clears, the task a box on a card stands for.
         *
         * The state the tap asks for travels with it, because the box was drawn in one state and
         * pressed in that same one: the tap means the other one, whatever the store says by the time
         * the write is made. A line that is no longer a task is left alone - the note may have been
         * edited between the card being drawn and the box being pressed - and nothing else about the
         * note is touched.
         *
         * The write and the redraw that follows it are what the caller keeps the process alive for
         * (see [tickTask]), so the redraw is made here rather than scheduled: the card has to be
         * showing the tick by the time the caller lets the process go.
         */
        internal suspend fun tick(context: Context, widgetId: Int, line: Int, checked: Boolean) {
            val app = context.applicationContext
            // What the card is allowed to show, which is nothing at all while the app is locked.
            val note = WidgetContent.note(app, widgetId) ?: return
            if (note.isLocked) {
                Log.i(TAG, "note ${note.id} is locked; its tasks are read-only")
                return
            }
            val updated = Markdown.setTaskChecked(note.content, line, checked)
            // The content only: the note's modified time is left alone, or ticking an item would
            // reorder the app's own list behind the card the user is ticking.
            if (updated == note.content) {
                // A box was pressed for a line that is no longer a task - the note was edited
                // between the card being drawn and the box being pressed. Said out loud, because a
                // tick that changes nothing looks exactly like a tap that never arrived when it is
                // read from the home screen.
                Log.w(TAG, "line $line of note ${note.id} is not a task; nothing to tick")
            } else {
                WidgetRepositories.notes(app).replaceContent(note.id, updated)
                Log.i(TAG, "ticked line $line of note ${note.id} on widget $widgetId as $checked")
            }
            // A drawing that was already waiting read the note before this write; it is replaced
            // rather than left to paint the pre-tick note after the tick has been painted.
            cancelPending(widgetId)
            draw(app, widgetId, readSettings(app))
        }

        /** Redraws every placed widget; the appearance settings change all of them at once. */
        fun refreshAll(context: Context) {
            ids(context).forEach { refresh(context, it) }
        }

        /** Redraws the widgets showing any of [noteIds], each of them once. */
        fun refreshNotes(context: Context, noteIds: Collection<String>) {
            noteIds.flatMap { WidgetNotes.widgetsShowing(context, it) }
                .toSet()
                .forEach { refresh(context, it) }
        }

        /** Redraws the widgets showing [noteId], and only those. */
        fun refreshNote(context: Context, noteId: String) = refreshNotes(context, listOf(noteId))

        private fun ids(context: Context): IntArray =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, HiNotesWidgetProvider::class.java))

        /**
         * Asks for a drawing of one widget from what is stored now.
         *
         * A request that arrives while another drawing of the same widget is still waiting replaces
         * it: both would read the same stored note, so there is nothing for the earlier one to draw.
         */
        internal fun refresh(context: Context, widgetId: Int) {
            val app = context.applicationContext
            synchronized(pending) {
                pending.remove(widgetId)?.cancel()
                pending[widgetId] = scope.launch {
                    delay(COALESCE_MS)
                    synchronized(pending) { pending.remove(widgetId) }
                    draw(app, widgetId, readSettings(app))
                }
            }
        }

        /** Forgets a drawing that has not started yet, so a newer drawing is the one that runs. */
        private fun cancelPending(widgetId: Int) {
            synchronized(pending) {
                pending.remove(widgetId)?.cancel()
            }
        }

        /**
         * Reads and draws one widget.
         *
         * The body is built in one of two shapes, because the platform's collection API changed
         * under it. From API 32 the supported form hands the host every item inside the card
         * itself (`RemoteCollectionItems`), so a single update carries the whole drawing and
         * nothing is left to notify. Below 32 the body stays the service-backed collection the app
         * shipped for those versions, and its items are asked for separately - which is why the
         * notification follows the update there, and only there. The first implementation of the
         * eager form (API 31) is left on the service on purpose: it is the reason the AndroidX
         * compatibility layer uses that same split. From API 35 the service-backed form is
         * deprecated, and on Android 16 the platform converts it into the eager shape itself while
         * `notifyAppWidgetViewDataChanged` may quietly do nothing.
         *
         * A failure is logged rather than thrown: this runs on a scope nobody awaits, and the user
         * is looking at their home screen, not at the app that would otherwise be taken down.
         */
        @Suppress("DEPRECATION")
        private suspend fun draw(app: Context, widgetId: Int, settings: HiNotesSettings) {
            runCatching {
                val content = WidgetContent.card(app, widgetId)
                AppWidgetManager.getInstance(app).apply {
                    updateAppWidget(widgetId, card(app, widgetId, content, settings))
                    // The legacy collection is read from the service, so it is told that a new
                    // drawing is waiting; an eager one travelled with the update, and a card with
                    // nothing bound has no collection to refresh at all.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2 &&
                        content is WidgetContent.Card.Shown
                    ) {
                        notifyAppWidgetViewDataChanged(intArrayOf(widgetId), android.R.id.list)
                    }
                }
            }.onFailure { Log.w(TAG, "could not draw widget $widgetId", it) }
        }

        /** The appearance a card is drawn with, or the defaults when the store cannot be read. */
        private suspend fun readSettings(app: Context): HiNotesSettings =
            runCatching { WidgetRepositories.settings(app).current() }
                .getOrDefault(HiNotesSettings())

        /** The card: the note's title over its scrolling body, or an empty card. */
        private fun card(
            app: Context,
            widgetId: Int,
            content: WidgetContent.Card,
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

            if (content !is WidgetContent.Card.Shown) {
                // Nothing to draw: the note has gone, nothing was ever chosen, or the app has an
                // unlock password. The card is left in the scheme's colour and holds nothing at
                // all, whatever the title setting says - an empty card has no title for that
                // setting to be about.
                views.setViewVisibility(R.id.widget_title, titleVisibility(settings, bound = false))
                views.setViewVisibility(android.R.id.list, View.GONE)
                // The tap is taken off the card rather than only left unset: the card was drawn a
                // moment ago with a tap on it, so the drawing that follows a deletion says what
                // the tap does now instead of leaving the old one in place.
                views.setOnClickPendingIntent(
                    R.id.widget_card,
                    when (content) {
                        // A note that has gone is a note to choose again: the same picker the
                        // launcher opens for a new widget, over this widget's own binding.
                        is WidgetContent.Card.Missing ->
                            if (content.notesAvailable) pickNote(app, widgetId) else openApp(app, widgetId)
                        // A locked app has nothing to offer here. The card says nothing and takes
                        // no tap rather than becoming a second way round the password.
                        else -> null
                    },
                )
                return views
            }
            val note = content.note

            // With the title switched off the card shows the note's body alone - see
            // `widget_card.xml`, where the gap under the title belongs to the title.
            views.setViewVisibility(R.id.widget_title, titleVisibility(settings, bound = true))
            if (settings.widgetShowTitle) {
                views.setTextViewText(
                    R.id.widget_title,
                    note.displayTitle(app.getString(R.string.home_untitled)),
                )
                views.setTextColor(R.id.widget_title, style.title)
            }

            // The body is a collection, and the platform has two ways of handing one to the host.
            // From API 32 its items are built here and travel inside the card, which is the shape
            // the platform asks for on 16; below it the collection stays the service's, its items
            // are read as the list scrolls, and the view data notification refreshes them. See
            // [draw] for why the notification only exists on that second path.
            val body = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
                // A body that cannot be built must not cost the card its update: the service shape
                // is still installed on this platform, and a list drawn from it is a list the
                // reader can scroll even when the eager snapshot failed.
                runCatching { eagerBody(views, app, widgetId, note, settings) }.getOrElse { failure ->
                    Log.w(TAG, "could not build the body eagerly; falling back to the service", failure)
                    legacyBody(views, app, widgetId)
                }
            } else {
                legacyBody(views, app, widgetId)
            }
            if (body) {
                views.setViewVisibility(android.R.id.list, View.VISIBLE)
                // The list scrolls, so its items are what a finger lands on: every one of them is
                // filled into the tap handler's template - a line opens the note, a task's box
                // ticks the item - and the tap on the card covers the title and the margins and
                // opens the note itself.
                views.setPendingIntentTemplate(android.R.id.list, tapNote(app, widgetId, note.id))
            } else {
                // Nothing to scroll: the note holds no body at all, or its items could not be
                // built. The title is the card then, and there is no collection to tap.
                views.setViewVisibility(android.R.id.list, View.GONE)
            }
            views.setOnClickPendingIntent(R.id.widget_card, openNote(app, note.id, widgetId))
            return views
        }

        /**
         * Hands the host the whole body as items built here, on API 32 and above.
         *
         * `RemoteCollectionItems` is the form of a collection those versions support directly, and
         * the service-backed collection this app shipped for older ones is deprecated from API 35.
         * On Android 16 keeping the service would make the platform convert the collection for
         * every update instead, which is where a card's rows were seen to repeat and reorder; this
         * shape is the one update that carries the card and the body it scrolls.
         *
         * The items are built in this process, so the whole card is one parcel: the item builder
         * caps the body it returns (see [WidgetItems]) rather than letting a pathological note push
         * the update past what a binder transaction can carry.
         *
         * @return whether the card has a body at all; a note with nothing to scroll gets none.
         */
        @RequiresApi(Build.VERSION_CODES.S_V2)
        private fun eagerBody(
            views: RemoteViews,
            app: Context,
            widgetId: Int,
            note: Note,
            settings: HiNotesSettings,
        ): Boolean {
            val items = WidgetItems.list(app, widgetId, note, settings)
            if (items.isEmpty()) return false
            val collection = RemoteViews.RemoteCollectionItems.Builder()
                .setViewTypeCount(1)
                .setHasStableIds(false)
            items.forEachIndexed { index, item -> collection.addItem(index.toLong(), item) }
            views.setRemoteAdapter(android.R.id.list, collection.build())
            return true
        }

        /**
         * Points the list at the widget's service, which is how the body is drawn below API 32.
         *
         * The two-argument `setRemoteAdapter` is the one every supported version honours; the form
         * that takes the app widget id arrived in API 31, the same version the eager body above
         * starts after. The data URI is what makes the adapter intent this widget's own: without
         * one, every widget would share the first intent's extras and they would all show the same
         * note.
         *
         * @return whether the card has a body at all: this path always has one to read, even when
         *   the note itself turns out to be empty.
         */
        @Suppress("DEPRECATION")
        private fun legacyBody(views: RemoteViews, app: Context, widgetId: Int): Boolean {
            views.setRemoteAdapter(android.R.id.list, adapter(app, widgetId))
            return true
        }

        /**
         * Whether the title is drawn.
         *
         * The setting only answers that for a card that is showing a note: a card with nothing bound
         * has no title to show either way, and its appearance is not a preference about titles. One
         * place decides both cases, so the two drawings of a card cannot disagree about it.
         */
        private fun titleVisibility(settings: HiNotesSettings, bound: Boolean): Int =
            if (bound && settings.widgetShowTitle) View.VISIBLE else View.GONE

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
                // The address is what tells this pending intent apart from the plain "open the
                // app" one below: two intents differing only in their extras are one pending
                // intent to the platform, and the second set would overwrite the first.
                data = Uri.parse("hinotes://note/$widgetId")
            }
            return PendingIntent.getActivity(
                app,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * The template every row of the card's body is filled into.
         *
         * A row cannot carry a pending intent of its own that would work on every launcher, and it
         * cannot start an activity itself; what a collection item has always supported is a *fill-in*
         * intent aimed at one template. So the template is this app's tap handler
         * ([WidgetTapActivity]) with the note the card shows in it, every row fills it in with
         * nothing (a tap anywhere on a line opens the note), and a task's box fills in the line it
         * stands for (a tap there ticks the item instead). See `HiNotesWidgetService`.
         *
         * The pending intent is mutable because filling it in is exactly what the rows do; on
         * Android 12 that has to be asked for, and before that every pending intent was mutable.
         * The intent is explicit, so nothing any other app does with the filled-in copy can point it
         * anywhere this app did not name.
         */
        private fun tapNote(app: Context, widgetId: Int, noteId: String): PendingIntent {
            val intent = Intent(app, WidgetTapActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(MainActivity.EXTRA_NOTE_ID, noteId)
                data = Uri.parse("hinotes://tap/$widgetId")
            }
            return PendingIntent.getActivity(
                app,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
        }

        /**
         * Opens the app on its home screen, with no note to show.
         *
         * The tap of a card whose note is gone and whose app holds no note at all: the picker would
         * offer an empty list, so what is left to do is what the launcher icon does.
         */
        private fun openApp(app: Context, widgetId: Int): PendingIntent {
            val intent = Intent(app, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                data = Uri.parse("hinotes://home/$widgetId")
            }
            return PendingIntent.getActivity(
                app,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        /**
         * Asks which note this widget should show.
         *
         * The same picker the launcher opens when a widget is placed, started by the app rather than
         * by the host: it reads the widget id the card was drawn with, binds the note that is chosen
         * and redraws the card. A tap on a card whose note has been deleted is the one moment the
         * choice has to be made again, and this is the only screen that can make it.
         */
        private fun pickNote(app: Context, widgetId: Int): PendingIntent {
            val intent = Intent(app, WidgetConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                data = Uri.parse("hinotes://pick/$widgetId")
            }
            return PendingIntent.getActivity(
                app,
                widgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
