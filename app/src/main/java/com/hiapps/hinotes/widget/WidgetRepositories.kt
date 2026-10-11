package com.hiapps.hinotes.widget

import android.content.Context
import com.hiapps.hinotes.data.LockStore
import com.hiapps.hinotes.data.NotesRepository
import com.hiapps.hinotes.data.SettingsRepository

/**
 * The readers a widget draws from.
 *
 * A widget is not drawn by an activity, so there is no view model to borrow a repository from. One
 * of each is kept here instead of one per redraw: a `NotesRepository` builds a `SQLiteOpenHelper`,
 * and a redraw happens every time a note is saved or a setting changes - so a fresh pair each time
 * would leave a trail of helpers and connections for the collector to find.
 *
 * The application context is what is held, so nothing here outlives the process, and both classes
 * are thin wrappers around the database and the settings store rather than state of their own.
 */
internal object WidgetRepositories {

    @Volatile private var notesRepository: NotesRepository? = null
    @Volatile private var settingsRepository: SettingsRepository? = null
    @Volatile private var lockStore: LockStore? = null

    fun notes(context: Context): NotesRepository = notesRepository ?: synchronized(this) {
        notesRepository ?: NotesRepository(context.applicationContext).also { notesRepository = it }
    }

    fun settings(context: Context): SettingsRepository =
        settingsRepository ?: synchronized(this) {
            settingsRepository ?: SettingsRepository(context.applicationContext)
                .also { settingsRepository = it }
        }

    fun lock(context: Context): LockStore = lockStore ?: synchronized(this) {
        lockStore ?: LockStore(context.applicationContext).also { lockStore = it }
    }
}
