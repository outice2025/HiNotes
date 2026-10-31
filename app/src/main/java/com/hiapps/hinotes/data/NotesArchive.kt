package com.hiapps.hinotes.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The shareable notes archive: one UTF-8 document per note, zipped.
 *
 * A note is prose, and the archive needs no conversion and no proprietary shape - unzip it and
 * every note is a document any editor can open. The document's extension follows the editor's
 * Markdown switch: `.md` while notes are written as Markdown, `.txt` while they are written as
 * plain text, which is what tells a reader (and a file manager) how to treat the contents. The
 * shape of the document is the same either way - the note's title as an `# ` heading when it has
 * one, then the body - so an import restores titles whatever the extension says.
 *
 * A document carries text and nothing else, so a note's own two flags are not part of an archive:
 * a Markdown file has nowhere to keep "locked" or "pinned". The JSON notes backup in [Backup] is
 * what carries those, and it is what a full restore is taken from.
 *
 * The pictures a note refers to are in the archive too, in an `images/` directory of their own and
 * under the very names the note's addresses hold, because the note's text is the only record of
 * which pictures it has: a restore that did not bring them back would leave every address in a
 * restored note pointing at a file that was never there. The directory is what keeps them from being
 * read back as notes, and an archive written before pictures were carried has no such directory at
 * all - it imports exactly as it did, and a reader that does not know about pictures reads this one
 * exactly as it read that one.
 *
 * Nothing here asks Android for anything: the pictures arrive as bytes the caller read, and the only
 * thing this knows about them is the address grammar [NoteImages] reads out of a note's own text.
 * That keeps the codec testable on its own terms: [decode] is the exact inverse of [encode], and
 * that round trip is what the export/import pair promises.
 */
object NotesArchive {

    /** What a document leaves as when the notes are Markdown, and when they are plain text. */
    private const val MARKDOWN_EXTENSION = ".md"
    private const val TEXT_EXTENSION = ".txt"

    /** The one directory inside an archive that holds pictures rather than documents. */
    private const val IMAGE_DIRECTORY = "images"

    /**
     * Builds the archive from [notes].
     *
     * @param markdown whether the notes are written as Markdown, which decides the extension every
     *   document in the archive carries.
     * @param picture the bytes of the picture stored under a given name, or null when there is no
     *   such file. Which pictures the archive holds is read out of the notes' own text, so the two
     *   halves cannot disagree about what a restored note needs.
     */
    fun encode(
        notes: List<Note>,
        markdown: Boolean,
        picture: (name: String) -> ByteArray? = { null },
    ): ByteArray {
        val extension = if (markdown) MARKDOWN_EXTENSION else TEXT_EXTENSION
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            val used = HashSet<String>()
            notes.forEach { note ->
                zip.putNextEntry(ZipEntry(uniqueName(note, used, extension)))
                zip.write(markdownDocument(note).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            writePictures(zip, notes, picture)
        }
        return buffer.toByteArray()
    }

    /** True when [bytes] starts with the ZIP local-file header. */
    fun looksLikeArchive(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            bytes[2] == 3.toByte() && bytes[3] == 4.toByte()

    /**
     * Reads an archive back into notes, or null when the bytes cannot be read as an archive.
     *
     * The inverse of [encode]: an `# ` heading on the first line is the title and everything
     * after it is the body. Files with neither a heading nor a body are skipped rather than
     * imported as empty notes.
     *
     * A zip with no readable entries at all - not a zip, or a truncated one - is null rather than
     * an empty list: "this file is not ours" and "this file is an archive of nothing" have to be
     * distinguishable, because the first is a failed import and the second is an empty export.
     *
     * @param picture receives every picture the archive carries, under the name its entry holds, as
     *   that entry is read: an import is writing files, and one picture at a time is the difference
     *   between restoring a backup and holding all of it in memory. An archive written before
     *   pictures were carried has none, so a caller that has nowhere to put one is never asked.
     */
    fun decode(
        bytes: ByteArray,
        picture: (name: String, bytes: ByteArray) -> Unit = { _, _ -> },
    ): List<Note>? {
        if (!looksLikeArchive(bytes)) return null
        return runCatching {
            val out = ArrayList<Note>()
            var entries = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries++
                    when {
                        entry.isDirectory -> Unit
                        // A picture is known by the directory it is written in, so a note whose
                        // title reads "images" is still a document like any other.
                        isPicture(entry.name) -> {
                            val name = entry.name.removePrefix("$IMAGE_DIRECTORY/")
                            picture(name, zip.readBytes())
                        }
                        isDocument(entry.name) -> {
                            // A ZipInputStream stops at the end of the current entry, so this reads
                            // exactly one file.
                            val text = zip.readBytes().toString(Charsets.UTF_8)
                            noteFromMarkdown(text)?.let(out::add)
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (entries == 0) null else out
        }.getOrNull()
    }

    /**
     * Writes one `images/<name>` entry per picture the notes refer to.
     *
     * One entry per name however many notes name it: duplicating a note copies its text and the
     * picture along with it, so two notes can hold the same address, and the archive carries that
     * address's picture once. A picture whose file has gone is left out rather than failing the
     * export - the address stays in the note's text, which is where it has always been.
     *
     * A picture is asked for and written one at a time, so an archive of photographs is never held
     * in memory twice over on its way out.
     */
    private fun writePictures(
        zip: ZipOutputStream,
        notes: List<Note>,
        picture: (name: String) -> ByteArray?,
    ) {
        val written = HashSet<String>()
        for (note in notes) {
            for (address in NoteImages.addresses(note.content)) {
                val name = NoteImages.storedName(address) ?: continue
                if (!written.add(name)) continue
                val bytes = picture(name) ?: continue
                zip.putNextEntry(ZipEntry("$IMAGE_DIRECTORY/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    /** The file a note is written as: its title as an `# ` heading, then the body. */
    private fun markdownDocument(note: Note): String {
        val title = note.title.trim()
        val body = note.content
        return buildString {
            if (title.isNotEmpty()) append("# ").append(title).append("\n\n")
            append(body)
            if (body.isNotEmpty() && !body.endsWith("\n")) append('\n')
        }
    }

    /**
     * A unique, filesystem-safe name for [note] within one archive, carrying [extension].
     *
     * An untitled note is named after its first non-empty body line rather than after
     * [FALLBACK_NAME]: on the home screen "Untitled" is the honest label for a note with no title,
     * but twenty files called `Untitled.md` in one export help nobody. The name is only a file
     * name - the note's own title is untouched by it.
     */
    private fun uniqueName(note: Note, used: MutableSet<String>, extension: String): String {
        val cleaned = note.title.trim()
            .ifBlank { note.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty() }
            .ifBlank { FALLBACK_NAME }
            // Characters no common filesystem accepts, plus control characters.
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            // Markdown line markers would otherwise end up at the front of a file name.
            .trimStart('#', '-', '*', '>', ' ', '.')
            .trim()
            .take(60)
            .ifBlank { FALLBACK_NAME }
        var candidate = "$cleaned$extension"
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = "$cleaned-$suffix$extension"
            suffix++
        }
        return candidate
    }

    /** True for the names [encode] writes, either way round the Markdown switch. */
    private fun isDocument(name: String): Boolean =
        name.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ||
            name.endsWith(TEXT_EXTENSION, ignoreCase = true)

    /**
     * True for the entries [encode] writes a picture under.
     *
     * The name after the directory is deliberately not judged here: what a picture may be called is
     * decided where the file would be written - see `NoteImages.storedFile` - so a name that would
     * walk out of the pictures directory is refused at the door rather than in the middle of a zip.
     */
    private fun isPicture(name: String): Boolean =
        name.startsWith("$IMAGE_DIRECTORY/") && name.length > IMAGE_DIRECTORY.length + 1

    private fun noteFromMarkdown(text: String): Note? {
        val lines = text.lines()
        val heading = lines.firstOrNull()
            ?.takeIf { it.startsWith("# ") }
            ?.removePrefix("# ")
            ?.trim()
            .orEmpty()
        val body = (if (heading.isEmpty()) text else lines.drop(1).joinToString("\n"))
            .removePrefix("\n")
        if (heading.isEmpty() && body.isBlank()) return null
        return Note(title = heading, content = body)
    }

    private const val FALLBACK_NAME = "note"
}
