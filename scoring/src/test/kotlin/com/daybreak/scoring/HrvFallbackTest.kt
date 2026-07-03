package com.daybreak.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The resolved PRD §10.2 fallback: HRV missing -> +10% Sleep (40), +10% RHR (35). */
class HrvFallbackTest {

    @Test
    fun `missing HRV redistributes weight and still totals 100`() {
        val baselines = TestFixtures.baselines(hrvMs = null)
        val result = RecoveryScore.score(TestFixtures.goodNight(hrvMs = null), baselines, 80)

        assertEquals(4, result.contributors.size)
        assertEquals(100, result.contributors.sumOf { it.weightPct })
        assertNull(result.contributors.firstOrNull { it.name == "HRV balance" })

        val sleep = result.contributors.first { it.name == "Previous-night sleep" }
        val rhr = result.contributors.first { it.name == "Resting HR vs baseline" }
        assertEquals(40, sleep.weightPct)
        assertEquals(35, rhr.weightPct)
    }

    @Test
    fun `baseline has null HRV when no night ever reported it`() {
        val baselines = TestFixtures.baselines(hrvMs = null)
        assertNull(baselines.hrv)
    }

    @Test
    fun `fallback still produces an in-range score`() {
        val baselines = TestFixtures.baselines(hrvMs = null)
        val result = RecoveryScore.score(TestFixtures.goodNight(hrvMs = null), baselines, 80)
        assertTrue(result.value in 0..100)
    }
}
