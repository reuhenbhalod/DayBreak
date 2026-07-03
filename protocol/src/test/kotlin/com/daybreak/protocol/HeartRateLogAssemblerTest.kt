package com.daybreak.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeartRateLogAssemblerTest {

    private fun meta(count: Int, range: Int) =
        Packet.build(Command.READ_HEART_RATE, byteArrayOf(0, count.toByte(), range.toByte()))

    private fun firstData(timestamp: Long, values: IntArray): ByteArray {
        val sub = ByteArray(14)
        sub[0] = 1
        for (i in 0 until 4) sub[1 + i] = ((timestamp shr (8 * i)) and 0xFF).toByte()
        for (i in values.indices) sub[5 + i] = values[i].toByte() // packet[6..] = sub[5..]
        return Packet.build(Command.READ_HEART_RATE, sub)
    }

    private fun moreData(subType: Int, values: IntArray): ByteArray {
        val sub = ByteArray(14)
        sub[0] = subType.toByte()
        for (i in values.indices) sub[1 + i] = values[i].toByte() // packet[2..] = sub[1..]
        return Packet.build(Command.READ_HEART_RATE, sub)
    }

    @Test
    fun `assembles a three-packet log and completes on the last`() {
        val a = HeartRateLogAssembler()
        assertFalse(a.offer(meta(count = 3, range = 5)))
        assertFalse(a.offer(firstData(timestamp = 1_700_000_000L, values = IntArray(9) { 60 })))
        assertTrue(a.offer(moreData(subType = 2, values = IntArray(13) { 61 })))

        val log = a.result()
        assertEquals(1_700_000_000L, log.startTimestampSeconds)
        assertEquals(5, log.rangeMinutes)
        assertEquals(288, log.rates.size)
        assertEquals(60, log.rates[0])
        assertEquals(60, log.rates[8])
        assertEquals(61, log.rates[9])
        assertEquals(61, log.rates[21])
        assertEquals(0, log.rates[22]) // untouched slots stay zero
    }

    @Test
    fun `a 0xFF sub-type means no heart-rate data and completes immediately`() {
        val a = HeartRateLogAssembler()
        val noData = Packet.build(Command.READ_HEART_RATE, byteArrayOf(0xFF.toByte()))
        assertTrue(a.offer(noData))
        assertTrue(a.noData)
    }

    @Test
    fun `ignores packets for other commands`() {
        val a = HeartRateLogAssembler()
        assertFalse(a.offer(Packet.build(Command.BATTERY, byteArrayOf(50, 0))))
    }
}
