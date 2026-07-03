package com.daybreak.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TagMarksTest {

    private val dates = listOf("2026-06-20", "2026-06-21", "2026-06-22", "2026-06-23")

    @Test
    fun `marks the positions of tagged dates`() {
        val marks = TagMarks.indices(dates, setOf("2026-06-21", "2026-06-23"))
        assertEquals(setOf(1, 3), marks)
    }

    @Test
    fun `untagged or out-of-range dates produce no marks`() {
        assertEquals(emptySet<Int>(), TagMarks.indices(dates, setOf("2026-01-01")))
        assertEquals(emptySet<Int>(), TagMarks.indices(dates, emptySet()))
    }
}
