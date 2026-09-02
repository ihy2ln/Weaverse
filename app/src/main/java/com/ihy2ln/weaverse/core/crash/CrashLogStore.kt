package com.ihy2ln.weaverse.core.crash

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashLogStore {
    private val stampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun dir(context: Context): File = File(context.filesDir, "crash").also { it.mkdirs() }

    fun latestFile(context: Context): File = File(dir(context), "latest.log")

    fun latestText(context: Context): String {
        val file = latestFile(context)
        if (!file.isFile) return "No crash log yet."
        return file.readText()
    }

    fun write(context: Context, thread: Thread, throwable: Throwable) {
        val body = buildString {
            appendLine("Weaverse crash")
            appendLine("time: ${stampFormat.format(Date())}")
            appendLine("thread: ${thread.name}")
            appendLine()
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            append(sw.toString())
        }
        val folder = dir(context)
        File(folder, "latest.log").writeText(body)
        File(folder, "crash-${System.currentTimeMillis()}.log").writeText(body)
        folder.listFiles()
            ?.filter { it.name.startsWith("crash-") && it.extension == "log" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(8)
            ?.forEach { it.delete() }
    }
}
