package com.hiapps.hinotes.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The shareable notes archive: one UTF-8 Markdown file per note, zipped.
 *
 * A note is prose, and Markdown is what the editor already writes, so a notes export needs no
 * conversion and no proprietary shape - unzip it and every note is a document any editor can
 * open. The note's title becomes the file's name and, when it has one, an `# ` heading, so a
 * file that has been renamed still says what it was.
 *
 * Nothing here touches Android, which keeps the codec testable on its own terms: [decode] is the
 * exact inverse of [encode], and that round trip is what the export/import pair promises.
 */
object NotesArchive {

    /** Builds the archive from [notes]. */
    fun encode(notes: List<Note>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            val used = HashSet<String>()
            notes.forEach { note ->
                zip.putNextEntry(ZipEntry(uniqueName(note, used)))
                zip.write(markdownDocument(note).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
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
     */
    fun decode(bytes: ByteArray): List<Note>? {
        if (!looksLikeArchive(bytes)) return null
        return runCatching {
            val out = ArrayList<Note>()
            var entries = 0
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    entries++
                    if (!entry.isDirectory && entry.name.endsWith(".md", ignoreCase = true)) {
                        // A ZipInputStream stops at the end of the current entry, so this reads
                        // exactly one file.
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        noteFromMarkdown(text)?.let(out::add)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            if (entries == 0) null else out
        }.getOrNull()
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

    /** A unique, filesystem-safe `.md` name for [note] within one archive. */
    private fun uniqueName(note: Note, used: MutableSet<String>): String {
        val cleaned = note.displayTitle(FALLBACK_NAME)
            // Characters no common filesystem accepts, plus control characters.
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            // Markdown line markers would otherwise end up at the front of a file name.
            .trimStart('#', '-', '*', '>', ' ', '.')
            .trim()
            .take(60)
            .ifBlank { FALLBACK_NAME }
        var candidate = "$cleaned.md"
        var suffix = 2
        while (!used.add(candidate)) {
            candidate = "$cleaned-$suffix.md"
            suffix++
        }
        return candidate
    }

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
