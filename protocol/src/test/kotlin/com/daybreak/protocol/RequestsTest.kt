package com.daybreak.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestsTest {

    @Test
    fun `setTime encodes BCD fields and english language`() {
        val p = Requests.setTime(2026, 6, 22, 14, 5, 9)
        assertEquals(Command.SET_TIME, ub(p[0]))
        assertEquals(0x26, ub(p[1])) // year % 2000 = 26 -> 0x26
        assertEquals(0x06, ub(p[2]))
        assertEquals(0x22, ub(p[3])) // day 22 -> 0x22
        assertEquals(0x14, ub(p[4])) // hour 14 -> 0x14
        assertEquals(0x05, ub(p[5]))
        assertEquals(0x09, ub(p[6]))
        assertEquals(1, ub(p[7]))
        assertTrue(Packet.isValid(p))
    }

    @Test
    fun `readHeartRate encodes a little-endian uint32 timestamp`() {
        val p = Requests.readHeartRate(0x01020304L)
        assertEquals(Command.READ_HEART_RATE, ub(p[0]))
        assertEquals(0x04, ub(p[1]))
        assertEquals(0x03, ub(p[2]))
        assertEquals(0x02, ub(p[3]))
        assertEquals(0x01, ub(p[4]))
    }

    @Test
    fun `getSteps carries the day offset and fixed trailer (Gadgetbridge format)`() {
        val p = Requests.getSteps(0)
        assertEquals(Command.GET_STEPS, ub(p[0]))
        assertEquals(0, ub(p[1]))     // day offset
        assertEquals(0x0F, ub(p[2]))
        assertEquals(0x00, ub(p[3]))
        assertEquals(0x5F, ub(p[4]))
        assertEquals(0x01, ub(p[5]))
    }

    @Test
    fun `real-time start and stop carry the reading kind`() {
        val start = Requests.startRealTime(RealTimeKind.HEART_RATE)
        assertEquals(Command.START_REAL_TIME, ub(start[0]))
        assertEquals(RealTimeKind.HEART_RATE, ub(start[1]))
        assertEquals(1, ub(start[2])) // Action.START

        val stop = Requests.stopRealTime(RealTimeKind.SPO2)
        assertEquals(Command.STOP_REAL_TIME, ub(stop[0]))
        assertEquals(RealTimeKind.SPO2, ub(stop[1]))
    }
}
