package com.hiapps.hinotes.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * A task's box, drawn as the picture the widget's list shows beside a task line.
 *
 * The app itself draws a Material 3 checkbox, so a task in a note has to read the same on the home
 * screen: both forms of that checkbox are therefore rasterised from the same three numbers the
 * composed one is drawn with - a 20dp square, a 2dp corner and a 2dp outline - filled with the
 * scheme's `primary` and carrying an `onPrimary` tick when the item is done, and outlined in
 * `onSurfaceVariant` when it is not.
 *
 * The picture is taller than the square: it stands in the band one line of the note's text occupies,
 * with the box centred in that band. A row's picture is pinned to the row's top - a box in the middle
 * of a wrapped item is a box beside a line the reader has not begun to tick (see the item layouts) -
 * so the band's height is what puts the box on the item's *first* line. A one-line task and a
 * ten-line one therefore show their boxes at the same height, and the row's own text padding is
 * included in the band so the box meets the line's centre rather than the row's edge.
 *
 * The tick is Material 3's own path, not a shape of this file's: it runs from 20% across the box at
 * half its height, down to 40% across at 70% of it, and up to 80% across at 30% of it, struck with
 * the same 2dp stroke and square ends the composed checkbox uses. Anything else was a box that only
 * looked a little like a checkbox, and beside a card's real one it read as a second style.
 *
 * A bitmap rather than a vector drawable, because the picture crosses two process boundaries: the
 * item is built in this app and drawn by the launcher, and a `Bitmap` is the one drawing that is
 * written out whole in that parcel. A vector would arrive as a resource id, which the launcher cannot
 * resolve against this app's own resources, and the box would come out blank.
 *
 * Each picture is stamped with the display's density as it is built: a bitmap is created at 160dpi
 * whatever screen it is meant for, and a view draws it scaled by the display's density over the
 * bitmap's own - so a box left at the density it was created with is drawn at the display's density
 * as a scale factor on top of a size already worked out in that display's pixels, and the box comes
 * out almost three times its size on a 440dpi phone.
 *
 * The pictures are cached per size, band height, density and colour. A drawing pass builds the body
 * of a note that may hold many boxes, and a card redrawn in another scheme has to paint the boxes in
 * those colours rather than the ones it drew last time.
 */
internal object WidgetBoxes {

    /**
     * The side of the box's rounded corner and the width of its outline, in dp.
     *
     * These are the lengths Material 3 draws a checkbox with: the composed box is a 20dp square with
     * a 2dp corner and a 2dp stroke, and both are lengths rather than fractions of the box for the
     * same reason they are in the app - a box is not meant to grow with the type around it.
     */
    private const val CORNER_DP = 2f
    private const val OUTLINE_DP = 2f

    /**
     * The tick, as fractions of the box, straight out of Material 3's checkbox: the short stroke
     * starts at [TICK_LEFT] and [TICK_KNEE], comes down to the joint at [TICK_TURN_X] and
     * [TICK_TURN_Y], and the long one rises from there to [TICK_RIGHT] and [TICK_TOP].
     */
    private const val TICK_LEFT = 0.2f
    private const val TICK_KNEE = 0.5f
    private const val TICK_TURN_X = 0.4f
    private const val TICK_TURN_Y = 0.7f
    private const val TICK_RIGHT = 0.8f
    private const val TICK_TOP = 0.3f

    /** The two pictures one card draws, at one size and in one set of colours. */
    data class Boxes(val checked: Bitmap, val unchecked: Bitmap)

    /**
     * How many drawings the cache keeps.
     *
     * One card needs one of them; the rest are the previous schemes a card has been drawn in, which
     * are kept because a redraw back to one of them should not paint the boxes again. The cache is
     * emptied rather than grown when it fills: what it holds is only ever a cache, and a picture in
     * a parcel that has not been sent yet must never be recycled - dropping the reference is what
     * lets the collector have it.
     */
    private const val CACHE_LIMIT = 8

    /**
     * The drawings already made, shared by every widget in the process.
     *
     * Two widgets' factories can be asked for their items at the same time, on different threads, so
     * every read and write of the cache is guarded. The drawing itself happens outside the lock: it
     * allocates two bitmaps, and a cache miss building the same boxes twice is cheaper than holding
     * the other widget's thread for it.
     */
    private val cache = mutableMapOf<Key, Boxes>()

    private data class Key(
        val size: Int,
        val band: Int,
        val density: Int,
        val fill: Int,
        val tick: Int,
        val outline: Int,
    )

    /**
     * The box pictures for [colors], drawn [sizePx] square inside a band [bandPx] tall and stamped
     * with [density].
     *
     * The band is the height of one line of the note's body including the gap a task line keeps above
     * and below it, so a picture pinned to a row's top has its square centred on the first line: see
     * the note at the top of this file. A band shorter than the square is grown to it - a picture
     * that would clip the box is worse than a box that stands a hair below the line's centre.
     *
     * The density is the display's own, in dots per inch, and it is part of what the pictures are
     * rather than a detail of the drawing: see the note at the top of this file for why a box handed
     * to a span at any other density is drawn at the wrong size. It is also what turns the 2dp
     * corner and stroke into pixels: Material 3 resolves its own stroke to whole pixels before it
     * draws, and doing the same keeps the two outlines the same width.
     *
     * Null when either picture cannot be built - a zero-sized box, a bitmap the process has no room
     * for - because a caller that gets one still has the character the note was rendered with, and a
     * note must never lose its text to a picture that failed.
     */
    fun boxes(sizePx: Int, bandPx: Int, density: Int, colors: WidgetStyle): Boxes? {
        if (sizePx < 1) return null
        val band = bandPx.coerceAtLeast(sizePx)
        val key = Key(sizePx, band, density, colors.boxFill, colors.boxTick, colors.boxOutline)
        synchronized(cache) { cache[key] }?.let { return it }
        val built = runCatching {
            Boxes(
                checked = square(
                    size = sizePx,
                    band = band,
                    density = density,
                    fill = colors.boxFill,
                    tick = colors.boxTick,
                    outline = colors.boxOutline,
                    checked = true,
                ),
                unchecked = square(
                    size = sizePx,
                    band = band,
                    density = density,
                    fill = colors.boxFill,
                    tick = colors.boxTick,
                    outline = colors.boxOutline,
                    checked = false,
                ),
            )
        }.getOrNull() ?: return null
        synchronized(cache) {
            if (cache.size >= CACHE_LIMIT) cache.clear()
            cache[key] = built
        }
        return built
    }

    /** The box's side in device pixels at [sizeDp], which is the size its bitmap is drawn at. */
    fun sizePx(context: Context, sizeDp: Float): Int =
        (sizeDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    /**
     * One box: the square, and the tick inside it when the item is done.
     *
     * The bitmap is [band] pixels tall and the square sits in its middle, so the view that draws it
     * from the top of a row puts the box on the row's first line rather than at the row's top edge.
     * The bitmap is stamped with [density] as it is built, because a view draws it by that density:
     * see the note at the top of this file.
     */
    private fun square(
        size: Int,
        band: Int,
        density: Int,
        fill: Int,
        tick: Int,
        outline: Int,
        checked: Boolean,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, band, Bitmap.Config.ARGB_8888)
        bitmap.density = density
        val canvas = Canvas(bitmap)
        // Everything below is written in the square's own coordinates; the band only decides where
        // the square starts inside the picture.
        canvas.translate(0f, (band - size) / 2f)
        val side = size.toFloat()
        val stroke = floor(OUTLINE_DP * density / 160f).coerceAtLeast(1f)
        val corner = CORNER_DP * density / 160f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (checked) {
            // A filled square, with the tick drawn on top of it in the colour that belongs there.
            paint.style = Paint.Style.FILL
            paint.color = fill
            canvas.drawRoundRect(RectF(0f, 0f, side, side), corner, corner, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            // Material 3 draws the tick with square ends and meets the two strokes in a plain
            // joint; a rounded end here reads as a heavier, softer tick beside the composed box.
            paint.strokeCap = Paint.Cap.SQUARE
            paint.strokeJoin = Paint.Join.MITER
            paint.color = tick
            val tickPath = Path().apply {
                moveTo(side * TICK_LEFT, side * TICK_KNEE)
                lineTo(side * TICK_TURN_X, side * TICK_TURN_Y)
                lineTo(side * TICK_RIGHT, side * TICK_TOP)
            }
            canvas.drawPath(tickPath, paint)
        } else {
            // An outlined square. The stroke sits inside the box's edge, exactly as Material 3
            // places it: a round rectangle inset by half the stroke, so its outer edge is the box's
            // own outline rather than a line straddling it.
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = stroke
            paint.color = outline
            val half = stroke / 2f
            canvas.drawRoundRect(
                RectF(half, half, side - half, side - half),
                (corner - half).coerceAtLeast(0f),
                (corner - half).coerceAtLeast(0f),
                paint,
            )
        }
        return bitmap
    }
}
