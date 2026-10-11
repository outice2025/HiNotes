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

/** The colours one widget paints with, resolved to ARGB because a RemoteViews cannot hold a theme. */
internal data class WidgetStyle(
    val container: Int,
    val title: Int,
    val body: Int,
)

/**
 * Resolves a widget's colours exactly the way the app resolves its own.
 *
 * A widget is drawn by the launcher from a RemoteViews, so it cannot read the Compose theme; the
 * same pipeline is therefore run here in plain code. The accent palette supplies the scheme unless
 * wallpaper-derived dynamic colour is on, dark mode lifts the surfaces the app lifts, OLED dark
 * flattens them, and the roles are the ones an app card uses: a `surfaceContainerLow` surface - the
 * same one a note card is drawn on in the app, in both modes, so the widget and the card it stands
 * in for are the same colour - with `onSurface` for the title and `onSurfaceVariant` for the body.
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
        container = scheme.surfaceContainerLow.toArgb(),
        title = scheme.onSurface.toArgb(),
        body = scheme.onSurfaceVariant.toArgb(),
    )
}

/** True while the device itself is in dark mode; the app's own preference decides what to do. */
internal fun systemDark(context: Context): Boolean =
    context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
