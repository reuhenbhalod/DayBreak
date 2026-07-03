package com.daybreak.protocol

/**
 * COLMI "big data V2" protocol — used for sleep (and SpO2). Unlike the 16-byte UART
 * commands, big data flows over a dedicated GATT service (notify `de5bf729-…`,
 * command `de5bf72a-…`) as a variable-length message reassembled from notifications.
 *
 * Message: `[0xBC][type][len_lo][len_hi][crc16_lo][crc16_hi]` then `len` payload bytes.
 * Gadgetbridge does not validate the CRC, so neither do we (the field is still skipped).
 */
object BigData {
    const val CMD = 0xBC
    const val HEADER_SIZE = 6

    object Type {
        const val SLEEP = 0x27
        const val SPO2 = 0x2A
    }

    /** Raw request written to the V2 command characteristic (not the 16-byte packet format). */
    fun sleepRequest(): ByteArray =
        byteArrayOf(CMD.toByte(), Type.SLEEP.toByte(), 0x01, 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte())

    fun spo2Request(): ByteArray =
        byteArrayOf(CMD.toByte(), Type.SPO2.toByte(), 0x01, 0x00, 0xFF.toByte(), 0x00, 0xFF.toByte())
}
