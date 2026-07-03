package com.daybreak.protocol

/**
 * Reassembles the multi-packet heart-rate log (CMD_READ_HEART_RATE). Sub-type in
 * `packet[1]` sequences the response:
 *  - 0: metadata — `[2]` total packet count, `[3]` range minutes.
 *  - 1: first data — uint32 LE timestamp at offset 2, then 9 values in `[6..14]`.
 *  - 2+: 13 values each in `[2..14]`.
 * Completes when sub-type == count − 1. Normalizes to 288 five-minute slots.
 */
class HeartRateLogAssembler {

    private companion object {
        const val SLOTS = 288 // 24h / 5min
    }

    private var expectedPackets = 0
    private var rangeMinutes = 0
    private var startTimestamp = 0L
    private val rates = IntArray(SLOTS)
    private var index = 0

    private var started = false

    var complete = false
        private set
    var noData = false
        private set

    /** Feed one packet; returns true once the log is complete. */
    fun offer(packet: ByteArray): Boolean {
        if (ub(packet[0]) != Command.READ_HEART_RATE) return complete
        // 0xFF in the sub-type slot is a terminator. Before any data it means "no log for
        // that day"; after data it just marks the end of the stream (keep what we have).
        if (ub(packet[1]) == 0xFF) {
            if (!started) noData = true
            complete = true
            return true
        }
        started = true
        when (val subType = ub(packet[1])) {
            0 -> {
                expectedPackets = ub(packet[2])
                rangeMinutes = ub(packet[3])
            }

            1 -> {
                startTimestamp = u32le(packet, 2)
                for (i in 0 until 9) put(ub(packet[6 + i]))
            }

            else -> {
                for (i in 0 until 13) put(ub(packet[2 + i]))
                if (expectedPackets > 0 && subType == expectedPackets - 1) complete = true
            }
        }
        return complete
    }

    private fun put(value: Int) {
        if (index < rates.size) rates[index++] = value
    }

    fun result(): HeartRateLog =
        HeartRateLog(startTimestamp, rangeMinutes, rates.toList())
}
