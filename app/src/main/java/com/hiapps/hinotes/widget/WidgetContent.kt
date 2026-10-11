package com.hiapps.hinotes.widget

import android.content.Context
import com.hiapps.hinotes.data.Note

/**
 * What a widget shows right now.
 *
 * Two things can leave a card with nothing in it. Nothing has been chosen for it - the host may
 * place a widget before the picker is answered, and a note that has since been deleted takes its
 * binding with it. And the app can have an unlock password: a locked app's notes are not readable
 * from the home screen, so that card shows nothing at all rather than the note behind the password.
 *
 * Either way the card is still drawn, in the scheme the user picked: an empty card that follows the
 * palette is a card that belongs on the home screen, and one left in the layout's own colours is a
 * white rectangle on a dark wallpaper.
 */
internal object WidgetContent {

    /** The note this widget should show, or null when it should show nothing. */
    suspend fun note(context: Context, widgetId: Int): Note? {
        if (WidgetRepositories.lock(context).isConfigured) return null
        val noteId = WidgetNotes.noteId(context, widgetId) ?: return null
        return WidgetRepositories.notes(context).note(noteId)
    }
}
