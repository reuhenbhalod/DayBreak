package com.daybreak.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class Spo2ParserTest {

    private fun day(daysAgo: Int, minMax: Pair<Int, Int>): ByteArray {
        val b = ByteArray(1 + 48)
        b[0] = daysAgo.toByte()
        for (h in 0 until 24) {
            b[1 + h * 2] = minMax.first.toByte()
            b[2 + h * 2] = minMax.second.toByte()
        }
        return b
    }

    @Test
    fun `parses a day and averages hourly min and max`() {
        val payload = day(1, 94 to 98) + byteArrayOf(0) // terminator
        val days = Spo2Parser.parse(payload)
        assertEquals(1, days.size)
        assertEquals(1, days[0].daysAgo)
        assertEquals(24, days[0].hourly.size)
        assertEquals(96, days[0].average) // (94+98)/2
        assertEquals(96, days[0].minimum)
    }

    @Test
    fun `parses multiple days until the zero terminator`() {
        val payload = day(1, 95 to 97) + day(2, 90 to 92) + byteArrayOf(0)
        val days = Spo2Parser.parse(payload)
        assertEquals(2, days.size)
        assertEquals(96, days[0].average)
        assertEquals(91, days[1].average)
    }

    @Test
    fun `ignores hours with no reading`() {
        val b = ByteArray(1 + 48)
        b[0] = 1
        // only first hour has data (95/97), rest are zero
        b[1] = 95; b[2] = 97
        val days = Spo2Parser.parse(b)
        assertEquals(96, days[0].average) // only the valid hour counts
    }
}
