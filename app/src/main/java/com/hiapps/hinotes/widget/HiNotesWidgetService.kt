package com.hiapps.hinotes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.annotation.RequiresApi
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.HiNotesSettings
import com.hiapps.hinotes.data.NoteImages
import kotlinx.coroutines.runBlocking

private const val TAG = "HiNotesWidget"

/** The card's own inset, which a picture is drawn inside rather than over. See `widget_card.xml`. */
private const val CARD_INSET_DP = 16f

/**
 * The gap a task's box is given above and below, in dp.
 *
 * A task line is 18dp of box beside a line of text, and set at the line height alone the boxes of
 * two neighbouring items meet - which is the crowding this is here to prevent. Three dp on each side
 * is the gap the app's own checklist keeps between one row and the next.
 */
private const val TASK_GAP_DP = 3f

/**
 * How much of a card's height a picture may take.
 *
 * The rest is the note's own text: a picture is what the line of the note is about, but it is not
 * the note, and one that filled the card would put the writing about it out of sight.
 */
private const val PICTURE_SHARE = 3

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
        @Volatile private var chunks: List<WidgetChunk> = emptyList()
        @Volatile private var bodyColor: Int = Color.BLACK
        @Volatile private var bodySizeSp: Float = 14f
        @Volatile private var settings: HiNotesSettings = HiNotesSettings()
        @Volatile private var boxes: WidgetBoxes.Boxes? = null

        /**
         * The pictures the note holds, decoded.
         *
         * A list asks for its items as they come into view, so a note with the same picture on two
         * screens of text would otherwise be decoded again for every item that shows it. The map
         * lives as long as the factory does, which is as long as the note and the card's size stay
         * what they are: the host builds a new factory when it wants the data read again.
         *
         * The key is the picture's own address, because every picture in one body is decoded to the
         * same width - the width of the card it is drawn in - so the width is the factory's rather
         * than the entry's.
         */
        private val pictures = HashMap<String, Bitmap>()

        override fun onCreate() = Unit

        /**
         * Reads what the list shows.
         *
         * The contract for a remote views factory is that this runs on a background thread and may
         * block, which is exactly what reading the settings store, the note's row and the pictures in
         * it does. A read that fails is logged rather than thrown: this runs in the app's own process
         * while the launcher is waiting on the binder, and a card drawn empty is a better outcome
         * than the app being taken down with it.
         */
        override fun onDataSetChanged() {
            runCatching {
                settings = runBlocking { WidgetRepositories.settings(context).current() }
                // What the card shows is decided in one place, so a locked app hides the body here
                // exactly as it hides the title there.
                val note = runBlocking { WidgetContent.note(context, widgetId) }
                val style = widgetStyle(context, settings, systemDark(context))
                bodyColor = style.body
                bodySizeSp = WidgetText.bodySizeSp(settings.widgetFontScale)
                // A task's box is drawn at the body's own type size, so it is measured where the
                // body is built: the picture cannot be handed a size in sp for the launcher to
                // scale, the way the text can.
                val boxPx = WidgetText.boxSizePx(context, settings.widgetFontScale)
                boxes = WidgetBoxes.boxes(boxPx, style.densityDpi, style)
                // A task line becomes an item of its own only when its box can be drawn: the box is
                // taken out of the text and put beside it, and a row that lost its box would read as
                // prose. Without the pictures the characters stay in the text, as they always were.
                val taskLines = if (settings.markdownEnabled && boxes != null) {
                    note?.let { WidgetChunks.taskLines(it.content) }.orEmpty()
                } else {
                    emptyList()
                }
                chunks = note
                    ?.let {
                        WidgetText.body(
                            content = it.content,
                            markdown = settings.markdownEnabled,
                            boxPx = boxPx,
                            style = style,
                            image = ::picture,
                            taskLines = taskLines,
                        )
                    }
                    .orEmpty()
            }.onFailure { failure ->
                Log.w(TAG, "could not read note for widget $widgetId", failure)
                chunks = emptyList()
            }
        }

        /**
         * The picture at [url], decoded to the size the card draws it at, or null when there is
         * nothing to draw.
         *
         * A picture that cannot be read is not a failure of the drawing: the place it was written in
         * gives up the one character that stood for it, and the rest of the note is drawn as it was.
         */
        private fun picture(url: String): Bitmap? {
            pictures[url]?.let { return it }
            val decoded = NoteImages.decode(context, url, pictureWidthPx()) ?: return null
            // A picture taller than the room a card can give it is cut to that room: a photograph
            // fills the card on its own otherwise, and the rest of the note is pushed out of sight.
            // The taller copy is let go of straight away, because it is only ever the way to this one
            // and a card is drawn while the launcher waits on the binder.
            val tallest = pictureHeightPx()
            val sized = if (decoded.height > tallest) {
                Bitmap.createScaledBitmap(
                    decoded,
                    (decoded.width.toLong() * tallest / decoded.height).toInt().coerceAtLeast(1),
                    tallest,
                    true,
                ).also { decoded.recycle() }
            } else {
                decoded
            }
            // The picture is stamped with the display's own density, because a picture in a span is
            // scaled by the density it carries: the span draws it through a `BitmapDrawable`, which
            // scales the bitmap by the display's density over the bitmap's own, and a file decoded
            // from storage is stamped with the default 160dpi whatever screen it was taken on. Left
            // as it is decoded, the span would draw the picture at the phone's density as a multiple
            // of its size, and that size was already measured in the phone's own pixels.
            sized.density = context.resources.displayMetrics.densityDpi
            pictures[url] = sized
            return sized
        }

        /**
         * The width a picture is drawn at: the card's own width, less the insets it is drawn inside.
         *
         * The host reports both sizes a card can be dragged to, and the smaller of the two is the one
         * used, so a picture is decoded once at the width the card is at now rather than at the width
         * a wider card would give it.
         */
        private fun pictureWidthPx(): Int = widgetSizePx(
            AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
            AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
            largest = false,
        ) - (CARD_INSET_DP * 2 * context.resources.displayMetrics.density).toInt()

        /**
         * The height a picture may take: about a third of the card, which is a size the eye reads as
         * a picture rather than as a thumbnail while leaving most of the card to the note's text.
         *
         * The larger of the two heights is the one used, because a card can be dragged taller and the
         * cap is a share of the room the card can have rather than of the room it happens to have: a
         * picture sized from the smallest card would be a dozen pixels tall on the home screen.
         */
        private fun pictureHeightPx(): Int {
            val inset = (CARD_INSET_DP * context.resources.displayMetrics.density).toInt()
            val room = widgetSizePx(
                AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                largest = true,
            ) - inset
            return (room / PICTURE_SHARE).coerceAtLeast(1)
        }

        /**
         * One of the card's own sizes in device pixels, read from the host.
         *
         * The host reports a widget's sizes in dp as a range - what it is now and what it can be
         * dragged to - and a drawing is in pixels, so the end the caller asks for is converted with
         * the display's density. A host that reports nothing at all answers with the smallest size a
         * card can be placed at - see `hi_notes_widget_info.xml` - which is a size every version this
         * app supports reports.
         *
         * @param largest true for the end a card can be dragged to, false for the one it is at now.
         */
        private fun widgetSizePx(minOption: String, maxOption: String, largest: Boolean): Int {
            val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
            val smallest = options.getInt(minOption, 0)
            val biggest = options.getInt(maxOption, smallest)
            val dp = if (largest) maxOf(smallest, biggest) else minOf(smallest, biggest)
            val density = context.resources.displayMetrics.density
            return (dp * density).toInt().coerceAtLeast(1)
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
            views.setTextViewText(R.id.widget_chunk, chunk.text)
            views.setTextColor(R.id.widget_chunk, bodyColor)
            views.setTextViewTextSize(R.id.widget_chunk, TypedValue.COMPLEX_UNIT_SP, bodySizeSp)
            // Filled in by the template the card sets: every item opens the same note.
            views.setOnClickFillInIntent(R.id.widget_item, Intent())
            if (chunk is WidgetChunk.Task) drawTask(views, chunk)
            return views
        }

        /**
         * Draws [chunk]'s box beside its text, and makes a tap on the box tick the item.
         *
         * The box is a view of its own rather than a picture inside the text, which is what lets it
         * answer a tap without the note being opened: from Android 12 a view can carry a response of
         * its own, and this one's response is the broadcast the card's provider ticks the line with.
         * Before that the box is drawn and pressed like the rest of the item, which opens the note -
         * the item's own template is what a tap falls back to.
         *
         * The gap above and below is added to the text rather than to the row, so two task lines are
         * separated without moving the prose around them: a task line is a row of a box and a line
         * of text, and set at the line height alone the boxes of two neighbouring items touched.
         */
        private fun drawTask(views: RemoteViews, chunk: WidgetChunk.Task) {
            val drawn = boxes ?: return
            views.setViewVisibility(R.id.widget_box, View.VISIBLE)
            views.setImageViewBitmap(
                R.id.widget_box,
                if (chunk.checked) drawn.checked else drawn.unchecked,
            )
            val gap = (TASK_GAP_DP * context.resources.displayMetrics.density).toInt()
            views.setViewPadding(R.id.widget_chunk, 0, gap, 0, gap)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setOnClickResponse(R.id.widget_box, tick(chunk))
            }
        }

        /**
         * The response a tap on a task's box sends: tick the line it stands for, or clear it.
         *
         * The state is carried by the broadcast rather than read again when it arrives, because the
         * box on the card is drawn in one state and pressed in that same one: the tap means "the
         * other one".
         */
        @RequiresApi(Build.VERSION_CODES.S)
        private fun tick(chunk: WidgetChunk.Task): RemoteViews.RemoteResponse {
            val intent = Intent(context, HiNotesWidgetProvider::class.java).apply {
                action = HiNotesWidgetProvider.ACTION_TICK_TASK
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                putExtra(HiNotesWidgetProvider.EXTRA_TASK_LINE, chunk.line)
                putExtra(HiNotesWidgetProvider.EXTRA_TASK_CHECKED, !chunk.checked)
                // The box's own address, or two of them on one card would share a pending intent
                // and every one of them would tick the same line.
                data = Uri.parse("hinotes://tick/$widgetId/${chunk.line}")
            }
            val pending = PendingIntent.getBroadcast(
                context,
                chunk.line,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            return RemoteViews.RemoteResponse.fromPendingIntent(pending)
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false

        override fun onDestroy() = Unit
    }
}
