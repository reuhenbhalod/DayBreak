package com.daybreak.protocol

/** Command opcodes (tahnok/colmi_r02_client). */
object Command {
    const val SET_TIME = 1
    const val BATTERY = 3
    const val READ_HEART_RATE = 21   // 0x15
    const val GET_STEPS = 67         // 0x43
    const val START_REAL_TIME = 105
    const val STOP_REAL_TIME = 106
}

/** Real-time reading kinds (the first sub-data byte of a real-time command). */
object RealTimeKind {
    const val HEART_RATE = 1
    const val SPO2 = 3
}

/** Builders for the request packets Daybreak sends to the ring. */
object Requests {

    /** CMD_SET_TIME: six BCD time fields + language=1 (english). */
    fun setTime(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): ByteArray =
        Packet.build(
            Command.SET_TIME,
            byteArrayOf(
                toBcd(year % 2000),
                toBcd(month),
                toBcd(day),
                toBcd(hour),
                toBcd(minute),
                toBcd(second),
                1,
            ),
        )

    fun battery(): ByteArray = Packet.build(Command.BATTERY)

    /** CMD_READ_HEART_RATE: little-endian uint32 unix timestamp (seconds) of the target day. */
    fun readHeartRate(epochSeconds: Long): ByteArray {
        val ts = epochSeconds
        val data = ByteArray(4) { i -> ((ts shr (8 * i)) and 0xFF).toByte() }
        return Packet.build(Command.READ_HEART_RATE, data)
    }

    /**
     * CMD_SYNC_ACTIVITY: day offset back from today (0 = today), plus the fixed trailer.
     * Payload matches Gadgetbridge's ColmiR0x format: [daysAgo, 0x0F, 0x00, 0x5F, 0x01]
     * (no extra leading 0x00 — that malformed the request and the R09 replied "no data").
     */
    fun getSteps(dayOffset: Int): ByteArray =
        Packet.build(
            Command.GET_STEPS,
            byteArrayOf(dayOffset.toByte(), 0x0F, 0x00, 0x5F, 0x01),
        )

    fun startRealTime(kind: Int): ByteArray =
        Packet.build(Command.START_REAL_TIME, byteArrayOf(kind.toByte(), 1))

    fun stopRealTime(kind: Int): ByteArray =
        Packet.build(Command.STOP_REAL_TIME, byteArrayOf(kind.toByte(), 0, 0))
}
