package com.daybreak.sync

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
import androidx.core.content.ContextCompat
import com.daybreak.MainActivity
import com.daybreak.R

/** The single morning notification (PRD §8.5): "Your night is ready." Nothing else. */
object Notifications {

    private const val CHANNEL_ID = "daybreak_morning"
    private const val NOTIFICATION_ID = 1001

    private const val SYNC_CHANNEL_ID = "daybreak_sync"
    const val SYNC_NOTIFICATION_ID = 1002

    /** A quiet notification shown only while a sync is actively transferring (foreground service). */
    fun syncNotification(context: Context): android.app.Notification {
        ensureSyncChannel(context)
        return NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_daybreak_notification)
            .setContentText("Updating your data…")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }

    private fun ensureSyncChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            SYNC_CHANNEL_ID,
            "Syncing",
            NotificationManager.IMPORTANCE_MIN,
        ).apply { description = "Shown briefly while reading data from the ring." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notifyNightReady(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        ensureChannel(context)

        val openIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val openApp = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_daybreak_notification)
            .setContentTitle("Your night is ready")
            .setContentText("Tap to see how you slept.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Morning summary",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "A gentle nudge when your night's data is ready." }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
