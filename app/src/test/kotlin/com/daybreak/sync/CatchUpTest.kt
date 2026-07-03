package com.daybreak.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class CatchUpTest {

    @Test
    fun `synced today pulls only today`() {
        assertEquals(listOf(0), CatchUp.offsets(0))
    }

    @Test
    fun `a gap pulls every missed day plus today`() {
        assertEquals(listOf(0, 1, 2, 3), CatchUp.offsets(3))
    }

    @Test
    fun `a long gap is capped`() {
        assertEquals((0..CatchUp.MAX_BACKFILL_DAYS).toList(), CatchUp.offsets(30))
    }

    @Test
    fun `negative gaps are clamped to today`() {
        assertEquals(listOf(0), CatchUp.offsets(-5))
    }
}
