package com.v2ray.ang.ui.fandogh

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Records the last fatal exception so it can be shown on the next launch.
 *
 * A crash during startup leaves nothing on screen and, for most users, no practical way
 * to reach logcat. This writes the stack trace to the app's private storage and hands the
 * throwable on to the platform handler, so behaviour is otherwise unchanged — the process
 * still dies exactly as it would have.
 *
 * Every method swallows its own failures: a reporter that can crash is worse than none.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        try {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            val appContext = context.applicationContext
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    write(appContext, thread, throwable)
                } catch (_: Throwable) {
                    // Never let reporting mask the original crash.
                }
                previous?.uncaughtException(thread, throwable)
            }
        } catch (_: Throwable) {
        }
    }

    private fun write(context: Context, thread: Thread, throwable: Throwable) {
        val trace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("Fandogh crash report")
            appendLine("time:    $stamp")
            appendLine("thread:  ${thread.name}")
            appendLine("device:  ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("abi:     ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine()
            append(trace)
        }
        File(context.filesDir, FILE_NAME).writeText(report)
    }

    /** The previous run's crash report, or null when the last run exited cleanly. */
    fun lastReport(context: Context): String? = try {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) file.readText() else null
    } catch (_: Throwable) {
        null
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (_: Throwable) {
        }
    }
}
