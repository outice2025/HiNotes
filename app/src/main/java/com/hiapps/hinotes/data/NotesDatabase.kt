package com.hiapps.hinotes.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite schema for notes.
 *
 * Notes are stored locally only; nothing leaves the device. The database is a plain
 * [SQLiteOpenHelper] store so note persistence has no annotation-processing or
 * plugin-version coupling in the build.
 */
internal class NotesDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                $COL_ID TEXT PRIMARY KEY NOT NULL,
                $COL_TITLE TEXT NOT NULL DEFAULT '',
                $COL_CONTENT TEXT NOT NULL DEFAULT '',
                $COL_LOCKED INTEGER NOT NULL DEFAULT 0,
                $COL_CREATED INTEGER NOT NULL,
                $COL_UPDATED INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_notes_updated ON $TABLE ($COL_UPDATED DESC)")
        db.execSQL("CREATE INDEX idx_notes_created ON $TABLE ($COL_CREATED DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Notes are the user's data: never drop them on upgrade.
        if (oldVersion < 2 && !hasColumn(db, COL_LOCKED)) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $COL_LOCKED INTEGER NOT NULL DEFAULT 0")
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    private fun hasColumn(db: SQLiteDatabase, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($TABLE)", null).use { c ->
            val nameIndex = c.getColumnIndex("name")
            while (c.moveToNext()) {
                if (nameIndex >= 0 && c.getString(nameIndex) == column) return true
            }
            false
        }

    fun upsert(note: Note) {
        val values = ContentValues().apply {
            put(COL_ID, note.id)
            put(COL_TITLE, note.title)
            put(COL_CONTENT, note.content)
            put(COL_LOCKED, if (note.isLocked) 1 else 0)
            put(COL_CREATED, note.createdAt)
            put(COL_UPDATED, note.updatedAt)
        }
        writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun delete(id: String) {
        writableDatabase.delete(TABLE, "$COL_ID = ?", arrayOf(id))
    }

    fun deleteAll() {
        writableDatabase.delete(TABLE, null, null)
    }

    fun all(): List<Note> =
        readableDatabase.query(
            TABLE, null, null, null, null, null, "$COL_UPDATED DESC",
        ).use { c -> c.toNotes() }

    fun byId(id: String): Note? =
        readableDatabase.query(
            TABLE, null, "$COL_ID = ?", arrayOf(id), null, null, null, "1",
        ).use { c -> c.toNotes().firstOrNull() }

    fun search(query: String): List<Note> {
        val like = "%${query.replace("%", "\\%").replace("_", "\\_")}%"
        return readableDatabase.query(
            TABLE,
            null,
            "($COL_TITLE LIKE ? ESCAPE '\\' OR $COL_CONTENT LIKE ? ESCAPE '\\')",
            arrayOf(like, like),
            null,
            null,
            "$COL_UPDATED DESC",
        ).use { c -> c.toNotes() }
    }

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    /** Replaces the entire contents of the table in one transaction. */
    fun replaceAll(notes: List<Note>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE, null, null)
            notes.forEach { note ->
                val values = ContentValues().apply {
                    put(COL_ID, note.id)
                    put(COL_TITLE, note.title)
                    put(COL_CONTENT, note.content)
                    put(COL_LOCKED, if (note.isLocked) 1 else 0)
                    put(COL_CREATED, note.createdAt)
                    put(COL_UPDATED, note.updatedAt)
                }
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun android.database.Cursor.toNotes(): List<Note> {
        val idIndex = getColumnIndexOrThrow(COL_ID)
        val titleIndex = getColumnIndexOrThrow(COL_TITLE)
        val contentIndex = getColumnIndexOrThrow(COL_CONTENT)
        val lockedIndex = getColumnIndexOrThrow(COL_LOCKED)
        val createdIndex = getColumnIndexOrThrow(COL_CREATED)
        val updatedIndex = getColumnIndexOrThrow(COL_UPDATED)
        val out = ArrayList<Note>(count)
        while (moveToNext()) {
            out += Note(
                id = getString(idIndex),
                title = getString(titleIndex),
                content = getString(contentIndex),
                isLocked = getInt(lockedIndex) != 0,
                createdAt = getLong(createdIndex),
                updatedAt = getLong(updatedIndex),
            )
        }
        return out
    }

    companion object {
        private const val DB_NAME = "hinotes.db"
        private const val DB_VERSION = 2
        private const val TABLE = "notes"
        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_CONTENT = "content"
        private const val COL_LOCKED = "locked"
        private const val COL_CREATED = "created_at"
        private const val COL_UPDATED = "updated_at"
    }
}
