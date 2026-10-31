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
 * wallpaper-derived dynamic colour is on, dark mode lifts the surfaces the app lifts, and AMOLED
 * dark flattens them.
 *
 * The card's fill is the scheme's `onSecondary`, the role Material 3 pairs with the secondary accent
 * for content drawn on it: near-white in a light scheme and a dark tinted step in a dark one, under
 * every palette. A home-screen card is not a surface inside the app's page hierarchy, so it takes
 * that scheme-defined face rather than a shade picked from the page ramp - the same role under every
 * accent, and one a wallpaper-derived scheme defines exactly as a built-in palette does. The title
 * keeps `onSurface` and the body `onSurfaceVariant`; Material 3 places those on tones 10/30 in a
 * light scheme and 90/80 in a dark one while `onSecondary` is tone 100 or 20, so the text sits at the
 * opposite end of the ramp from the fill, and the ratios are measured for every shipped palette
 * rather than assumed (see tools/verify/ThemeChecks.kt). The AMOLED treatment flattens only the
 * surface roles, so it moves neither the fill nor the text.
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
        container = scheme.onSecondary.toArgb(),
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
