package com.ihy2ln.weaverse.core

import android.content.Context
import android.content.Intent

/** Relaunches Weaverse in a fresh process, e.g. after a restore replaced the library under an open database. */
object AppRestarter {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val component = launch.component ?: return
        context.startActivity(Intent.makeRestartActivityTask(component))
        Runtime.getRuntime().exit(0)
    }
}
