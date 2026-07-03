package com.daybreak.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringEngineTest {

    @Test
    fun `fewer than the warmup nights yields Calibrating`() {
        val state = ScoringEngine.compute(TestFixtures.goodNight(), TestFixtures.history(n = 5))
        assertTrue(state is ScoringState.Calibrating)
        val calibrating = state as ScoringState.Calibrating
        assertEquals(5, calibrating.nightsObserved)
        assertEquals(7, calibrating.nightsRequired)
    }

    @Test
    fun `exactly the warmup nights yields Ready with all scores in range`() {
        val state = ScoringEngine.compute(TestFixtures.goodNight(), TestFixtures.history(n = 7))
        assertTrue(state is ScoringState.Ready)
        val ready = state as ScoringState.Ready
        for (score in listOf(ready.sleep, ready.recovery, ready.activity)) {
            assertTrue("score ${score.value} out of range", score.value in 0..100)
        }
        assertTrue(ready.summary.isNotBlank())
    }

    @Test
    fun `a good night against good baselines scores well`() {
        val state = ScoringEngine.compute(TestFixtures.goodNight(), TestFixtures.history())
        val ready = state as ScoringState.Ready
        assertTrue("sleep should be solid: ${ready.sleep.value}", ready.sleep.value >= 65)
        assertTrue("recovery should be solid: ${ready.recovery.value}", ready.recovery.value >= 60)
    }
}
