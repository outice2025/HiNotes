package com.hiapps.hinotes.data

import java.util.UUID

/**
 * A single note.
 *
 * [content] holds the raw note body. When Markdown is enabled in settings the body is written
 * as Markdown source; otherwise it is treated as plain text. Either way it is stored verbatim
 * so nothing is lost when the setting is toggled.
 *
 * [isLocked] and [pinned] are the note's own two flags: a locked note cannot be edited but can
 * still be deleted, and a pinned note is listed ahead of the rest whatever the sort order is.
 */
data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val content: String = "",
    val isLocked: Boolean = false,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /**
     * Title for display.
     *
     * A note without a title is listed as [fallback] ("Untitled") rather than borrowing its first
     * line of text: the body is already shown underneath the title on the same card, so repeating
     * it as a heading made every untitled note look as if it had a title after all.
     */
    fun displayTitle(fallback: String): String = title.ifBlank { fallback }
}

/**
 * How much text a note body holds, for the properties sheet.
 *
 * Letters and digits only. Whitespace is not text, punctuation is not text, Markdown syntax is
 * punctuation, and an emoji is a symbol rather than a word - so a Chinese note counts its
 * characters and an English one counts its letters, which is what "字数" means for either. The
 * note's title is not part of the body and is therefore never counted.
 */
fun countNoteCharacters(content: String): Int = content.count { it.isLetterOrDigit() }

/**
 * How much text [text] holds with its punctuation kept, for the properties sheet's second count.
 *
 * Letters, digits, punctuation and symbols, whitespace excluded: the title and the punctuation the
 * first count leaves out are both counted here, so the two numbers read as the same note measured
 * strictly and loosely.
 */
fun countCharactersWithPunctuation(text: String): Int = text.count { !it.isWhitespace() }

/**
 * How many punctuation marks and symbols [text] carries.
 *
 * Unicode punctuation and symbol code points, emoji included, counted one apiece: the marks the two
 * character counts above treat as not text are what this row is about. A surrogate pair is one
 * symbol, not two characters.
 */
private val punctuationOrSymbol = Regex("[\\p{P}\\p{S}]")

fun countNotePunctuation(text: String): Int = punctuationOrSymbol.findAll(text).count()

/**
 * How many lines [text] opens, for the properties sheet.
 *
 * An empty body has no line, and a trailing line break opens one more, because that is the line the
 * caret sits on when the note ends with a break.
 */
fun countNoteLines(text: String): Int = if (text.isEmpty()) 0 else text.split('\n').size

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
