package com.daybreak.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketTest {

    @Test
    fun `packet is 16 bytes with command first`() {
        val p = Packet.build(Command.BATTERY)
        assertEquals(16, p.size)
        assertEquals(Command.BATTERY, ub(p[0]))
    }

    @Test
    fun `checksum is low byte of the sum of the first 15 bytes`() {
        // cmd=3, all else zero -> checksum 3.
        val p = Packet.build(Command.BATTERY)
        assertEquals(3, ub(p[15]))
        assertTrue(Packet.isValid(p))
    }

    @Test
    fun `checksum wraps mod 256`() {
        // sub-data summing past 255 must wrap, not saturate.
        val p = Packet.build(0xF0, byteArrayOf(0xF0.toByte(), 0x30))
        val expected = (0xF0 + 0xF0 + 0x30) and 0xFF
        assertEquals(expected, ub(p[15]))
    }

    @Test
    fun `tampered packet fails validation`() {
        val p = Packet.build(Command.BATTERY, byteArrayOf(50, 1))
        p[1] = 99
        assertFalse(Packet.isValid(p))
    }

    @Test
    fun `subdata longer than 14 bytes is rejected`() {
        try {
            Packet.build(1, ByteArray(15))
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
