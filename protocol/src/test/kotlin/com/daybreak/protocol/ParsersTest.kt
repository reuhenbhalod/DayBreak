package com.daybreak.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {

    @Test
    fun `battery response parses level and charging`() {
        val charging = BatteryParser.parse(Packet.build(Command.BATTERY, byteArrayOf(85, 1)))
        assertEquals(BatteryInfo(85, true), charging)

        val notCharging = BatteryParser.parse(Packet.build(Command.BATTERY, byteArrayOf(40, 0)))
        assertEquals(BatteryInfo(40, false), notCharging)
    }

    @Test
    fun `battery parser rejects a non-battery packet`() {
        assertNull(BatteryParser.parse(Packet.build(Command.GET_STEPS)))
    }

    @Test
    fun `real-time value parses heart rate`() {
        val r = RealTimeParser.parse(Packet.build(Command.START_REAL_TIME, byteArrayOf(1, 0, 72)))
        assertTrue(r is RealTimeReading.Value)
        r as RealTimeReading.Value
        assertEquals(RealTimeKind.HEART_RATE, r.kind)
        assertEquals(72, r.value)
    }

    @Test
    fun `real-time error code is surfaced`() {
        val r = RealTimeParser.parse(Packet.build(Command.START_REAL_TIME, byteArrayOf(1, 5, 0)))
        assertEquals(RealTimeReading.Err(1, 5), r)
    }
}
