package com.ihy2ln.weaverse.ai

import android.util.Log

/** Logcat helper that degrades gracefully in JVM unit tests. */
object WeaverseAiLog {
    const val TAG = "WeaverseAI"

    fun i(message: String) {
        runCatching { Log.i(TAG, message) }.onFailure {
            println("I/$TAG: $message")
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        runCatching {
            if (throwable != null) Log.e(TAG, message, throwable) else Log.e(TAG, message)
        }.onFailure {
            System.err.println("E/$TAG: $message")
            throwable?.printStackTrace()
        }
    }
}
