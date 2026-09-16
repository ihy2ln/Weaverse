package com.ihy2ln.weaverse.mihon.shizuku

import android.annotation.SuppressLint
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.res.AssetFileDescriptor
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.UserHandle
import com.ihy2ln.weaverse.BuildConfig
import rikka.shizuku.SystemServiceHelper
import java.io.OutputStream
import kotlin.system.exitProcess

/** Runs in Shizuku's privileged user-service process. Derived from Mihon v0.20.4 (Apache-2.0). */
class ShellInterface : IShellInterface.Stub() {
    private val userId = UserHandle::class.java.getMethod("myUserId").invoke(null) as Int
    @SuppressLint("PrivateApi")
    override fun install(apk: AssetFileDescriptor, intentSender: IntentSender) {
        val pm = Class.forName("android.content.pm.IPackageManager\$Stub").getMethod("asInterface", IBinder::class.java)
            .invoke(null, SystemServiceHelper.getSystemService("package"))
        val installer = Class.forName("android.content.pm.IPackageManager").getMethod("getPackageInstaller").invoke(pm)
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            val field = javaClass.getField("installFlags"); field.set(this, field.getInt(this) or 0x00000002)
            if (Build.VERSION.SDK_INT >= 33) setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE)
            if (Build.VERSION.SDK_INT >= 34) setInstallerPackageName(BuildConfig.APPLICATION_ID)
        }
        val sessionId = if (Build.VERSION.SDK_INT >= 31) {
            installer.javaClass.getMethod("createSession", PackageInstaller.SessionParams::class.java, String::class.java, String::class.java, Int::class.java)
                .invoke(installer, params, BuildConfig.APPLICATION_ID, BuildConfig.APPLICATION_ID, userId) as Int
        } else {
            installer.javaClass.getMethod("createSession", PackageInstaller.SessionParams::class.java, String::class.java, Int::class.java)
                .invoke(installer, params, BuildConfig.APPLICATION_ID, userId) as Int
        }
        val session = installer.javaClass.getMethod("openSession", Int::class.java).invoke(installer, sessionId)
        val descriptor = session.javaClass.getMethod("openWrite", String::class.java, Long::class.java, Long::class.java)
            .invoke(session, "extension", 0L, apk.length) as ParcelFileDescriptor
        ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output -> apk.createInputStream().use { it.copyTo(output) } }
        if (Build.VERSION.SDK_INT > 26) session.javaClass.getMethod("commit", IntentSender::class.java, Boolean::class.java).invoke(session, intentSender, false)
        else session.javaClass.getMethod("commit", IntentSender::class.java).invoke(session, intentSender)
    }
    override fun destroy() = exitProcess(0)
}
