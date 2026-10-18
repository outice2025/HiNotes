package com.hiapps.hinotes.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.roundToInt

/**
 * A task's box, drawn as the picture the widget's spanned text carries.
 *
 * The body of a note writes a task as the `☑`/`☐` characters, which are whatever the system font
 * happens to draw them as; the app itself draws a Material 3 checkbox, so a task in a note reads
 * differently on the home screen than it does in the editor. A `Spanned` may hold a picture in the
 * middle of its text, so the two forms of that checkbox are rasterised from the app's own geometry
 * - an 18dp rounded square, filled with the scheme's `primary` and carrying an `onPrimary` tick when
 * the item is done, outlined in `onSurfaceVariant` when it is not - and the glyph is swapped for one
 * of them.
 *
 * A bitmap rather than a vector drawable, because the span is parcelled: the widget's text is
 * written into a RemoteViews and read back in the launcher's process, and only a `Bitmap` is written
 * out whole. A `VectorDrawable` span would arrive there as a resource id alone, which the launcher
 * cannot resolve the widget's own drawing from, and the box would come out blank.
 *
 * Each picture is stamped with the display's density as it is built. A bitmap is created at 160dpi
 * whatever screen it is meant for, and the `BitmapDrawable` behind an `ImageSpan` scales what it is
 * given by the display's density over the bitmap's own - so a box left at the density it was created
 * with is drawn at the display's density as a scale factor on top of a size already worked out in
 * that display's pixels, and the box comes out almost three times its size on a 440dpi phone.
 *
 * The pictures are cached per size, density and colour. A drawing pass rebuilds the whole body, so a
 * card holding a checklist would otherwise allocate one bitmap per box; the cache is keyed by
 * everything the drawing depends on, so a card redrawn at another type size or in another scheme
 * paints the boxes that size and in those colours rather than the ones it drew last time.
 */
internal object WidgetBoxes {

    /** The box's own side, in dp, which is the size Material 3 draws a checkbox at. */
    private const val BOX_DP = 18f

    /**
     * The side of the box's rounded corner and the width of an outline, as fractions of the box.
     *
     * Both are the proportions the app's own checkbox uses: an 18dp box has a 2dp radius and a 2dp
     * outline, and keeping them as fractions is what lets the box follow the widget's type size.
     */
    private const val CORNER = 2f / BOX_DP
    private const val OUTLINE = 2f / BOX_DP

    /**
     * The tick, as fractions of the box.
     *
     * Three points on a path: the short stroke starts at [TICK_LEFT] and [TICK_KNEE], comes down to
     * the joint at [TICK_TURN_X] and [TICK_TURN_Y], and the long one rises from there to
     * [TICK_RIGHT] and [TICK_TOP]. Every point is kept inside the box by more than the stroke's own
     * half-width, so the round cap that finishes it is drawn whole.
     */
    private const val TICK = 2f / BOX_DP
    private const val TICK_LEFT = 0.22f
    private const val TICK_TURN_X = 0.42f
    private const val TICK_TURN_Y = 0.70f
    private const val TICK_RIGHT = 0.80f
    private const val TICK_TOP = 0.28f
    private const val TICK_KNEE = 0.50f

    /** The two pictures one card draws, at one size and in one set of colours. */
    data class Boxes(val checked: Bitmap, val unchecked: Bitmap)

    private val cache = mutableMapOf<Key, Boxes>()

    private data class Key(
        val size: Int,
        val density: Int,
        val fill: Int,
        val tick: Int,
        val outline: Int,
    )

    /**
     * The box pictures for [colors], drawn [sizePx] square and stamped with [density].
     *
     * The density is the display's own, in dots per inch, and it is part of what the pictures are
     * rather than a detail of the drawing: see the note at the top of this file for why a box handed
     * to a span at any other density is drawn at the wrong size. It is passed in rather than read
     * here because the caller is the one that resolved the style the colours come from, and that
     * resolution is where the display is known.
     *
     * Null when either picture cannot be built - a zero-sized box, a bitmap the process has no room
     * for - because a caller that gets one still has the character the note was rendered with, and a
     * note must never lose its text to a picture that failed.
     */
    fun boxes(sizePx: Int, density: Int, colors: WidgetStyle): Boxes? {
        if (sizePx < 1) return null
        val key = Key(sizePx, density, colors.boxFill, colors.boxTick, colors.boxOutline)
        cache[key]?.let { return it }
        val built = runCatching {
            Boxes(
                checked = square(
                    size = sizePx,
                    density = density,
                    fill = colors.boxFill,
                    tick = colors.boxTick,
                    outline = colors.boxOutline,
                    checked = true,
                ),
                unchecked = square(
                    size = sizePx,
                    density = density,
                    fill = colors.boxFill,
                    tick = colors.boxTick,
                    outline = colors.boxOutline,
                    checked = false,
                ),
            )
        }.getOrNull() ?: return null
        cache[key] = built
        return built
    }

    /** The box's side in device pixels at [sizeDp], which is the size its bitmap is drawn at. */
    fun sizePx(context: Context, sizeDp: Float): Int =
        (sizeDp * context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1)

    /**
     * One box: the square, and the tick inside it when the item is done.
     *
     * The bitmap is stamped with [density] as it is built, because a span scales it by that density:
     * see the note at the top of this file.
     */
    private fun square(
        size: Int,
        density: Int,
        fill: Int,
        tick: Int,
        outline: Int,
        checked: Boolean,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.density = density
        val canvas = Canvas(bitmap)
        val side = size.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        if (checked) {
            // A filled box, with the tick drawn on top of it in the colour that belongs there.
            paint.style = Paint.Style.FILL
            paint.color = fill
            canvas.drawRoundRect(RectF(0f, 0f, side, side), side * CORNER, side * CORNER, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = side * TICK
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = tick
            val tickPath = Path().apply {
                moveTo(side * TICK_LEFT, side * TICK_KNEE)
                lineTo(side * TICK_TURN_X, side * TICK_TURN_Y)
                lineTo(side * TICK_RIGHT, side * TICK_TOP)
            }
            canvas.drawPath(tickPath, paint)
        } else {
            // An outlined box: the stroke is drawn inside the edge, so nothing is clipped by it.
            val inset = side * OUTLINE / 2f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = side * OUTLINE
            paint.color = outline
            canvas.drawRoundRect(
                RectF(inset, inset, side - inset, side - inset),
                side * CORNER,
                side * CORNER,
                paint,
            )
        }
        return bitmap
    }
}
