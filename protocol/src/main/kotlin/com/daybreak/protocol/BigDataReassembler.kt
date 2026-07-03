package com.daybreak.protocol

/**
 * Reassembles a big-data message from successive BLE notifications. The first chunk
 * carries the 6-byte header; chunks are appended until the buffer holds
 * `payloadLength + 6` bytes.
 */
class BigDataReassembler {

    private val buffer = ArrayList<Byte>()
    private var dataType = -1

    var complete = false
        private set

    /** Feed one notification chunk; returns true once the whole message has arrived. */
    fun offer(chunk: ByteArray): Boolean {
        if (buffer.isEmpty()) {
            if (chunk.size < 2 || ub(chunk[0]) != BigData.CMD) return false
            dataType = ub(chunk[1])
        }
        for (b in chunk) buffer.add(b)

        if (buffer.size >= 4) {
            val payloadLen = ub(buffer[2]) or (ub(buffer[3]) shl 8)
            if (buffer.size >= payloadLen + BigData.HEADER_SIZE) complete = true
        }
        return complete
    }

    fun type(): Int = dataType

    /** The payload bytes (after the 6-byte header). Valid once [complete]. */
    fun payload(): ByteArray {
        val payloadLen = ub(buffer[2]) or (ub(buffer[3]) shl 8)
        return ByteArray(payloadLen) { i -> buffer[BigData.HEADER_SIZE + i] }
    }
}
