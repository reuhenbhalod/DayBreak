package com.daybreak.protocol

/** Parsed battery status. */
data class BatteryInfo(val level: Int, val charging: Boolean)

/** A real-time reading response (heart rate or SpO2), or an error code from the ring. */
sealed interface RealTimeReading {
    data class Value(val kind: Int, val value: Int) : RealTimeReading
    data class Err(val kind: Int, val code: Int) : RealTimeReading
}

/**
 * A full day's recorded heart rate: 288 entries at 5-minute intervals starting at
 * [startTimestampSeconds]. A value of 0 means "no reading" for that slot.
 */
data class HeartRateLog(
    val startTimestampSeconds: Long,
    val rangeMinutes: Int,
    val rates: List<Int>,
)

/** Aggregated activity for one day, summed from the 15-minute interval packets. */
data class DailyActivitySummary(
    val totalSteps: Int,
    val totalCalories: Int,
    val totalDistanceMeters: Int,
    val noData: Boolean,
)
