package com.daybreak.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BigDataReassemblerTest {

    private fun message(type: Int, payload: ByteArray): ByteArray {
        val len = payload.size
        val header = byteArrayOf(
            BigData.CMD.toByte(),
            type.toByte(),
            (len and 0xFF).toByte(),
            ((len shr 8) and 0xFF).toByte(),
            0x00, 0x00, // crc placeholder — not validated
        )
        return header + payload
    }

    @Test
    fun `reassembles a message split across two notifications`() {
        val payload = ByteArray(20) { it.toByte() }
        val full = message(BigData.Type.SLEEP, payload)
        val first = full.copyOfRange(0, 10)
        val rest = full.copyOfRange(10, full.size)

        val r = BigDataReassembler()
        assertFalse(r.offer(first))
        assertTrue(r.offer(rest))
        assertEquals(BigData.Type.SLEEP, r.type())
        assertArrayEquals(payload, r.payload())
    }

    @Test
    fun `completes from a single chunk`() {
        val payload = byteArrayOf(1, 2, 3)
        val r = BigDataReassembler()
        assertTrue(r.offer(message(BigData.Type.SLEEP, payload)))
        assertArrayEquals(payload, r.payload())
    }

    @Test
    fun `ignores a chunk that does not start with the big-data command`() {
        val r = BigDataReassembler()
        assertFalse(r.offer(byteArrayOf(0x01, 0x02, 0x03)))
    }
}
