package com.ihy2ln.weaverse.ai

import android.util.Log

/**
 * Logcat helper that degrades gracefully in JVM unit tests. Info lines can carry whole
 * prompts and replies (the writer's story), so they're only written in debug builds.
 */
object WeaverseAiLog {
    const val TAG = "WeaverseAI"

    fun i(message: String) {
        if (!com.ihy2ln.weaverse.BuildConfig.DEBUG) return
        runCatching { Log.i(TAG, message) }.onFailure {
            println("I/$TAG: $message")
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        // Errors still log in release, cut short so they can't dump a whole story.
        val message = if (com.ihy2ln.weaverse.BuildConfig.DEBUG) message else message.take(300)
        runCatching {
            if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
        }.onFailure {
            System.err.println("E/$TAG: $message")
            throwable?.printStackTrace()
        }
    }
}
