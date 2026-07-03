package com.daybreak.protocol

/** Single-packet response parsers. Multi-packet responses use the assemblers. */

object BatteryParser {
    /** Battery response: `[3][level][charging]…`. */
    fun parse(packet: ByteArray): BatteryInfo? {
        if (packet.size != Packet.SIZE || ub(packet[0]) != Command.BATTERY) return null
        return BatteryInfo(level = ub(packet[1]), charging = ub(packet[2]) != 0)
    }
}

object RealTimeParser {
    /** Real-time response: `[105][kind][errorCode][value]…`. */
    fun parse(packet: ByteArray): RealTimeReading? {
        if (packet.size != Packet.SIZE || ub(packet[0]) != Command.START_REAL_TIME) return null
        val kind = ub(packet[1])
        val error = ub(packet[2])
        return if (error != 0) {
            RealTimeReading.Err(kind, error)
        } else {
            RealTimeReading.Value(kind, ub(packet[3]))
        }
    }
}
