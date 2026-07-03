package com.daybreak

import android.app.Application
import com.daybreak.sync.SyncScheduler

class DaybreakApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Schedule the daily background refresh windows (morning/midday/evening) and kick
        // an immediate catch-up sync so the night is ready without opening the app (PRD §8.1).
        SyncScheduler.scheduleDaily(this)
        SyncScheduler.syncNow(this)
    }
}
