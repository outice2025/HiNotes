package com.hiapps.hinotes.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.util.UUID

/**
 * The pictures a note holds.
 *
 * A note is Markdown text, so a picture in it is an address like any other. The address is this
 * app's own scheme and the picture itself is copied into the app's private storage when it is
 * imported, because the two alternatives are worse: the address the picker hands over is a loan -
 * the file behind it may be moved, renamed or deleted the moment the picker closes, and it belongs
 * to whoever took the picture - while a widget is drawn by the launcher, which cannot read a file
 * this app was merely lent.
 *
 * Nothing here throws: a picture that cannot be read, copied or decoded comes back as null, and the
 * renderer draws the address the note holds instead.
 *
 * A note's own text is the only record of which pictures it has - there is no column for them and
 * nothing else in the app knows they exist - so the addresses are read back out of that text when
 * the pictures leave with an archive and when they go with the note they belonged to.
 */
object NoteImages {

    /** The scheme an imported picture's address carries, so a note's own pictures are recognisable. */
    const val SCHEME = "hinotes-image"

    /** Where the copies live, inside the app's private files directory. */
    private const val DIRECTORY = "images"

    /** The address a note is written with for the picture stored as [name]. */
    fun url(name: String): String = "$SCHEME://$name"

    /**
     * The addresses [text] holds, in the order they appear and each of them once.
     *
     * The editor writes a picture as `![alt](address)`, but a note that is written as plain text, or
     * one whose address was moved by hand, holds the bare address just as well and names the same
     * file either way - so the address is what is looked for, not the form it was written in. A run
     * of address characters ends at the first one that cannot be inside an address, which is why the
     * Markdown form's closing bracket never ends up in the name.
     */
    fun addresses(text: String): List<String> =
        ADDRESS.findAll(text).map { it.value }.distinct().toList()

    /**
     * What an address is made of: the scheme, then a name running to the first delimiter.
     *
     * The excluded characters - whitespace and the brackets and quotes every form of an address is
     * written inside - cannot appear in a stored name in the first place, because a name is
     * generated here from a picture's type; stopping at them therefore never cuts a name short.
     */
    private val ADDRESS = Regex("$SCHEME://[^\\s()\\[\\]<>\"']+")

    /**
     * Copies [source] into the app's own storage.
     *
     * An empty copy is not a picture: a provider answers with an empty stream for a row whose file
     * has gone - a picture still uploading, one in the cloud, one the user deleted between picking
     * and reading - and a note that keeps the address of an empty file draws nothing at all. Such a
     * copy is deleted rather than kept, and the pick is reported as the failure it is.
     *
     * @return the address to write into the note, or null when the picture could not be read or
     *   written - a picker can hand over a file that has already gone, and a full disk is a full
     *   disk.
     */
    fun import(context: Context, source: Uri): String? = runCatching {
        val target = File(directory(context), "${UUID.randomUUID()}.${extension(context, source)}")
        val copied = context.contentResolver.openInputStream(source)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
            target.length() > 0
        } ?: false
        if (copied) {
            url(target.name)
        } else {
            // Whatever arrived is not something a note can hold, so nothing is left behind for it.
            runCatching { target.delete() }
            null
        }
    }.getOrNull()

    /**
     * The picture at most [maxWidth] pixels wide, or null when there is nothing to decode.
     *
     * Almost every picture a phone takes is larger than the space it is shown in - a card is a few
     * hundred pixels wide and the file behind it may be twelve megapixels - so it is decoded at the
     * size it is drawn at rather than at the size it was taken at. A maxWidth of 0 decodes it whole,
     * which is what an export or a share wants.
     */
    fun decode(context: Context, url: String, maxWidth: Int = 0): Bitmap? = runCatching {
        val file = file(context, url)?.takeIf { it.isFile } ?: return@runCatching null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxWidth)
        }
        val decoded = BitmapFactory.decodeFile(file.path, options) ?: return@runCatching null
        // The sample size only halves, so the picture is scaled the rest of the way: a card wants
        // the picture it has room for, not the nearest power of two above it. The one in between is
        // let go of as soon as the smaller copy exists - a photograph decoded at half its width is
        // still tens of megabytes of pixels, and a note may hold several.
        if (maxWidth in 1 until decoded.width) {
            val scaled = Bitmap.createScaledBitmap(
                decoded,
                maxWidth,
                (decoded.height.toLong() * maxWidth / decoded.width).toInt().coerceAtLeast(1),
                true,
            )
            decoded.recycle()
            scaled
        } else {
            decoded
        }
    }.getOrNull()

    /**
     * The bytes of the picture stored as [name], or null when there is no such file.
     *
     * A picture whose file has gone comes back as null, which is what an export does with it: that
     * one picture is left out of the archive and the note keeps the address it has always held.
     */
    fun read(context: Context, name: String): ByteArray? = runCatching {
        storedFile(directory(context), name)?.takeIf { it.isFile }?.readBytes()
    }.getOrNull()

    /**
     * Writes a picture that arrived in an archive under [name].
     *
     * The directory is a parameter rather than something read from a context so that the rule below
     * can be executed by the desktop checks in `tools/verify`, where there is no app to ask.
     *
     * A file that is already there is left exactly as it is. Names are generated here when a picture
     * is imported, so a name that is taken either belongs to the same picture arriving a second time
     * or to a picture this device already holds under it, and neither is improved by replacing it
     * with whatever an archive happened to carry under that name: the import would quietly change a
     * picture the notes on this device have been showing.
     *
     * @return true when the file was written; false when it was skipped, refused or could not be
     *   written, none of which fails an import - the note's text still holds the address, and a
     *   picture that is not there is drawn as that address.
     */
    fun restore(directory: File, name: String, bytes: ByteArray): Boolean = runCatching {
        val target = storedFile(directory, name) ?: return@runCatching false
        if (target.exists()) return@runCatching false
        target.outputStream().use { it.write(bytes) }
        true
    }.getOrDefault(false)

    /** [restore] into this app's own pictures directory. */
    fun restore(context: Context, name: String, bytes: ByteArray): Boolean =
        restore(directory(context), name, bytes)

    /**
     * Deletes the files behind [addresses], except the ones [keep] names as well.
     *
     * [keep] is what every note that is still there refers to, and it is the whole reason this is
     * not a plain loop over the deleted note's pictures: a picture is not owned by one note -
     * duplicating a note copies its text, and the text is the only record of the picture it holds -
     * so the note that is left keeps its picture alive. The directory is a parameter for the same
     * reason [restore] takes one.
     *
     * Nothing here throws and nothing here is reported as a failure: a file that has already gone,
     * or one the system refuses to remove, leaves the note deleted, which is what was asked for, and
     * a picture that cannot be read is drawn as the address the note holds.
     *
     * @return how many files were actually removed.
     */
    fun delete(directory: File, addresses: Collection<String>, keep: Collection<String>): Int {
        var removed = 0
        addresses.distinct().forEach { address ->
            if (address in keep) return@forEach
            val target = file(directory, address) ?: return@forEach
            if (runCatching { target.delete() }.getOrDefault(false)) removed++
        }
        return removed
    }

    /** [delete] from this app's own pictures directory. */
    fun delete(context: Context, addresses: Collection<String>, keep: Collection<String>): Int =
        delete(directory(context), addresses, keep)

    /** The file a note's own address refers to, or null when the address is not one of ours. */
    fun file(context: Context, url: String): File? = file(directory(context), url)

    /** The file [url] refers to inside [directory], or null when the address is not one of ours. */
    private fun file(directory: File, url: String): File? =
        storedName(url)?.let { storedFile(directory, it) }

    /** The stored name inside [url], or null when it is not one of this app's addresses. */
    fun storedName(url: String): String? =
        url.removePrefix("$SCHEME://").takeIf { it != url && it.isNotBlank() }

    /**
     * The file [name] stands for inside [directory], or null when that is not a file of its own.
     *
     * The name is the whole of what an address carries, so anything that could walk out of the
     * directory it names is refused rather than resolved: a separator, and the two names that stand
     * for a directory itself. This is reached with names read out of a note's text and out of an
     * archive somebody else wrote, and an export or a delete that obeyed one of those would reach
     * past the pictures this app copied in.
     */
    private fun storedFile(directory: File, name: String): File? {
        if (name.contains('/') || name.contains('\\') || name != File(name).name) return null
        if (name == "." || name == "..") return null
        return File(directory, name)
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    /**
     * How much a picture is halved by while decoding.
     *
     * A power of two, because that is what the decoder accepts, and the smallest one that still
     * leaves the picture at least as wide as the space it is going into.
     */
    private fun sampleSize(width: Int, height: Int, maxWidth: Int): Int {
        if (maxWidth <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= 1) sample *= 2
        return sample
    }

    /** The extension a copy of [source] is stored under, from the type its provider reports. */
    private fun extension(context: Context, source: Uri): String =
        when (val type = context.contentResolver.getType(source)) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/gif" -> "gif"
            "image/heic", "image/heif" -> "heic"
            "image/bmp" -> "bmp"
            else -> type?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "img"
        }
}
