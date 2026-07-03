package com.daybreak.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvBuilderTest {

    private fun row(date: String, recovery: Int?) = ExportRow(
        date = date, recovery = recovery, sleep = 80, activity = 90,
        totalSleepMin = 480, deepMin = 90, remMin = 100, lightMin = 290, awakeMin = 20,
        steps = 8000, restingHr = 52,
    )

    @Test
    fun `header is first and rows follow`() {
        val csv = CsvBuilder.build(listOf(row("2026-06-22", 78)))
        val lines = csv.trim().lines()
        assertEquals("date,recovery,sleep,activity,total_sleep_min,deep_min,rem_min,light_min,awake_min,steps,resting_hr", lines[0])
        assertTrue(lines[1].startsWith("2026-06-22,78,80,90,480,90,100,290,20,8000,52"))
    }

    @Test
    fun `null scores render as empty cells`() {
        val csv = CsvBuilder.build(listOf(row("2026-06-01", null)))
        assertTrue(csv.lines()[1].startsWith("2026-06-01,,80,90"))
    }

    @Test
    fun `empty data still emits the header`() {
        assertEquals(1, CsvBuilder.build(emptyList()).trim().lines().size)
    }
}
