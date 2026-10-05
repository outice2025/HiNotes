package com.hinotes.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Local image storage for notes.
 *
 * An image the user picks through the system picker only grants read access for as long as the
 * app holds it, so referencing the picker's URI inside a note would leave a broken image the
 * next time the note is opened. Instead the bytes are copied into the app's own files directory
 * and the note stores a stable `hinotes-image://<name>` reference that always resolves.
 */
object ImageStore {

    private const val TAG = "HiNotesImages"

    /** URI scheme used inside note bodies. */
    const val SCHEME = "hinotes-image"

    private const val DIR = "images"

    /** Largest edge kept when importing; notes do not need full-resolution photos. */
    private const val MAX_EDGE = 2048

    private const val JPEG_QUALITY = 88

    /**
     * Copies [source] into app storage, downscaling it to a sane size.
     *
     * Returns the reference to embed in the note, or null when the image could not be read.
     */
    suspend fun import(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val decoded = decodeDownscaled(context, source) ?: return@withContext null
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val name = "img-${System.currentTimeMillis()}-${(0..0xFFFF).random()}.jpg"
            val target = File(dir, name)

            target.outputStream().use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            decoded.recycle()

            reference(name)
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "image too large to import", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "could not import image", e)
            null
        }
    }

    /** Builds the in-note reference for a stored image. */
    fun reference(name: String): String = "$SCHEME://$name"

    /** Resolves an in-note reference to the file on disk, or null when it is not one of ours. */
    fun fileFor(context: Context, reference: String): File? {
        if (!reference.startsWith("$SCHEME://")) return null
        val name = reference.removePrefix("$SCHEME://").substringBefore('?')
        // Guard against a crafted reference escaping the images directory.
        if (name.isEmpty() || name.contains('/') || name.contains('\\') || name.contains("..")) {
            return null
        }
        val file = File(File(context.filesDir, DIR), name)
        return file.takeIf { it.isFile }
    }

    /** Decodes a stored image to a bitmap for display. */
    suspend fun load(context: Context, reference: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = fileFor(context, reference) ?: return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    private fun decodeDownscaled(context: Context, source: Uri): Bitmap? {
        // First pass: read the bounds only, so a huge image never allocates in full.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(source)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
        }
        return context.contentResolver.openInputStream(source)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    /** Power-of-two downsample factor that keeps the longest edge within [MAX_EDGE]. */
    internal fun sampleSizeFor(width: Int, height: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= MAX_EDGE) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /** Deletes stored images no longer referenced by any note. */
    suspend fun prune(context: Context, liveReferences: Set<String>) = withContext(Dispatchers.IO) {
        val liveNames = liveReferences
            .filter { it.startsWith("$SCHEME://") }
            .map { it.removePrefix("$SCHEME://").substringBefore('?') }
            .toSet()
        File(context.filesDir, DIR).listFiles()?.forEach { file ->
            if (file.name !in liveNames) file.delete()
        }
    }
}
