package com.hiapps.hinotes.data

/**
 * Standalone checks for [NotesArchive], run on a desktop JVM by tools/verify/run.ps1.
 *
 * The archive codec is the one piece of export/import logic with no Android dependency, so it can
 * be executed rather than reasoned about: this checks that decode(encode(notes)) returns the same
 * notes, that file names stay unique and filesystem-safe, and that foreign bytes are rejected.
 */
object ArchiveChecks {

    private var failures = 0

    private fun check(name: String, condition: Boolean, detail: String = "") {
        if (condition) {
            println("  ok    $name")
        } else {
            failures++
            println("  FAIL  $name ${if (detail.isEmpty()) "" else "-> $detail"}")
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("NotesArchive round trip")

        val notes = listOf(
            Note(title = "Shopping list", content = "- milk\n- [ ] bread\n\n**bold** text"),
            Note(title = "", content = "Untitled note body\nsecond line"),
            Note(title = "中文标题", content = "第一行\n第二行，带标点。"),
            Note(title = "Shopping list", content = "a duplicate title"),
            Note(title = "a/b:c*d?e\"f<g>h|i", content = "illegal filename characters"),
            Note(title = "# Heading looking title", content = "body"),
            Note(title = "trailing newline", content = "line\n"),
            Note(title = "  padded  ", content = "  padded body  "),
        )

        val bytes = NotesArchive.encode(notes)
        check("archive is a zip", NotesArchive.looksLikeArchive(bytes), "size=${bytes.size}")
        check("json is not mistaken for a zip", !NotesArchive.looksLikeArchive("{}".toByteArray()))

        val back = NotesArchive.decode(bytes)
        check("decodes", back != null)
        if (back == null) return report()

        check("note count preserved", back.size == notes.size, "got ${back.size}")

        // The title round trips; the body round trips apart from the newline the writer appends
        // so a file does not end mid-line.
        notes.zip(back).forEachIndexed { index, (original, restored) ->
            val titleIn = original.title.trim()
            check("[$index] title", restored.title == titleIn, "want '$titleIn' got '${restored.title}'")
            val bodyIn = original.content
            check(
                "[$index] body",
                restored.content == bodyIn || restored.content == "$bodyIn\n",
                "want '$bodyIn' got '${restored.content}'",
            )
        }

        // The empty-title note takes its first body line as the file name, not "note".
        val names = zipEntryNames(bytes)
        check("empty title uses first line", names.any { it == "Untitled note body.md" }, names.toString())
        check("duplicate titles get a suffix", names.count { it.startsWith("Shopping list") } == 2 &&
            names.contains("Shopping list-2.md"), names.toString())
        check(
            "illegal characters replaced",
            names.any { it == "a b c d e f g h i.md" },
            names.toString(),
        )
        check("markdown heading trimmed from name", names.contains("Heading looking title.md"), names.toString())
        check("every entry is .md", names.all { it.endsWith(".md") }, names.toString())
        check("names unique", names.toSet().size == names.size, names.toString())

        // An empty note (no title, no body) is written but not read back as a note.
        val blank = NotesArchive.decode(NotesArchive.encode(listOf(Note())))
        check("blank note is skipped on import", blank != null && blank.isEmpty(), blank.toString())

        check("foreign bytes rejected", NotesArchive.decode("not a zip".toByteArray()) == null)

        // A truncated archive keeps the local-file header but has no readable entries; it must be
        // reported as unreadable rather than imported as nothing.
        val truncated = bytes.copyOfRange(0, 12)
        check(
            "truncated archive rejected",
            NotesArchive.decode(truncated) == null,
            NotesArchive.decode(truncated).toString(),
        )

        report()
    }

    private fun zipEntryNames(bytes: ByteArray): List<String> {
        val out = ArrayList<String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                out += entry.name
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun report() {
        if (failures == 0) {
            println("archive checks: all checks passed")
        } else {
            println("archive checks: $failures check(s) FAILED")
            throw IllegalStateException("$failures archive check(s) failed")
        }
    }
}
