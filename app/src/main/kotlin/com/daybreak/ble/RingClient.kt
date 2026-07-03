package com.daybreak.ble

import com.daybreak.protocol.BatteryInfo
import com.daybreak.protocol.DailyActivitySummary
import com.daybreak.protocol.HeartRateLog
import com.daybreak.protocol.SleepDay
import com.daybreak.protocol.Spo2Day
import com.daybreak.scoring.NightData
import kotlinx.coroutines.flow.StateFlow

/** One night decoded from the ring, tagged with its ISO-8601 date (used for seeding/sleep). */
data class DecodedNight(val date: String, val data: NightData)

/**
 * Boundary to the COLMI R09 ring. Increment 2 implements battery, the daily heart-rate
 * log, and daily step/activity totals over native GATT (see [RealRingClient]). Sleep
 * staging (the 0xBC big-data protocol) is Increment 2b; [NoopRingClient] keeps the app
 * runnable without a ring.
 */
interface RingClient {
    /** Live, human-readable BLE event log (tx/rx/connection) for the diagnostics screen. */
    val events: StateFlow<List<String>>

    suspend fun connect(): Boolean
    suspend fun disconnect()
    /** Set the ring's clock to the phone's time so its day-keyed records align with ours. */
    suspend fun setTime()
    /** Measure heart rate live via the ring's real-time mode; calls [onReading] for each
     *  non-zero BPM as it streams, and returns the final reading (or null if it couldn't). */
    suspend fun measureHeartRate(onReading: (Int) -> Unit): Int?
    suspend fun fetchBattery(): BatteryInfo?
    /** Recorded heart rate for the day containing [epochSecondsOfDay] (288 five-minute slots). */
    suspend fun fetchHeartRateLog(epochSecondsOfDay: Long): HeartRateLog?
    /** Step/calorie/distance totals for [dayOffset] days back (0 = today). */
    suspend fun fetchDailyActivity(dayOffset: Int): DailyActivitySummary?
    /** Recorded sleep sessions (one per night) via the big-data protocol. */
    suspend fun fetchSleep(): List<SleepDay>?
    /** Overnight SpO2 history via the big-data protocol. */
    suspend fun fetchSpo2(): List<Spo2Day>?
}
