package com.hiapps.hinotes.data

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Standalone checks for [NotesArchive], run on a desktop JVM by tools/verify/run.ps1.
 *
 * The archive codec is the one piece of export/import logic that asks Android for nothing, so it can
 * be executed rather than reasoned about: this checks that decode(encode(notes)) returns the same
 * notes, that file names stay unique and filesystem-safe, and that foreign bytes are rejected.
 *
 * The pictures a note refers to travel in that archive and go with the note when it is deleted, and
 * the only record of them is the address in the note's own text - so the address is read, the
 * archive is written and read back with real files in a temporary directory, and the files are
 * deleted the way a delete path deletes them.
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

        val bytes = NotesArchive.encode(notes, markdown = true)
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

        // The empty-title note is named after its first body line rather than after "Untitled":
        // the home screen shows "Untitled" for a note with no title, but a folder of Untitled.md
        // files is no use to anyone.
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

        // With Markdown off the same notes leave as plain text: the extension says so, and the
        // documents still read back, so an import after a switch of the setting loses nothing.
        val textBytes = NotesArchive.encode(notes, markdown = false)
        val textNames = zipEntryNames(textBytes)
        check("text export is a zip", NotesArchive.looksLikeArchive(textBytes))
        check("every text entry is .txt", textNames.all { it.endsWith(".txt") }, textNames.toString())
        check(
            "text names match the markdown ones",
            textNames.map { it.substringBeforeLast('.') } == names.map { it.substringBeforeLast('.') },
            textNames.toString(),
        )
        val textBack = NotesArchive.decode(textBytes)
        check("text archive decodes", textBack?.size == notes.size, textBack?.size.toString())

        // An empty note (no title, no body) is written but not read back as a note.
        val blank = NotesArchive.decode(NotesArchive.encode(listOf(Note()), markdown = true))
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

        pictures()
        report()
    }

    /**
     * The pictures a note refers to.
     *
     * A picture is a file and an address in a note's text and nothing else, so the two things that
     * can quietly go wrong are the address being misread and the file being lost by - or taken from -
     * a note that still needs it. Both are driven here with real files in a temporary directory,
     * which is why [NoteImages] takes the directory it works in as an argument.
     */
    private fun pictures() {
        println("pictures in a note")

        val inMarkdown = NoteImages.addresses("before\n\n![a picture](hinotes-image://a1b2.jpg)\n\nafter")
        check(
            "an address in the Markdown form is read",
            inMarkdown == listOf("hinotes-image://a1b2.jpg"),
            inMarkdown.toString(),
        )
        val bare = NoteImages.addresses("plain text names hinotes-image://c3d4.png on its line")
        check("a bare address is read", bare == listOf("hinotes-image://c3d4.png"), bare.toString())
        val twice = NoteImages.addresses("![a](hinotes-image://same.png)\n![b](hinotes-image://same.png)")
        check("a picture named twice is held once", twice.size == 1, twice.toString())
        check(
            "addresses of other kinds are left alone",
            NoteImages.addresses("https://example.com/a.png and hinotes-image:a.png").isEmpty(),
        )
        check(
            "an address yields the name its file is stored under",
            NoteImages.storedName("hinotes-image://e5.png") == "e5.png",
        )
        check("another scheme yields no name", NoteImages.storedName("https://e5.png") == null)

        val held = Files.createTempDirectory("hinotes-pictures").toFile()
        val restored = Files.createTempDirectory("hinotes-restored").toFile()
        try {
            File(held, "a1b2.jpg").writeBytes(byteArrayOf(1, 2, 3))
            File(held, "c3d4.png").writeBytes(byteArrayOf(4, 5, 6))

            // Two notes naming one picture is what a duplicated note looks like: the same address in
            // both texts, and one file behind it.
            val shared = Note(title = "with pictures", content = "one ![p](hinotes-image://a1b2.jpg)")
            val copy = Note(
                title = "a copy",
                content = "the same picture ![p](hinotes-image://a1b2.jpg)\n![q](hinotes-image://c3d4.png)",
            )
            val missing = Note(title = "a picture that has gone", content = "![p](hinotes-image://not-here.jpg)")

            val bytes = NotesArchive.encode(listOf(shared, copy, missing), markdown = true) { name ->
                File(held, name).takeIf { it.isFile }?.readBytes()
            }
            val names = zipEntryNames(bytes)
            check("a picture goes into the archive", names.contains("images/a1b2.jpg"), names.toString())
            check(
                "two notes naming one picture leave one entry",
                names.count { it == "images/a1b2.jpg" } == 1,
                names.toString(),
            )
            check(
                "a picture whose file has gone is not in the archive",
                names.none { it.contains("not-here") },
                names.toString(),
            )

            val carried = HashMap<String, ByteArray>()
            val back = NotesArchive.decode(bytes) { name, picture -> carried[name] = picture }
            check("the notes still decode", back?.size == 3, back?.size.toString())
            check(
                "the pictures come back under the names the addresses hold",
                carried.keys == setOf("a1b2.jpg", "c3d4.png"),
                carried.keys.toString(),
            )
            check(
                "a picture comes back whole",
                carried["a1b2.jpg"]?.contentEquals(byteArrayOf(1, 2, 3)) == true,
            )

            // A note holds the address of a picture that is not in the archive; the note imports
            // either way, which is what the renderer's fallback to the address is for.
            val orphan = back?.firstOrNull { it.title == "a picture that has gone" }
            check(
                "a note whose picture is missing keeps its address",
                orphan?.content?.contains("hinotes-image://not-here.jpg") == true,
                orphan?.content.orEmpty(),
            )

            // An archive written before pictures were carried has no images directory at all, so it
            // reads back exactly as it always did and no picture is ever handed over.
            val withoutPictures = NotesArchive.encode(listOf(shared, copy), markdown = true)
            check(
                "an archive without pictures has no images directory",
                zipEntryNames(withoutPictures).none { it.startsWith("images/") },
                zipEntryNames(withoutPictures).toString(),
            )
            var handedOver = false
            val older = NotesArchive.decode(withoutPictures) { _, _ -> handedOver = true }
            check(
                "an archive without pictures imports as it always did",
                older?.size == 2 && !handedOver,
                older?.size.toString(),
            )

            // A picture already on the device is not replaced, which is the rule that keeps an
            // import from changing a picture the notes here have been showing.
            File(restored, "a1b2.jpg").writeBytes(byteArrayOf(9, 9, 9))
            check(
                "a picture the device does not have is written",
                NoteImages.restore(restored, "c3d4.png", byteArrayOf(4, 5, 6)),
            )
            check(
                "a picture the device already has is left alone",
                !NoteImages.restore(restored, "a1b2.jpg", byteArrayOf(1, 2, 3)),
            )
            check(
                "the picture that was here is untouched",
                File(restored, "a1b2.jpg").readBytes().contentEquals(byteArrayOf(9, 9, 9)),
            )
            check(
                "a name that would walk out of the directory is refused",
                !NoteImages.restore(restored, "../hinotes-escape.png", byteArrayOf(7)),
            )

            // An archive is somebody else's file: a picture entry that names a directory of its own
            // is handed over as the name it holds, and refused where the file would be written.
            val hostile = ByteArrayOutputStream()
            ZipOutputStream(hostile).use { zip ->
                zip.putNextEntry(ZipEntry("images/../hinotes-escape.png"))
                zip.write(byteArrayOf(7))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("note.md"))
                zip.write("# note\n\n![p](hinotes-image://../hinotes-escape.png)".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            val accepted = HashMap<String, Boolean>()
            val hostileNotes = NotesArchive.decode(hostile.toByteArray()) { name, picture ->
                accepted[name] = NoteImages.restore(restored, name, picture)
            }
            check(
                "a note in a foreign archive still imports",
                hostileNotes?.size == 1,
                hostileNotes?.size.toString(),
            )
            check(
                "a picture name that walks out is refused",
                accepted["../hinotes-escape.png"] == false,
                accepted.toString(),
            )
            check(
                "nothing was written outside the pictures directory",
                !File(restored.parentFile, "hinotes-escape.png").exists(),
            )

            // Deleting a note takes its pictures with it, except the ones a note that is left names.
            // The picture that cannot be read is in there on purpose: it must not fail the delete.
            val gone = NoteImages.delete(
                directory = restored,
                addresses = NoteImages.addresses(
                    "![p](hinotes-image://a1b2.jpg)\n![q](hinotes-image://c3d4.png)\n![r](hinotes-image://not-here.png)",
                ),
                keep = NoteImages.addresses("![q](hinotes-image://c3d4.png)"),
            )
            check("only the picture no other note names is removed", gone == 1, "$gone removed")
            check("the picture another note still names is kept", File(restored, "c3d4.png").isFile)
            check("the picture only that note held is gone", !File(restored, "a1b2.jpg").exists())
            check(
                "an address that would walk out of the directory deletes nothing",
                NoteImages.delete(restored, listOf("hinotes-image://../hinotes-escape.png"), emptyList()) == 0,
            )
            check("the directory above is still there", restored.parentFile?.isDirectory == true)
        } finally {
            held.deleteRecursively()
            restored.deleteRecursively()
        }
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
