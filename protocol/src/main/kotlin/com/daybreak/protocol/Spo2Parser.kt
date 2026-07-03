package com.daybreak.protocol

/**
 * One day of overnight SpO2: 24 hourly values (the per-hour average of the reported
 * min/max), 0 meaning "no reading" for that hour.
 */
data class Spo2Day(val daysAgo: Int, val hourly: List<Int>) {
    private val valid: List<Int> get() = hourly.filter { it > 0 }
    val average: Int get() = if (valid.isEmpty()) 0 else valid.average().toInt()
    val minimum: Int get() = valid.minOrNull() ?: 0
}

/**
 * Parses the SpO2 big-data payload (after the 6-byte header is stripped). Repeated day
 * blocks: `[daysAgo][24 × (minByte, maxByte)]`, terminated by a `daysAgo` of 0
 * (Gadgetbridge ColmiR0xPacketHandler.historicalSpo2).
 */
object Spo2Parser {
    private const val HOURS = 24

    fun parse(payload: ByteArray): List<Spo2Day> {
        val days = ArrayList<Spo2Day>()
        var p = 0
        while (p < payload.size) {
            val daysAgo = ub(payload[p])
            if (daysAgo == 0) break
            p++
            if (p + HOURS * 2 > payload.size) break

            val hourly = ArrayList<Int>(HOURS)
            for (h in 0 until HOURS) {
                val min = ub(payload[p])
                val max = ub(payload[p + 1])
                p += 2
                hourly.add(if (min == 0 && max == 0) 0 else (min + max + 1) / 2)
            }
            days.add(Spo2Day(daysAgo, hourly))
        }
        return days
    }
}
