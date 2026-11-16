package com.hmx.harness.data.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

class UpdateInstaller(
    private val context: Context,
    private val store: UpdateStore,
) {
    sealed interface Result {
        data object Started : Result
        data object PermissionRequired : Result
        data class Failed(val message: String) : Result
    }

    fun install(file: File, release: UpdateRelease): Result {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            return Result.PermissionRequired
        }
        var sessionId = -1
        return try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setSize(file.length())
            }
            val installer = context.packageManager.packageInstaller
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, UpdateInstallReceiver::class.java).apply {
                    action = UpdateInstallReceiver.ACTION
                    putExtra(UpdateInstallReceiver.EXTRA_VERSION_CODE, release.versionCode)
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                store.lastInstallVersionCode = release.versionCode
                store.lastInstallStatus = PackageInstaller.STATUS_PENDING_USER_ACTION
                session.commit(callback.intentSender)
            }
            Result.Started
        } catch (error: Throwable) {
            if (sessionId >= 0) {
                runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
            }
            Result.Failed(error.message ?: "Android could not start the installer.")
        }
    }
}
