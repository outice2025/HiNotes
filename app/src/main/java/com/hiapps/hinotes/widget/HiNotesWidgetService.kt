package com.hiapps.hinotes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.hiapps.hinotes.R
import kotlinx.coroutines.runBlocking

private const val TAG = "HiNotesWidget"

/**
 * The scrolling half of the widget: the note's body as a collection of items, one item per chunk of
 * text.
 *
 * A widget cannot scroll a `ScrollView` on every version the app supports - RemoteViews only accepts
 * one from API 31 - so the body is a collection instead, which is the platform's own answer for a
 * widget that scrolls. A list is also what makes "swipe for more" mean something: items are built as
 * they come into view rather than all at once, and the layout switches the scrollbars off, so the
 * text simply continues as the user swipes.
 *
 * This is the path for versions before Android 12L. From Android 12L (API 32) the provider hands the
 * host the same items eagerly through `RemoteViews.RemoteCollectionItems`, which is the platform's
 * modern API and skips the deprecated service intent (and the conversion layer the platform puts
 * around it); both paths draw through [WidgetItems], so an item looks the same either way. API 31 is
 * left on this path on purpose: the first version of the eager API is the one the platform's own
 * compatibility library avoids.
 *
 * The note and the appearance are read here rather than handed in: the launcher rebuilds a
 * collection whenever it wants the data, including long after the app's process has gone.
 */
class HiNotesWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ChunkFactory(applicationContext, intent)

    private class ChunkFactory(
        private val context: Context,
        intent: Intent,
    ) : RemoteViewsFactory {

        private val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )

        /**
         * The drawing the list asks its items from, or null while there is nothing to show.
         *
         * Written on the binder thread in [onDataSetChanged] and read from whichever thread the
         * host asks for an item on, so it is published whole: a reader has either the drawing the
         * old list was built from or the one the new list was.
         */
        @Volatile private var drawing: WidgetDrawing? = null

        /** The layout an empty item is built from, i.e. the one the drawing in hand uses. */
        @Volatile private var layout: Int = R.layout.widget_item_regular

        override fun onCreate() = Unit

        /**
         * Reads what the list shows.
         *
         * The contract for a remote views factory is that this runs on a background thread and may
         * block, which is exactly what reading the settings store, the note's row and the box
         * pictures does. A read that fails is logged rather than thrown: the platform hands a throw
         * from a factory method to the process's uncaught-exception handler, which takes the app
         * down while the launcher waits on the binder.
         */
        override fun onDataSetChanged() {
            runCatching {
                val settings = runBlocking { WidgetRepositories.settings(context).current() }
                layout = WidgetText.bodyLayout(settings.widgetFontWeight)
                // What the card shows is decided in one place, so a locked app hides the body here
                // exactly as it hides the title there.
                val note = runBlocking { WidgetContent.note(context, widgetId) }
                val built = note?.let {
                    WidgetItems.collection(context, widgetId, it, settings)
                }
                drawing = built
                // One line per drawing of the body: what a card shows is the one thing that cannot
                // be read back off the home screen when a report says it shows something else.
                Log.i(
                    TAG,
                    "widget $widgetId: ${built?.itemCount ?: 0} items, " +
                        "${built?.taskCount ?: 0} tasks, " +
                        "markdown=${settings.markdownEnabled}, boxes=${built?.hasBoxes ?: false}",
                )
            }.onFailure { failure ->
                Log.w(TAG, "could not read note for widget $widgetId", failure)
                drawing = null
            }
        }

        override fun getCount(): Int = drawing?.itemCount ?: 0

        /**
         * One item, or one that shows nothing.
         *
         * The host may ask for a position the list no longer has - it can read the count and then
         * ask for an item after a refresh has shortened the list - and it may hand the item back to
         * a frame it recycled from another one. Every item therefore writes the full state of every
         * view it can show; see [WidgetDrawing] for why that has to be every path.
         *
         * The platform does not catch a throw from a factory method: it is handed to the process's
         * default uncaught-exception handler, so an item that cannot be drawn is answered with an
         * empty one rather than with an exception.
         */
        override fun getViewAt(position: Int): RemoteViews {
            return try {
                drawing?.viewAt(position) ?: WidgetItems.emptyItem(context, layout)
            } catch (failure: Throwable) {
                Log.w(TAG, "could not draw item $position of widget $widgetId", failure)
                try {
                    WidgetItems.emptyItem(context, layout)
                } catch (fallbackFailure: Throwable) {
                    Log.w(TAG, "could not draw an empty item $position of widget $widgetId", fallbackFailure)
                    RemoteViews(context.packageName, layout)
                }
            }
        }

        override fun getLoadingView(): RemoteViews? = null

        /**
         * Four view types: one per item layout this app can draw with.
         *
         * A drawing inflates its items from one layout, chosen by the widget's own type weight - but
         * a factory outlives settings, so over its life it can serve all four of them. The host
         * assigns a view type to each distinct layout id it meets and refuses an item once that
         * mapping runs past the declared count, which after a type-weight change would be a card
         * whose body silently loses its rows. The count therefore covers every layout this service
         * can name, not just the one the current drawing uses.
         */
        override fun getViewTypeCount(): Int = 4

        /** Positional ids, and [hasStableIds] is false, so the host does not keep an item's view. */
        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false

        /** The drawing holds the decoded pictures the list was built with; the host has its copy. */
        override fun onDestroy() {
            drawing = null
        }
    }
}
