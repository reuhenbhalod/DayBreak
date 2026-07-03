package com.daybreak.sync

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.daybreak.DaybreakApp
import com.daybreak.data.SyncLogEntity

/**
 * Background sync skeleton (PRD §8.1). Wires RingClient -> repository -> scoring.
 * The RingClient is a no-op until the BLE layer lands (Increment 2), so this
 * currently just records an attempt; the control flow is in place.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as DaybreakApp).container
        return try {
            val result = container.repository.syncFromRing(container.ringClient)
            log(container, result)
            maybeNotifyMorning(container)
            if (result.startsWith("ok")) Result.success() else Result.retry()
        } catch (t: Throwable) {
            log(container, "error: ${t.message}")
            Result.retry()
        }
    }

    /** Fire the single gentle morning notification once per day, when data is present. */
    private suspend fun maybeNotifyMorning(container: com.daybreak.AppContainer) {
        val today = java.time.LocalDate.now().toString()
        val hour = java.time.LocalTime.now().hour
        if (MorningPolicy.shouldNotify(hour, today, container.settings.lastNotifiedMorning) &&
            container.repository.hasAnyNight()
        ) {
            Notifications.notifyNightReady(applicationContext)
            container.settings.lastNotifiedMorning = today
        }
    }

    /** Lets an expedited sync run as a short foreground service so a transfer survives Doze. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification = Notifications.syncNotification(applicationContext)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Notifications.SYNC_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            ForegroundInfo(Notifications.SYNC_NOTIFICATION_ID, notification)
        }
    }

    private suspend fun log(container: com.daybreak.AppContainer, result: String) {
        container.db.syncLogDao().insert(
            SyncLogEntity(timestampEpochMs = System.currentTimeMillis(), result = result),
        )
    }

    companion object {
        const val UNIQUE_NAME = "daybreak-sync"
    }
}
