package com.ihy2ln.weaverse.data.backup

import android.content.Context

object AutoBackupScheduler {
    fun ensure(context: Context) = AutoBackupWorker.schedule(context)
    fun cancel(context: Context) = AutoBackupWorker.cancel(context)
}
