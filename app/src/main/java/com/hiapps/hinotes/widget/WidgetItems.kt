package com.hiapps.hinotes.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.hiapps.hinotes.R
import com.hiapps.hinotes.data.HiNotesSettings
import com.hiapps.hinotes.data.Note
import com.hiapps.hinotes.data.NoteImages
import kotlin.math.sqrt

private const val TAG = "HiNotesWidget"

/** The card's own inset, which a picture is drawn inside rather than over. See `widget_card.xml`. */
private const val CARD_INSET_DP = 16f

/**
 * The gap a task's box is given above and below, in dp.
 *
 * A task line is 20dp of box beside a line of text, and set at the line height alone the boxes of
 * two neighbouring items meet - which is the crowding this is here to prevent. Three dp on each side
 * is the gap the app's own checklist keeps between one row and the next. It is part of the band the
 * box picture is drawn in as well as of the text's padding, so the box stands on the line's centre
 * rather than on the row's top edge.
 */
private const val TASK_GAP_DP = 3f

/**
 * How much of a card's height a picture may take.
 *
 * The rest is the note's own text: a picture is what the line of the note is about, but it is not
 * the note, and one that filled the card would put the writing about it out of sight. The band the
 * item draws is a share of the card's height rather than a scaled-down copy of the whole picture,
 * so the picture keeps the card's full width and its pixels stay square.
 */
private const val PICTURE_SHARE = 3

/** The smallest a picture's band is drawn at, however short the card is. */
private const val PICTURE_MIN_BAND_DP = 48f

/** The tallest a picture's band is drawn at, as a fraction of the card's width. */
private const val PICTURE_MAX_ASPECT = 0.625f

/**
 * How much of an item's own bytes its actions are estimated to take, beyond its text and bitmaps.
 *
 * Used only to stop an eager list before the parcel carrying it gets too big; it is generous on
 * purpose, because the cost that matters is the one that fails the update.
 */
private const val ITEM_OVERHEAD_BYTES = 256

/** How many decoded pictures the scrolling collection keeps in memory for one card. */
private const val PICTURE_CACHE_LIMIT = 12

/**
 * A note drawn as the items of a widget's list.
 *
 * The same drawing serves both halves of the card's body. On Android 12L and later the provider asks
 * for [views] and hands the whole list to the host in one `RemoteCollectionItems` update; below that
 * the collection service asks for one item at a time with [viewAt]. The two need different picture
 * budgets: an eagerly sent list travels in one parcel together with every other item and the card,
 * so its pictures are cut smaller and the list stops before that parcel gets too big, while a lazily
 * requested item has most of a binder call to itself.
 *
 * A drawing is built once and then read from whichever thread the host asks on, so the one piece of
 * mutable state it holds - the picture cache - is guarded by [pictureLock].
 */
internal class WidgetDrawing(
    private val context: Context,
    widgetId: Int,
    note: Note,
    settings: HiNotesSettings,
    private val pictureBudget: Int,
    private val pictureTotalBudget: Int,
) {

    private val style = widgetStyle(context, settings, systemDark(context))
    private val bodyColor = style.body
    private val bodySizeSp = WidgetText.bodySizeSp(settings.widgetFontScale)

    /** The gap a task line keeps above and below its text, in device pixels. */
    private val taskGapPx = (TASK_GAP_DP * context.resources.displayMetrics.density).toInt()

    /**
     * A task's box, drawn in the band one line of the body occupies plus the line's own gap.
     *
     * The picture is pinned to the top of the row (see `widget_item_*.xml`), so this band's height is
     * what stands the box on the item's first line: a long item wraps below it and the box stays on
     * the line the item starts on, rather than sliding to the middle of the paragraph.
     */
    private val boxes = WidgetBoxes.boxes(
        WidgetText.boxSizePx(context),
        WidgetText.lineHeightPx(context, bodySizeSp) + taskGapPx * 2,
        style.densityDpi,
        style,
    )
    private val readOnly = note.isLocked
    private val band = pictureBand(context, widgetId)
    // A task line becomes an item of its own only when its box can be drawn: the box is taken out of
    // the text and put beside it, and a row that lost its box would read as prose. Without the
    // pictures the characters stay in the text, as they always were.
    private val chunks = WidgetText.body(
        content = note.content,
        markdown = settings.markdownEnabled,
        drawBoxes = boxes != null,
    )
    private val pictureLock = Any()
    private val pictures = HashMap<PictureKey, Bitmap>()
    private var pictureBytes = 0

    /**
     * The item layout every item of this drawing is built from.
     *
     * One layout per type weight, because a RemoteViews cannot be handed a `Typeface`; every item of
     * one drawing uses the same one, which is what lets the host treat the list as one view type.
     */
    val layout: Int = WidgetText.bodyLayout(settings.widgetFontWeight)

    /** How many items the list has. */
    val itemCount: Int get() = chunks.size

    /** How many of them are task lines; reported by the redraw log. */
    val taskCount: Int = chunks.count { chunk -> chunk is WidgetChunk.Task }

    /** Whether this drawing has the box pictures a task line is drawn with. */
    val hasBoxes: Boolean get() = boxes != null

    /**
     * The whole list as items, for the eager Android 12L+ path.
     *
     * Everything is built here rather than as the host scrolls, because that is what
     * `RemoteCollectionItems` is: one list handed over in one update. The list therefore stops at
     * [limit] items and, before that, once its estimated cost reaches the eager budget - a truncated
     * list is a list a reader can scroll, while a parcel the binder refuses is a card that cannot be
     * drawn at all. The scrolling collection below has no such budget: it sends one item per call.
     */
    fun views(limit: Int): List<RemoteViews> {
        val count = minOf(chunks.size, limit.coerceAtLeast(0))
        val items = ArrayList<RemoteViews>(minOf(count, 64))
        var bytes = 0
        for (position in 0 until count) {
            val item = itemAt(position)
            if (items.isNotEmpty() && bytes + item.bytes > WidgetItems.EAGER_LIST_BYTES) break
            items += item.views
            bytes += item.bytes
        }
        return items
    }

    /** One item: the chunk at [position], or an item that shows nothing when the list moved on. */
    fun viewAt(position: Int): RemoteViews = itemAt(position).views

    /**
     * Draws [position]'s chunk onto a new RemoteViews.
     *
     * The drawing of one item is what may fail - a decode, a crop, a bitmap the process has no room
     * for - and a collection item that cannot be drawn must still answer with an item, so a failed
     * drawing is replaced by the item's plain text. That text is taken as characters rather than as
     * the styled text the drawing failed on, so a bad span cannot fail the fallback as well.
     */
    private fun itemAt(position: Int): Item {
        val chunk = chunks.getOrNull(position)
        if (chunk == null) return Item(WidgetItems.emptyItem(context, layout), 0)
        val views = RemoteViews(context.packageName, layout)
        val bytes = runCatching { draw(views, chunk) }.getOrElse { failure ->
            Log.w(TAG, "could not draw item $position", failure)
            runCatching { drawFallback(views, chunk) }.getOrElse { fallbackFailure ->
                Log.w(TAG, "could not draw a fallback item $position", fallbackFailure)
                textBytes(chunk.text)
            }
        }
        return Item(views, ITEM_OVERHEAD_BYTES + bytes)
    }

    /**
     * Draws [chunk], from a full and explicit state, and reports what it is estimated to cost the
     * parcel that carries an eager list.
     *
     * A collection host may re-apply an item's RemoteViews onto the frame it recycled from another
     * item, and a view the RemoteViews does not address keeps what that frame held: a picture or a
     * box left visible squeezes the text beside it, a padding left on the text moves every line, and
     * a fill-in left on a view answers a tap with the previous item's line. Every path therefore
     * starts at [reset] and then sets exactly the views it draws, whatever kind of item it is.
     */
    private fun draw(views: RemoteViews, chunk: WidgetChunk): Int {
        reset(views)
        // Filled in by the template the card sets: every item opens the same note.
        views.setOnClickFillInIntent(R.id.widget_item, Intent())
        return when (chunk) {
            is WidgetChunk.Picture -> drawPicture(views, chunk)
            is WidgetChunk.Task -> {
                drawText(views, chunk.text)
                drawTask(views, chunk)
                textBytes(chunk.text) + boxBytes()
            }
            is WidgetChunk.Text -> {
                drawText(views, chunk.text)
                textBytes(chunk.text)
            }
        }
    }

    /** The plain-text drawing of an item whose own drawing failed. */
    private fun drawFallback(views: RemoteViews, chunk: WidgetChunk): Int {
        reset(views)
        views.setOnClickFillInIntent(R.id.widget_item, Intent())
        val text = if (chunk is WidgetChunk.Picture) chunk.url else chunk.text.toString()
        drawText(views, text)
        return textBytes(text)
    }

    /**
     * Puts every view an item can draw back to hidden, and its text back to no padding.
     *
     * Which of them an item shows is decided per item; the ones it does not show are hidden here
     * rather than left to the layout, because a recycled frame keeps the visibility and the bitmap
     * it was last drawn with.
     */
    private fun reset(views: RemoteViews) {
        views.setViewVisibility(R.id.widget_chunk, View.GONE)
        views.setViewVisibility(R.id.widget_box, View.GONE)
        views.setViewVisibility(R.id.widget_picture, View.GONE)
        views.setViewPadding(R.id.widget_chunk, 0, 0, 0, 0)
    }

    /** The item's text, shown, coloured and sized the way the card's own body is. */
    private fun drawText(views: RemoteViews, text: CharSequence) {
        views.setViewVisibility(R.id.widget_chunk, View.VISIBLE)
        views.setTextViewText(R.id.widget_chunk, text)
        views.setTextColor(R.id.widget_chunk, bodyColor)
        views.setTextViewTextSize(R.id.widget_chunk, TypedValue.COMPLEX_UNIT_SP, bodySizeSp)
    }

    /**
     * Draws a picture the note holds, in the item's own view.
     *
     * A picture cannot travel in the text a widget hands the launcher - the only spans that survive
     * the trip are the platform's own `ParcelableSpan`s, and one holding a bitmap is not one of
     * those - so it is an item of its own and the bitmap goes over as an image instead.
     *
     * A picture that cannot be read leaves its address in the item, which is what the note holds and
     * what a reader can still act on.
     */
    private fun drawPicture(views: RemoteViews, chunk: WidgetChunk.Picture): Int {
        val bitmap = picture(chunk.url)
        if (bitmap == null) {
            drawText(views, chunk.url)
            return textBytes(chunk.url)
        }
        views.setViewVisibility(R.id.widget_picture, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_picture, bitmap)
        // The pixels do not count against the list's own budget: a bitmap larger than 12KB travels
        // out of line (as a shared-memory file descriptor) rather than in the parcel's bytes, and
        // what all pictures of one drawing may spend together is capped by the picture budgets
        // instead. Counting them here as well would stop an eager list after its first pictures
        // even though the parcel is nowhere near full.
        return 0
    }

    /**
     * Draws [chunk]'s box beside its text, and makes a tap on the box tick the item.
     *
     * A row cannot open a page or write to the note by itself: what a collection item has always
     * been able to do is *fill in* the card's pending intent template, so the box fills in the line
     * it stands for and the tap that reaches the app is the tick - see `WidgetTapActivity` and
     * `HiNotesWidgetProvider.tick`. The line's own text fills nothing in, so a tap on it opens the
     * note as it always has.
     *
     * A locked note's boxes are left as pictures: the app will not write to a note it cannot open,
     * and a box that offers a tick it will refuse is worse than one that does not offer it.
     *
     * The gap above and below is added to the text rather than to the row, so two task lines are
     * separated without moving the prose around them.
     */
    private fun drawTask(views: RemoteViews, chunk: WidgetChunk.Task) {
        val drawn = boxes ?: return
        views.setViewVisibility(R.id.widget_box, View.VISIBLE)
        views.setImageViewBitmap(
            R.id.widget_box,
            if (chunk.checked) drawn.checked else drawn.unchecked,
        )
        val gap = taskGapPx
        views.setViewPadding(R.id.widget_chunk, 0, gap, 0, gap)
        if (!readOnly) {
            views.setOnClickFillInIntent(R.id.widget_box, tickFillIn(chunk))
        } else {
            // A locked note offers no tick: the box fills nothing in, so a tap on it can only open
            // the note, and a tick a recycled frame still carries from an earlier drawing of the
            // card cannot fire.
            views.setOnClickFillInIntent(R.id.widget_box, Intent())
        }
    }

    /**
     * What a tap on a task's box fills the card's template in with.
     *
     * The line the box stands for, and the state the tap means - the other one, since the box is
     * drawn in the state the note records. Nothing else is carried: the template already names the
     * widget and the note it shows.
     */
    private fun tickFillIn(chunk: WidgetChunk.Task): Intent = Intent().apply {
        putExtra(HiNotesWidgetProvider.EXTRA_TASK_LINE, chunk.line)
        putExtra(HiNotesWidgetProvider.EXTRA_TASK_CHECKED, !chunk.checked)
    }

    /**
     * The picture at [url], decoded for and cut to the band the card draws it in, or null when
     * there is nothing to draw.
     *
     * The result is cached by address and by the size it was cut to, so a picture shown twice, or
     * asked for again when the list scrolls back, is not decoded twice.
     */
    private fun picture(url: String): Bitmap? {
        val size = band
        if (size.width <= 0 || size.height <= 0) return null
        val width = targetWidth(size)
        val height = targetHeight(size, width)
        if (width <= 0 || height <= 0) return null
        val key = PictureKey(url, width, height)
        synchronized(pictureLock) { pictures[key] }?.let { return it }
        val decoded = NoteImages.decode(context, url, width) ?: return null
        val banded = bandBitmap(decoded, width, height) ?: return null
        val sized = fitBudget(banded)
        // The bitmap carries the display's density so that the view draws it at the pixel size it
        // was measured at rather than scaling it by the density of the file's own metadata.
        sized.density = context.resources.displayMetrics.densityDpi
        synchronized(pictureLock) {
            // An eager list carries every picture in one parcel, so what all of them may spend
            // together is capped: a picture past the cap is drawn as its address instead. A lazily
            // requested item has its own binder call, so it only obeys the per-picture limit.
            if (pictureTotalBudget > 0 && pictureBytes + sized.byteCount > pictureTotalBudget) {
                return null
            }
            if (pictureTotalBudget <= 0 && pictures.size >= PICTURE_CACHE_LIMIT) pictures.clear()
            pictures[key] = sized
            pictureBytes += sized.byteCount
        }
        return sized
    }

    /**
     * The width a picture is decoded and drawn at.
     *
     * A card's band is as wide as the card, but a picture carried in the same parcel as every other
     * item is cut narrower first: a parcel is measured in bytes, and a band scaled down a little is
     * still the same picture. The result keeps the band's own aspect, so the view stretches it back
     * to the card's width rather than to a taller or a shorter one.
     */
    private fun targetWidth(size: PictureBand): Int {
        if (pictureBudget <= 0) return size.width
        // RGB_565 pixels are two bytes each, and the band's aspect says how many rows one column of
        // them covers, so this is the widest band that fits the budget at that aspect.
        val width = sqrt(pictureBudget.toDouble() / 2.0 * size.width / size.height).toInt()
        return width.coerceIn(1, size.width)
    }

    /** The height that keeps [width] the band's aspect; never taller than the band itself. */
    private fun targetHeight(size: PictureBand, width: Int): Int =
        (width.toLong() * size.height / size.width).toInt().coerceIn(1, size.height)

    /**
     * [bitmap] cut to a box [width] x [height] pixels, taken from its middle.
     *
     * The crop comes first, in the source's own pixels, so a tall screenshot never becomes a
     * full-height scaled bitmap on the way to a band. A picture that is shorter than the box keeps
     * its own aspect rather than being stretched to fill it, and a picture narrower than the card is
     * widened to the box's width here rather than by the view, so the item's height is the band's
     * aspect and not whatever the card's width happens to make it.
     */
    private fun bandBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap? {
        if (bitmap.width <= 0 || bitmap.height <= 0 || width <= 0 || height <= 0) return null
        // The source rows that fill a box-tall strip of the box's width. A picture with fewer rows
        // than that is not stretched: only the rows it has are used, and the strip below keeps its
        // aspect.
        val cropHeight = (bitmap.width.toLong() * height / width)
            .coerceIn(1L, bitmap.height.toLong())
            .toInt()
        val top = (bitmap.height - cropHeight) / 2
        val cut = if (cropHeight >= bitmap.height) {
            bitmap
        } else {
            // Every argument is inside the bitmap: the size was checked above and the top is the
            // one that centres a strip the bitmap is known to be tall enough to hold.
            runCatching {
                Bitmap.createBitmap(bitmap, 0, top, bitmap.width, cropHeight)
            }.getOrNull() ?: return null
        }
        if (cut !== bitmap) bitmap.recycle()
        val scaledHeight = (cut.height.toLong() * width / cut.width).toInt().coerceIn(1, height)
        if (cut.width == width && cut.height == scaledHeight) return cut
        val scaled = runCatching {
            Bitmap.createScaledBitmap(cut, width, scaledHeight, true)
        }.getOrNull() ?: return cut
        if (scaled !== cut) cut.recycle()
        return scaled
    }

    /**
     * [bitmap] made small enough for the parcel that carries it.
     *
     * The colour depth goes first: 16-bit pixels halve the bytes without losing a pixel, and a
     * picture's detail is what the eye reads on a home screen. A picture still over the budget is
     * scaled down until it fits, which is the only case where the card shows it softer than the
     * pixels it was decoded at.
     */
    private fun fitBudget(bitmap: Bitmap): Bitmap {
        if (pictureBudget <= 0 || bitmap.byteCount <= pictureBudget) return bitmap
        val smaller = runCatching { bitmap.copy(Bitmap.Config.RGB_565, false) }.getOrNull()
        if (smaller != null) {
            bitmap.recycle()
            if (smaller.byteCount <= pictureBudget) return smaller
            return scaledDown(smaller)
        }
        return scaledDown(bitmap)
    }

    /**
     * [bitmap] scaled by the factor that brings its bytes within the picture budget.
     *
     * The sides are rounded down rather than to the nearest pixel: a bitmap a few bytes over the
     * budget is a parcel the binder refuses, and a pixel less cannot be seen.
     */
    private fun scaledDown(bitmap: Bitmap): Bitmap {
        val bytes = bitmap.byteCount
        if (pictureBudget <= 0 || bytes <= pictureBudget) return bitmap
        val factor = sqrt(pictureBudget.toDouble() / bytes)
        val width = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).toInt().coerceAtLeast(1)
        val scaled = runCatching {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }.getOrNull() ?: return bitmap
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    /** An item built from this drawing, and its share of what an eager list may cost. */
    private class Item(val views: RemoteViews, val bytes: Int)

    /** The bytes a run of text is estimated to take in the parcel an eager list travels in. */
    private fun textBytes(text: CharSequence): Int = text.length * 2

    /** The bytes one box picture takes in that parcel; every task item carries one. */
    private fun boxBytes(): Int = boxes?.checked?.byteCount ?: 0
}

/**
 * A picture's place in a card: the width it is drawn at, and the height of the band.
 */
private data class PictureBand(val width: Int, val height: Int)

/** One decoded picture and the size it was cut to, as the drawing's picture cache is keyed. */
private data class PictureKey(val url: String, val width: Int, val height: Int)

/**
 * The card's body as items, for whichever of the platform's two collection paths is in use.
 *
 * A collection is a list the launcher builds from what the app hands it, and the app has two ways to
 * hand it over: the deprecated `RemoteViewsService` intent, whose items are requested lazily as they
 * come into view, and `RemoteCollectionItems` (API 31, used from API 32 here because API 31's first
 * version of it is the one the platform's own compatibility library avoids), where the whole list is
 * built and sent in the card's own update. The same [WidgetDrawing] builds both, so an item looks the
 * same whichever path draws it; what differs is the budget a picture is allowed, because on the eager
 * path every item and the card share one parcel.
 */
internal object WidgetItems {

    /** The most items an eager list embeds; the scrolling collection below 12L shows all. */
    const val EAGER_ITEM_LIMIT = 200

    /**
     * The most bytes one picture may take in an eager list, and the most all of them together may.
     *
     * Pixels are not what fills a parcel - a bitmap over 12KB travels out of line - but every
     * picture of an eager list is decoded and kept by the launcher at once, and all of them share
     * the card's own bitmap allowance with the title and the boxes. A full-width band in 16-bit
     * colour comes to a little under 200KB at a phone's density, so the per-picture limit is set
     * just above one band (no scaling at the usual card width) and the total a little above what a
     * note with a handful of pictures needs; a picture past the total is drawn as its address,
     * which is what the note holds anyway.
     *
     * A legacy item below Android 12L has its own binder call and keeps its own, larger limit.
     */
    private const val EAGER_PICTURE_BYTES = 256 * 1024
    private const val EAGER_PICTURE_TOTAL_BYTES = 1536 * 1024

    /**
     * The most an eager list's items may be estimated to cost the parcel that carries them.
     *
     * Text and actions are what fill the parcel: a picture travels out of line and is bounded by the
     * picture budgets above, and a task item's box bitmap is about 10KB at a phone's density. A long
     * checklist is therefore what makes the parcel big, and the list stops before the estimate
     * reaches this, which leaves the card and the binder's own overhead room under the megabyte a
     * transaction has. The 37-task note the widget bug was reported with comes to about 370KB and is
     * not truncated.
     */
    const val EAGER_LIST_BYTES = 640 * 1024

    /**
     * The most bytes one picture may take when its item is its own binder call (below 12L).
     *
     * Half of what a transaction can carry, which leaves room for the item's text and actions in the
     * same parcel; a band at a phone's card width fits at full resolution within it.
     */
    private const val COLLECTION_PICTURE_BYTES = 256 * 1024

    /**
     * The note's body as the whole list of items an Android 12L+ host draws eagerly.
     *
     * Synchronous, and it reads and decodes files, so it belongs on a background thread. A drawing
     * that cannot be built is reported and answered with an empty list rather than an exception: the
     * card is still drawn, with nothing in its body, instead of failing to update at all.
     */
    fun list(
        context: Context,
        widgetId: Int,
        note: Note,
        settings: HiNotesSettings,
    ): List<RemoteViews> {
        val drawing = runCatching {
            WidgetDrawing(
                context = context,
                widgetId = widgetId,
                note = note,
                settings = settings,
                pictureBudget = EAGER_PICTURE_BYTES,
                pictureTotalBudget = EAGER_PICTURE_TOTAL_BYTES,
            )
        }.onFailure { failure ->
            Log.w(TAG, "could not read note for widget $widgetId", failure)
        }.getOrNull() ?: return emptyList()
        val items = runCatching { drawing.views(EAGER_ITEM_LIMIT) }.onFailure { failure ->
            Log.w(TAG, "could not draw the eager list for widget $widgetId", failure)
        }.getOrNull() ?: return emptyList()
        Log.i(TAG, "widget $widgetId eagerly: ${items.size} items, ${drawing.taskCount} tasks")
        return items
    }

    /**
     * The drawing the scrolling collection asks its items from, below Android 12L.
     *
     * A failure building it is the caller's to log and to answer with an empty list: this path is a
     * `RemoteViewsFactory`, and the platform takes a throw from one to the app's uncaught-exception
     * handler.
     */
    fun collection(
        context: Context,
        widgetId: Int,
        note: Note,
        settings: HiNotesSettings,
    ): WidgetDrawing = WidgetDrawing(
        context = context,
        widgetId = widgetId,
        note = note,
        settings = settings,
        pictureBudget = COLLECTION_PICTURE_BYTES,
        pictureTotalBudget = 0,
    )

    /**
     * An item that shows nothing.
     *
     * Used for a position the list no longer has, and as the last answer when even the plain-text
     * drawing of an item failed: every view is hidden explicitly, so a frame the host recycled
     * loses whatever it was showing rather than showing it again under another item's number.
     */
    fun emptyItem(context: Context, layout: Int): RemoteViews =
        RemoteViews(context.packageName, layout).also { views ->
            views.setViewVisibility(R.id.widget_chunk, View.GONE)
            views.setViewVisibility(R.id.widget_box, View.GONE)
            views.setViewVisibility(R.id.widget_picture, View.GONE)
        }
}

/**
 * The band a picture is drawn in: the card's own width, and a share of its height.
 *
 * The host reports a card's sizes in dp as a range; the smaller end of each is the size the card is
 * at now, and a card that is dragged larger reports again - which redraws it and re-reads this. A
 * host that reports nothing at all - every version this app supports does report, but a host is not
 * obliged to - falls back to the display's own width and a fraction of its height, which keeps a
 * picture visible rather than a few pixels tall.
 */
private fun pictureBand(context: Context, widgetId: Int): PictureBand {
    val display = context.resources.displayMetrics
    val density = display.density
    val inset = (CARD_INSET_DP * density).toInt()
    val cardWidth = widgetSizePx(
        context = context,
        widgetId = widgetId,
        minOption = AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
        maxOption = AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
        largest = false,
    )
    // A host can answer with a size too small to hold the card's own insets before it has measured
    // the card; that answer is treated as no answer rather than handed to the decoder as a width.
    val reported = if (cardWidth > 1) cardWidth - inset * 2 else 0
    val width = (if (reported > 0) reported else display.widthPixels - inset * 2).coerceAtLeast(1)
    val cardHeight = widgetSizePx(
        context = context,
        widgetId = widgetId,
        minOption = AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
        maxOption = AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
        largest = false,
    )
    val room = if (cardHeight > 1) cardHeight else display.heightPixels / 8
    val tallest = (width * PICTURE_MAX_ASPECT).toInt().coerceAtLeast(1)
    val height = (room / PICTURE_SHARE)
        .coerceAtLeast((PICTURE_MIN_BAND_DP * density).toInt())
        .coerceAtMost(tallest)
    return PictureBand(width, height.coerceAtLeast(1))
}

/**
 * One of the card's own sizes in device pixels, read from the host.
 *
 * The host reports a widget's sizes in dp as a range - what it is now and what it can be dragged to -
 * and a drawing is in pixels, so the end the caller asks for is converted with the display's
 * density.
 *
 * @param largest true for the end a card can be dragged to, false for the one it is at now.
 */
private fun widgetSizePx(
    context: Context,
    widgetId: Int,
    minOption: String,
    maxOption: String,
    largest: Boolean,
): Int {
    val options = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetOptions(widgetId)
    }.getOrNull()
    val smallest = options?.getInt(minOption, 0) ?: 0
    val biggest = options?.getInt(maxOption, smallest) ?: smallest
    val dp = if (largest) maxOf(smallest, biggest) else minOf(smallest, biggest)
    val density = context.resources.displayMetrics.density
    return (dp * density).toInt()
}
