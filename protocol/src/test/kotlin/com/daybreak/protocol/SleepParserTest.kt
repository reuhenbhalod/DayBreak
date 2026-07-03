package com.daybreak.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class SleepParserTest {

    private fun u16(value: Int) = byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    @Test
    fun `parses one night with stages and handles midnight crossing`() {
        // dayBytes = 4 (start/end) + 5 stage pairs (10) = 14.
        val day = u16(1380) + u16(420) + byteArrayOf(
            SleepStageType.LIGHT.toByte(), 30,
            SleepStageType.DEEP.toByte(), 60,
            SleepStageType.REM.toByte(), 40,
            SleepStageType.AWAKE.toByte(), 10,
            SleepStageType.LIGHT.toByte(), 20,
        )
        val payload = byteArrayOf(1) + byteArrayOf(0, day.size.toByte()) + day

        val days = SleepParser.parse(payload)
        assertEquals(1, days.size)
        val d = days[0]
        assertEquals(0, d.daysAgo)
        assertEquals(1380, d.sleepStartMin)
        assertEquals(420, d.sleepEndMin)
        assertEquals(480, d.spanMin) // crosses midnight: 420 + 1440 - 1380
        assertEquals(60, d.deepMin)
        assertEquals(40, d.remMin)
        assertEquals(50, d.lightMin) // 30 + 20
        assertEquals(10, d.awakeMin)
        assertEquals(1, d.wakeEvents)
        assertEquals(5, d.stages.size)
    }

    @Test
    fun `parses two nights in one payload`() {
        fun day(daysAgo: Int, start: Int, end: Int): ByteArray {
            val body = u16(start) + u16(end) + byteArrayOf(SleepStageType.DEEP.toByte(), 50)
            return byteArrayOf(daysAgo.toByte(), body.size.toByte()) + body
        }
        val payload = byteArrayOf(2) + day(0, 1380, 420) + day(1, 1350, 400)

        val days = SleepParser.parse(payload)
        assertEquals(2, days.size)
        assertEquals(0, days[0].daysAgo)
        assertEquals(1, days[1].daysAgo)
        assertEquals(50, days[1].deepMin)
    }

    @Test
    fun `empty payload yields no days`() {
        assertEquals(0, SleepParser.parse(ByteArray(0)).size)
    }
}
