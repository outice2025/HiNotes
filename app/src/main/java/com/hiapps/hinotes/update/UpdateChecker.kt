package com.hiapps.hinotes.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks the project's release feed for a newer version.
 *
 * This is the only request the app makes on its own initiative, it runs only when the user taps
 * "Check for updates", and it sends nothing about the device or the notes - just a versioned
 * `GET` for the latest release tag.
 */
object UpdateChecker {

    private const val RELEASES_API = "https://api.github.com/repos/hinotes/hinotes/releases/latest"
    private const val TIMEOUT_MS = 10_000

    sealed interface Outcome {
        /** The installed version is the newest one. */
        data object UpToDate : Outcome

        /** A newer version exists; [version] is its tag with any leading `v` removed. */
        data class Available(val version: String) : Outcome

        /** The feed could not be reached or did not look like a release payload. */
        data class Failed(val reason: String) : Outcome
    }

    /** Compares [currentVersion] against the latest published release. */
    fun check(currentVersion: String): Outcome {
        val body = fetch() ?: return Outcome.Failed("unreachable")
        val latest = runCatching {
            JSONObject(body).optString("tag_name").removePrefix("v")
        }.getOrNull()

        if (latest.isNullOrBlank()) return Outcome.Failed("no tag in response")

        return if (compare(latest, currentVersion) > 0) {
            Outcome.Available(latest)
        } else {
            Outcome.UpToDate
        }
    }

    private fun fetch(): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "HiNotes-Android")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Compares dotted numeric versions. Returns a positive number when [a] is newer than [b].
     * Non-numeric suffixes (e.g. `-beta1`) are ignored, which is the right call for release tags.
     */
    internal fun compare(a: String, b: String): Int {
        fun parts(value: String) = value.split('.', '-', '+')
            .map { segment -> segment.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

        val left = parts(a)
        val right = parts(b)
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = (left.getOrNull(i) ?: 0) - (right.getOrNull(i) ?: 0)
            if (diff != 0) return diff
        }
        return 0
    }
}
