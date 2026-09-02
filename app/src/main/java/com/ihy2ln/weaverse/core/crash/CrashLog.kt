package com.ihy2ln.weaverse.core.crash

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashLog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { CrashLogStore.write(context, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
                ?: kotlin.system.exitProcess(10)
        }
    }

    fun latestText(): String = CrashLogStore.latestText(context)
}
