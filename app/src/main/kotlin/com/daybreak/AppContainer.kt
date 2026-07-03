package com.daybreak

import android.content.Context
import com.daybreak.ble.RealRingClient
import com.daybreak.ble.RingClient
import com.daybreak.data.DaybreakDatabase
import com.daybreak.data.DaybreakRepository
import com.daybreak.data.SettingsStore
import com.daybreak.summary.LlmSummarizer

/**
 * Manual dependency container (no Hilt yet — fewer moving parts for the MVP).
 * [ringClient] is the real BLE client; it safely no-ops (connect returns false) when
 * Bluetooth is off or permissions are missing, e.g. on an emulator.
 */
class AppContainer(context: Context) {
    val db: DaybreakDatabase = DaybreakDatabase.get(context)
    val settings: SettingsStore = SettingsStore(context.applicationContext)
    val ringClient: RingClient = RealRingClient(context.applicationContext)
    val repository: DaybreakRepository =
        DaybreakRepository(db, settings, LlmSummarizer(context.applicationContext))
}
