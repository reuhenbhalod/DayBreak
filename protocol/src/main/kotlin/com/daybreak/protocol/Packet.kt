package com.daybreak.protocol

/**
 * COLMI R0x packet framing. Every command and response is exactly 16 bytes:
 * `[0]` command, `[1..14]` sub-data (zero-padded), `[15]` checksum.
 *
 * Checksum is `sum(packet) & 0xFF` (verified against tahnok/colmi_r02_client
 * `packet.py`: `return sum(packet) & 255`). Since byte 15 is 0 while building,
 * that equals the sum of the first 15 bytes mod 256 (PRD §12).
 */
object Packet {
    const val SIZE = 16
    const val MAX_SUBDATA = 14

    fun build(command: Int, subData: ByteArray = ByteArray(0)): ByteArray {
        require(subData.size <= MAX_SUBDATA) { "subData too long: ${subData.size} > $MAX_SUBDATA" }
        val packet = ByteArray(SIZE)
        packet[0] = command.toByte()
        subData.copyInto(packet, destinationOffset = 1)
        packet[SIZE - 1] = checksum(packet)
        return packet
    }

    /** Sum of the first 15 bytes, low byte (mod 256). */
    fun checksum(packet: ByteArray): Byte {
        var sum = 0
        for (i in 0 until SIZE - 1) sum += packet[i].toInt() and 0xFF
        return (sum and 0xFF).toByte()
    }

    fun isValid(packet: ByteArray): Boolean =
        packet.size == SIZE && packet[SIZE - 1] == checksum(packet)
}

/** Unsigned byte value (0..255). */
internal fun ub(b: Byte): Int = b.toInt() and 0xFF

/** Little-endian unsigned 16-bit from two bytes at [offset]. */
internal fun u16le(packet: ByteArray, offset: Int): Int =
    ub(packet[offset]) or (ub(packet[offset + 1]) shl 8)

/** Little-endian unsigned 32-bit from four bytes at [offset]. */
internal fun u32le(packet: ByteArray, offset: Int): Long {
    var v = 0L
    for (i in 0 until 4) v = v or (ub(packet[offset + i]).toLong() shl (8 * i))
    return v
}
