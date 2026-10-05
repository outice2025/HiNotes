package com.hiapps.hinotes.data

import java.util.UUID

/**
 * A single note.
 *
 * [content] holds the raw note body. When Markdown is enabled in settings the body is written
 * as Markdown source; otherwise it is treated as plain text. Either way it is stored verbatim
 * so nothing is lost when the setting is toggled.
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val isLocked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Title for display, falling back to the first non-empty content line, then a default. */
    fun displayTitle(fallback: String): String =
        title.ifBlank { content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
            .ifBlank { fallback }

    /** Single-line snippet of the body for list rows. */
    fun snippet(): String =
        content.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()

    val wordCount: Int
        get() = content.split(Regex("\\s+")).count { it.isNotBlank() }

    val charCount: Int get() = content.length

    val lineCount: Int get() = if (content.isEmpty()) 0 else content.count { it == '\n' } + 1
}

/** Sort orders offered on the home screen. */
enum class NoteSort(val key: String) {
    UpdatedDesc("updated"),
    CreatedDesc("created"),
    TitleAsc("title"),
    ;

    companion object {
        fun fromKey(key: String): NoteSort =
            entries.firstOrNull { it.key == key } ?: UpdatedDesc
    }
}

/** Outcome of an import operation, surfaced to the user as a confirmation message. */
sealed interface ImportResult {
    data class Success(val count: Int) : ImportResult
    data class Failure(val message: String) : ImportResult
}

/**
 * How the home screen arranges notes.
 *
 * [List] is the original single-column list; [Grid] puts two notes per row, with as many rows as
 * the notes need.
 */
enum class HomeLayout(val key: String) {
    List("list"),
    Grid("grid"),
    ;

    companion object {
        val Default = List

        fun fromKey(key: String): HomeLayout =
            entries.firstOrNull { it.key == key } ?: Default
    }
}
