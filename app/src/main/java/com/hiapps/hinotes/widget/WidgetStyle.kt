package com.hiapps.hinotes.widget

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.hiapps.hinotes.data.HiNotesSettings
import com.hiapps.hinotes.ui.theme.liftDarkSurfaces
import com.hiapps.hinotes.ui.theme.toOled

/**
 * The colours one widget paints with, resolved to ARGB because a RemoteViews cannot hold a theme.
 *
 * The display's density travels with them because the widget's own pictures cannot be scaled the way
 * its text can: a box is rasterised at this density so that the span holding it draws it at the pixel
 * size it was measured at, rather than at the size the launcher's own density would scale it to. The
 * style is where it is resolved because the scheme's colours are resolved there too, and a widget's
 * context is what knows its display.
 */
internal data class WidgetStyle(
    val container: Int,
    val title: Int,
    val body: Int,
    val boxFill: Int,
    val boxTick: Int,
    val boxOutline: Int,
    val densityDpi: Int,
)

/**
 * Resolves a widget's colours exactly the way the app resolves its own.
 *
 * A widget is drawn by the launcher from a RemoteViews, so it cannot read the Compose theme; the
 * same pipeline is therefore run here in plain code. The accent palette supplies the scheme unless
 * wallpaper-derived dynamic colour is on, dark mode lifts the surfaces the app lifts, and OLED dark
 * flattens them.
 *
 * The surface is whichever of the scheme's surface roles sits nearest the clock widget's dial - see
 * [widgetSurface], and the two dial colours it is measured against - rather than the
 * `surfaceContainerLow` an app card uses, because the card has to sit on the home screen beside that
 * widget rather than inside the app. The role that wins is the palette's own answer rather than a
 * fixed one: in light
 * mode the surface ramp is near-white in every accent and the dial is a pale blue-white, so the
 * match lands on `surfaceContainer` for the default Blue accent and on a neighbouring role for
 * another accent - `surfaceContainerLow` under the green tint, `surface` under the warm ones - while
 * in dark mode `surfaceContainerHighest` wins under every accent, because that is the lightest slate
 * each palette has. None of the differences is large: the whole ramp sits within a few steps of the
 * dial, which is what a card placed beside that widget needs. An OLED panel flattens every surface
 * to black, so black is what its card is drawn in. The title keeps `onSurface` and the body
 * `onSurfaceVariant`, so the text stays legible whatever surface is chosen.
 *
 * A task's box is drawn from the app's own checkbox roles - `primary` filled with `onPrimary` on it
 * when the item is done, and `onSurfaceVariant` as the outline when it is not - so a checklist on
 * the home screen reads the way it does in the editor.
 */
internal fun widgetStyle(
    context: Context,
    settings: HiNotesSettings,
    systemDark: Boolean,
): WidgetStyle {
    val dark = settings.darkMode.isDark(systemDark)
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = if (settings.dynamicColor && supportsDynamic) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        settings.accentPalette.scheme(dark)
    }
    val lifted = if (dark) base.liftDarkSurfaces() else base
    val scheme = if (dark && settings.oledDark) lifted.toOled() else lifted
    return WidgetStyle(
        container = widgetSurface(scheme, dark).toArgb(),
        title = scheme.onSurface.toArgb(),
        body = scheme.onSurfaceVariant.toArgb(),
        boxFill = scheme.primary.toArgb(),
        boxTick = scheme.onPrimary.toArgb(),
        boxOutline = scheme.onSurfaceVariant.toArgb(),
        densityDpi = context.resources.displayMetrics.densityDpi,
    )
}

/** True while the device itself is in dark mode; the app's own preference decides what to do. */
internal fun systemDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
