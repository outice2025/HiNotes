package com.hiapps.hinotes.widget

import android.content.Context

/**
 * Which note each placed widget shows.
 *
 * The launcher owns widget ids, so the mapping is kept beside them rather than in the app's
 * settings: one short entry per widget, written when the widget is configured and dropped when the
 * host removes it from the screen. A preferences file of its own is enough for that, and it keeps
 * launcher state out of the settings the user can export and restore.
 */
internal object WidgetNotes {

    private const val FILE = "hinotes_widgets"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The note this widget shows, or null while it has not been configured. */
    fun noteId(context: Context, widgetId: Int): String? =
        prefs(context).getString(widgetId.toString(), null)

    /**
     * Points [widgetId] at [noteId].
     *
     * Written synchronously: the configuration screen finishes as soon as this returns and the host
     * then places the widget and asks for its first drawing, so the binding has to be readable by
     * then rather than merely queued.
     */
    fun bind(context: Context, widgetId: Int, noteId: String) {
        prefs(context).edit().putString(widgetId.toString(), noteId).commit()
    }

    /** Forgets [widgetId], called when the host removes the widget. */
    fun unbind(context: Context, widgetId: Int) {
        prefs(context).edit().remove(widgetId.toString()).apply()
    }

    /** The placed widgets showing [noteId], so only those are redrawn when that note changes. */
    fun widgetsShowing(context: Context, noteId: String): List<Int> =
        prefs(context).all
            .filterValues { it == noteId }
            .keys
            .mapNotNull { it.toIntOrNull() }
}
