package com.hiapps.hinotes.data

import android.content.Context
import android.os.Build
import com.hiapps.hinotes.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup payloads.
 *
 * Settings and the JSON notes backup are plain UTF-8 JSON with a `kind` discriminator, so an
 * exported file can be inspected, diffed or hand-edited, and an import can reject a file that is
 * not ours instead of clobbering data. The Markdown notes archive lives in [NotesArchive].
 */
object Backup {

    const val KIND_SETTINGS = "hinotes.settings"
    const val KIND_NOTES = "hinotes.notes"

    private const val SCHEMA = 1
    private const val APP = "HiNotes"

    private fun envelope(kind: String): JSONObject = JSONObject().apply {
        put("app", APP)
        put("kind", kind)
        put("schema", SCHEMA)
        put("exportedAt", System.currentTimeMillis())
        put("appVersion", BuildConfig.VERSION_NAME)
    }

    // ------------------------------------------------------------------ settings

    fun encodeSettings(settings: HiNotesSettings): String {
        val values = JSONObject()
        SettingKey.entries.forEach { key -> values.put(key.storageKey, key.read(settings)) }
        return envelope(KIND_SETTINGS).put("settings", values).toString(2)
    }

    /**
     * Reads a settings file. Returns the decoded snapshot, or null when the payload is not a
     * settings backup (so the caller can report a clear error rather than silently resetting).
     */
    fun decodeSettings(raw: String, fallback: HiNotesSettings, reader: SettingsRepository): HiNotesSettings? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optString("kind") != KIND_SETTINGS) return null
        val values = root.optJSONObject("settings") ?: return null
        val pairs = buildMap {
            values.keys().forEach { name -> put(name, values.optString(name)) }
        }
        return reader.fromPairs(pairs, fallback)
    }

    // --------------------------------------------------------------------- notes

    fun encodeNotes(notes: List<Note>): String {
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject().apply {
                    put("id", note.id)
                    put("title", note.title)
                    put("content", note.content)
                    put("locked", note.isLocked)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                }
            )
        }
        return envelope(KIND_NOTES).put("notes", array).toString(2)
    }

    /** Reads a notes file, or null when the payload is not a notes backup. */
    fun decodeNotes(raw: String): List<Note>? {
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        if (root.optString("kind") != KIND_NOTES) return null
        val array = root.optJSONArray("notes") ?: return emptyList()
        val out = ArrayList<Note>(array.length())
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val now = System.currentTimeMillis()
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: Note().id
            out += Note(
                id = id,
                title = item.optString("title"),
                content = item.optString("content"),
                isLocked = item.optBoolean("locked", false),
                createdAt = item.optLong("createdAt", now),
                updatedAt = item.optLong("updatedAt", now),
            )
        }
        return out
    }

    // ---------------------------------------------------------------------- logs

    /**
     * Builds a diagnostics report. It deliberately contains no note text: only counts,
     * environment facts and the active settings, which is what a bug report needs.
     */
    fun buildLogReport(
        context: Context,
        settings: HiNotesSettings,
        noteCount: Int,
    ): String {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val json = JSONObject().apply {
            put("app", APP)
            put("kind", "hinotes.logs")
            put("appVersion", BuildConfig.VERSION_NAME)
            put("versionCode", BuildConfig.VERSION_CODE)
            put("generatedAt", stamp)
            put("noteCount", noteCount)
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("androidRelease", Build.VERSION.RELEASE)
                put("sdkInt", Build.VERSION.SDK_INT)
                put("supportedAbis", Build.SUPPORTED_ABIS.joinToString(", "))
            })
            put("locale", Locale.getDefault().toString())
            put("settings", JSONObject().apply {
                SettingKey.entries.forEach { key -> put(key.storageKey, key.read(settings)) }
            })
            put("lockConfigured", LockStore(context).isConfigured)
        }
        return json.toString(2)
    }

    /** Timestamped default file name for exports. */
    fun fileName(prefix: String, extension: String = "json"): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        return "hinotes-$prefix-$stamp.$extension"
    }
}
