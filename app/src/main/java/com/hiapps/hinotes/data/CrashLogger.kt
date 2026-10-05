package com.hiapps.hinotes.data

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash recorder.
 *
 * HiNotes has no analytics and no crash reporting service, so an unhandled exception would
 * otherwise leave the user with nothing to report. This installs a process-wide handler that
 * writes the stack trace plus the app version and device facts to `cacheDir/crashes/`, then
 * defers to the platform's default handler so the process still dies as Android expects.
 *
 * The report becomes shareable through the log export on the About screen, so a crash can be
 * reported without the store listing needing a network dependency.
 */
object CrashLogger {

    private const val TAG = "HiNotesCrash"
    private const val DIR = "crashes"
    private const val MAX_REPORTS = 5

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Never let the logger itself mask the original crash.
            runCatching { write(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(MAX_REPORTS - 1)
            ?.forEach { it.delete() }

        val stack = StringWriter().also { buffer ->
            PrintWriter(buffer).use { throwable.printStackTrace(it) }
        }.toString()

        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("HiNotes crash report")
            appendLine("time: $stamp")
            appendLine("thread: ${thread.name}")
            appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            appendLine("android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
            appendLine("app: ${com.hiapps.hinotes.BuildConfig.VERSION_NAME} (${com.hiapps.hinotes.BuildConfig.VERSION_CODE})")
            appendLine("locale: ${Locale.getDefault()}")
            appendLine()
            append(stack)
        }

        val file = File(dir, "crash-${System.currentTimeMillis()}.txt")
        file.writeText(report)
        Log.e(TAG, "uncaught exception recorded to ${file.name}", throwable)
    }

    /** Most recent crash report, if any, for inclusion in an exported log bundle. */
    fun latest(context: Context): String? {
        val dir = File(context.cacheDir, DIR)
        val newest = dir.listFiles()?.maxByOrNull { it.lastModified() } ?: return null
        return runCatching { newest.readText() }.getOrNull()
    }
}
