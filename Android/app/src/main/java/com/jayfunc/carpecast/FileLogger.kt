package com.jayfunc.carpecast

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private var logDir: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun init(context: Context) {
        logDir = File(context.getExternalFilesDir(null), "Logs")
        if (logDir?.exists() == false) {
            logDir?.mkdirs()
        }
        i("FileLogger", "Logger initialized at ${logDir?.absolutePath}")

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("GlobalCrash", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun getLogDir(): File? = logDir

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("D", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("I", tag, message)
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        Log.e(tag, message, tr)
        writeToFile("E", tag, message + (tr?.let { "\n${Log.getStackTraceString(it)}" } ?: ""))
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("W", tag, message)
    }

    private fun writeToFile(level: String, tag: String, message: String) {
        if (logDir == null) return
        try {
            val now = Date()
            val timestamp = dateFormat.format(now)
            val dateStr = fileDateFormat.format(now)
            val logFile = File(logDir, "log-$dateStr.txt")
            val logLine = "$timestamp $level/$tag: $message\n"
            FileOutputStream(logFile, true).use {
                it.write(logLine.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("FileLogger", "Error writing to log file", e)
        }
    }
}
