package com.daybreak.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Re-establishes the sync schedule and runs a catch-up sync after a reboot, so the ring
 * keeps syncing without the user reopening the app (PRD §8.1 self-heal).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SyncScheduler.scheduleDaily(context)
            SyncScheduler.syncNow(context)
        }
    }
}
