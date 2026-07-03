package com.daybreak.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Background refresh cadence (PRD §8.1): a guaranteed morning window plus midday and
 * evening top-ups, each a daily periodic job with exponential backoff on failure.
 * [syncNow] is an expedited one-time sync used for self-heal (app open / boot).
 */
object SyncScheduler {

    private val WINDOWS = mapOf("morning" to 7, "midday" to 13, "evening" to 20)
    private const val BACKOFF_MINUTES = 10L

    fun scheduleDaily(context: Context) {
        val wm = WorkManager.getInstance(context)
        // Remove the legacy single periodic job from earlier versions.
        wm.cancelUniqueWork("daybreak-sync")
        WINDOWS.forEach { (name, hour) ->
            val request = PeriodicWorkRequestBuilder<SyncWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(minutesUntilHour(hour), TimeUnit.MINUTES)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork("daybreak-sync-$name", ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }

    /** Self-heal: run a sync as soon as possible (app foreground, boot, proximity). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork("daybreak-sync-now", ExistingWorkPolicy.REPLACE, request)
    }

    /** Minutes from now until the next occurrence of [hour]:00 local time. */
    private fun minutesUntilHour(hour: Int): Long {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(hour, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMinutes().coerceAtLeast(1)
    }
}
