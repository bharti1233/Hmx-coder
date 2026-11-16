package com.hmx.harness.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.app.PendingIntent
import kotlinx.coroutines.flow.MutableSharedFlow

sealed interface InstallOutcome {
    data object Success : InstallOutcome
    data object Cancelled : InstallOutcome
    data class Failed(val message: String) : InstallOutcome
}

data class InstallEvent(val versionCode: Long, val outcome: InstallOutcome)

object UpdateInstallEvents {
    val events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 4)
}

class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmation = intent.getParcelableExtraCompat(Intent.EXTRA_INTENT) ?: return
            confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(confirmation) }
            return
        }
        val versionCode = intent.getLongExtra(EXTRA_VERSION_CODE, UpdateStore(context).lastInstallVersionCode)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        val outcome = when (status) {
            PackageInstaller.STATUS_SUCCESS -> InstallOutcome.Success
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.Cancelled
            else -> InstallOutcome.Failed(message.ifBlank { "Android cancelled the installation." })
        }
        UpdateStore(context).rememberInstallResult(status, versionCode, message)
        UpdateInstallEvents.events.tryEmit(InstallEvent(versionCode, outcome))
    }

    companion object {
        const val ACTION = "com.hmx.harness.action.UPDATE_INSTALL_STATUS"
        const val EXTRA_VERSION_CODE = "update_version_code"
    }
}

@Suppress("DEPRECATION")
private fun Intent.getParcelableExtraCompat(key: String): Intent? =
    if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Intent::class.java) else getParcelableExtra(key)
