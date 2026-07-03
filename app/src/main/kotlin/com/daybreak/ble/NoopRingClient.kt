package com.daybreak.ble

import com.daybreak.protocol.BatteryInfo
import com.daybreak.protocol.DailyActivitySummary
import com.daybreak.protocol.HeartRateLog
import com.daybreak.protocol.SleepDay
import com.daybreak.protocol.Spo2Day
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Placeholder ring client used when BLE is unavailable or no ring is paired. */
class NoopRingClient : RingClient {
    override val events: StateFlow<List<String>> = MutableStateFlow(emptyList())
    override suspend fun connect(): Boolean = false
    override suspend fun disconnect() = Unit
    override suspend fun setTime() = Unit
    override suspend fun measureHeartRate(onReading: (Int) -> Unit): Int? = null
    override suspend fun fetchBattery(): BatteryInfo? = null
    override suspend fun fetchHeartRateLog(epochSecondsOfDay: Long): HeartRateLog? = null
    override suspend fun fetchDailyActivity(dayOffset: Int): DailyActivitySummary? = null
    override suspend fun fetchSleep(): List<SleepDay>? = null
    override suspend fun fetchSpo2(): List<Spo2Day>? = null
}
