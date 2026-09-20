package com.saigro.editsai

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Minimal offline crash log for Phase 1 diagnostics. */
object CrashLogger {
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(context.filesDir, FILE_NAME).writeText(
                    "Time: $stamp\nThread: ${thread.name}\n" +
                        "${error.stackTraceToString()}"
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }
}
