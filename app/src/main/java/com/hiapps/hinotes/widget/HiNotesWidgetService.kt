package com.hiapps.hinotes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.HiNotesSettings
import kotlinx.coroutines.runBlocking

private const val TAG = "HiNotesWidget"

/**
 * The scrolling half of the widget: the note's body, one item per chunk of text.
 *
 * A widget cannot scroll a `ScrollView` on every version the app supports - RemoteViews only
 * accepts one from API 31 - so the body is a collection instead, which is the platform's own
 * answer for a widget that scrolls. A list is also what makes "swipe for more" mean something:
 * items are built as they come into view rather than all at once, and the layout switches the
 * scrollbars off, so the text simply continues as the user swipes.
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

        // Written on the binder thread in `onDataSetChanged` and read on the main thread in
        // `getViewAt`, so each is published whole: `@Volatile` is what stops the reader from
        // keeping the previous list, colour or settings after a refresh.
        @Volatile private var chunks: List<CharSequence> = emptyList()
        @Volatile private var bodyColor: Int = Color.BLACK
        @Volatile private var bodySizeSp: Float = 14f
        @Volatile private var settings: HiNotesSettings = HiNotesSettings()

        override fun onCreate() = Unit

        /**
         * Reads what the list shows.
         *
         * The contract for a remote views factory is that this runs on a background thread and may
         * block, which is exactly what reading the settings store and the note's row does. A read
         * that fails is logged rather than thrown: this runs in the launcher's process, and a card
         * drawn empty is a better outcome than the launcher being taken down with it.
         */
        override fun onDataSetChanged() {
            runCatching {
                settings = runBlocking { WidgetRepositories.settings(context).current() }
                // What the card shows is decided in one place, so a locked app hides the body here
                // exactly as it hides the title there.
                val note = runBlocking { WidgetContent.note(context, widgetId) }
                bodyColor = widgetStyle(context, settings, systemDark(context)).body
                bodySizeSp = WidgetText.bodySizeSp(settings.widgetFontScale)
                chunks = note
                    ?.let { WidgetText.chunks(WidgetText.body(it.content, settings.markdownEnabled)) }
                    .orEmpty()
            }.onFailure { failure ->
                Log.w(TAG, "could not read note for widget $widgetId", failure)
                chunks = emptyList()
            }
        }

        override fun getCount(): Int = chunks.size

        /**
         * One chunk, or an empty item.
         *
         * The host may ask for a position the list no longer has - it can read the count and then
         * ask for an item after a refresh has shortened the list - so the position is never used to
         * index the list directly.
         */
        override fun getViewAt(position: Int): RemoteViews {
            // The layout carries the weight the setting asks for; size and colour go on top of it.
            val views = RemoteViews(
                context.packageName,
                WidgetText.bodyLayout(settings.widgetFontWeight),
            )
            val chunk = chunks.getOrNull(position) ?: return views
            views.setTextViewText(R.id.widget_chunk, chunk)
            views.setTextColor(R.id.widget_chunk, bodyColor)
            views.setTextViewTextSize(R.id.widget_chunk, TypedValue.COMPLEX_UNIT_SP, bodySizeSp)
            // Filled in by the template the card sets: every item opens the same note.
            views.setOnClickFillInIntent(R.id.widget_chunk, Intent())
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false

        override fun onDestroy() = Unit
    }
}
