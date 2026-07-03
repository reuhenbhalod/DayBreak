package com.daybreak.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StepsAssemblerTest {

    /** Build one interval packet (steps < 256 keeps the high byte zero). */
    private fun interval(page: Int, total: Int, timeIndex: Int, steps: Int, calories: Int, distance: Int): ByteArray {
        val sub = ByteArray(12)
        sub[0] = toBcd(26)            // year -> packet[1]
        sub[1] = toBcd(6)             // month -> packet[2]
        sub[2] = toBcd(22)            // day -> packet[3]
        sub[3] = timeIndex.toByte()   // packet[4]
        sub[4] = page.toByte()        // packet[5]
        sub[5] = total.toByte()       // packet[6]
        sub[6] = calories.toByte()    // packet[7] (lo); [8] hi = 0
        sub[8] = steps.toByte()       // packet[9] (lo); [10] hi = 0
        sub[10] = distance.toByte()   // packet[11] (lo); [12] hi = 0
        return Packet.build(Command.GET_STEPS, sub)
    }

    @Test
    fun `sums steps across pages and completes on the last page`() {
        val a = StepsAssembler()
        assertFalse(a.offer(interval(page = 0, total = 2, timeIndex = 32, steps = 100, calories = 10, distance = 70)))
        assertTrue(a.offer(interval(page = 1, total = 2, timeIndex = 33, steps = 200, calories = 20, distance = 140)))

        val summary = a.summary()
        assertEquals(300, summary.totalSteps)
        assertEquals(30, summary.totalCalories)
        assertEquals(210, summary.totalDistanceMeters)
        assertFalse(summary.noData)
        assertEquals(2, a.intervals().size)
    }

    @Test
    fun `a 255 marker means no data for the day`() {
        val a = StepsAssembler()
        assertTrue(a.offer(Packet.build(Command.GET_STEPS, byteArrayOf(255.toByte()))))
        assertTrue(a.summary().noData)
        assertEquals(0, a.summary().totalSteps)
    }
}
