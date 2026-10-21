package app.gloss

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last crash so it can be shared from the app: Java crashes are written by an
 * uncaught-exception handler, and freezes (ANRs) and native crashes come from the system's
 * exit records on the next launch.
 */
object CrashLog {
    private const val REPORT = "last-crash.txt"
    private const val SEEN = "last-exit-seen"

    fun install(context: Context) {
        val file = File(context.filesDir, REPORT)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { file.writeText(header() + "Thread: ${thread.name}\n\n" + e.stackTraceToString()) }
            previous?.uncaughtException(thread, e)
        }
    }

    /** The report to offer, or null when the app last exited normally (or it was dismissed). */
    fun pending(context: Context): String? {
        val file = File(context.filesDir, REPORT)
        val exit = lastExit(context)
        if (exit != null) {
            markSeen(context, exit.timestamp)
            when (exit.reason) {
                ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_CRASH_NATIVE -> {
                    val trace = if (exit.reason == ApplicationExitInfo.REASON_ANR) {
                        runCatching { exit.traceInputStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
                            ?.let(::mainThreadOf)
                    } else null
                    val kind = if (exit.reason == ApplicationExitInfo.REASON_ANR) "Froze (not responding)" else "Native crash"
                    val text = header(exit.timestamp) + "$kind: ${exit.description.orEmpty()}\n\n" + (trace ?: "")
                    runCatching { file.writeText(text) }
                }
            }
        }
        return file.takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }
    }

    fun clear(context: Context) {
        File(context.filesDir, REPORT).delete()
    }

    /** The most recent exit we haven't looked at yet. */
    private fun lastExit(context: Context): ApplicationExitInfo? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val last = runCatching { am.getHistoricalProcessExitReasons(null, 0, 1).firstOrNull() }.getOrNull() ?: return null
        val seen = context.getSharedPreferences(SEEN, Context.MODE_PRIVATE).getLong("t", 0L)
        return last.takeIf { it.timestamp > seen }
    }

    private fun markSeen(context: Context, t: Long) {
        context.getSharedPreferences(SEEN, Context.MODE_PRIVATE).edit().putLong("t", t).apply()
    }

    /** An ANR dump lists every thread; the main thread is the one that froze. */
    private fun mainThreadOf(dump: String): String {
        val start = dump.indexOf("\"main\"")
        if (start < 0) return dump.take(6000)
        val end = dump.indexOf("\n\n", start).takeIf { it > 0 } ?: dump.length
        return dump.substring(start, end).take(6000)
    }

    private fun header(time: Long = System.currentTimeMillis()): String {
        val at = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(time))
        return "Gloss ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}\n" +
            "$at\n"
    }
}
