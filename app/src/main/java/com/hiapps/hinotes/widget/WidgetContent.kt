package com.hiapps.hinotes.widget

import android.content.Context
import com.hiapps.hinotes.data.Note

/**
 * What a widget shows right now.
 *
 * Three things can leave a card with nothing in it, and they ask for three different taps. Nothing
 * has been chosen for it, or the note it was bound to has since been deleted - either way the card
 * has nothing to show and the tap that would have opened its note can only offer to choose one.
 * The app can have an unlock password: a locked app's notes are not readable from the home screen,
 * so that card shows nothing at all rather than the note behind the password, and its tap has
 * nothing to choose either.
 *
 * Either way the card is still drawn, in the scheme the user picked: an empty card that follows the
 * palette is a card that belongs on the home screen, and one left in the layout's own colours is a
 * white rectangle on a dark wallpaper.
 */
internal object WidgetContent {

    /** What a card has to show, and what its tap can do. */
    sealed interface Card {

        /** The note this widget is bound to, still on disk. */
        class Shown(val note: Note) : Card

        /**
         * The app has an unlock password.
         *
         * Nothing is drawn and the card takes no tap: offering to choose a note would be offering
         * to list the titles of notes the password exists to keep off this screen.
         */
        data object Locked : Card

        /**
         * Nothing is bound, or the note that was bound has been deleted.
         *
         * @param notesAvailable whether the app holds any note at all. With none, a tap that
         *   offered the picker would open a page with nothing to pick, so it opens the app instead
         *   - which is where a note is written.
         */
        class Missing(val notesAvailable: Boolean) : Card
    }

    /** The state of [widgetId]'s card. */
    suspend fun card(context: Context, widgetId: Int): Card {
        if (WidgetRepositories.lock(context).isConfigured) return Card.Locked
        val notes = WidgetRepositories.notes(context)
        // The repository kept for widget drawing is a process-wide singleton, and its in-memory
        // list is only filled by writes made through that same instance - which is what a tick on
        // a card is. The app writes through its own repository, so reading that list here would
        // paint the note as it was at the last tick until the process restarted. Storage is the
        // one copy both writers share, so the card is decided on a fresh read of it; the note and
        // the count below then come out of the list this read just published.
        notes.refresh()
        val note = WidgetNotes.noteId(context, widgetId)?.let { notes.note(it) }
        if (note != null) return Card.Shown(note)
        return Card.Missing(notesAvailable = notes.count() > 0)
    }

    /** The note this widget should show, or null when it should show nothing. */
    suspend fun note(context: Context, widgetId: Int): Note? =
        (card(context, widgetId) as? Card.Shown)?.note
}
