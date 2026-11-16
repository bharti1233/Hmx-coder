package com.hmx.harness.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hmx.harness.MainActivity
import com.hmx.harness.R
import com.hmx.harness.AppNotifications

class UpdateNotificationManager(private val context: Context) {
    fun notifyAvailable(release: UpdateRelease): Boolean {
        if (!AppNotifications.enabled(context)) return false
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).putExtra(EXTRA_OPEN_UPDATES, true)
        val pending = PendingIntent.getActivity(
            context, 7301, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("HMXHARNESS update available")
            .setContentText("Version ${release.versionName} is ready to review.")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        return true
    }

    fun cancel() = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Stable HMXHARNESS update notifications"
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "app-updates"
        const val EXTRA_OPEN_UPDATES = "open_updates"
        private const val NOTIFICATION_ID = 7302
    }
}
