package com.daybreak.scoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryScoreTest {

    private val baselines = TestFixtures.baselines()

    @Test
    fun `lower resting HR than baseline scores higher`() {
        val low = RecoveryScore.score(TestFixtures.goodNight(minBpm = 46), baselines, previousNightSleepScore = 80)
        val high = RecoveryScore.score(TestFixtures.goodNight(minBpm = 60), baselines, previousNightSleepScore = 80)
        assertTrue("low-RHR ${low.value} should beat high-RHR ${high.value}", low.value > high.value)
    }

    @Test
    fun `an early nightly HR low scores the recovery index high`() {
        val earlyLow = RecoveryScore.recoveryIndexScore(TestFixtures.nightHr(50))
        val lateLow = RecoveryScore.recoveryIndexScore(
            listOf(HrSample(0, 64), HrSample(120, 62), HrSample(240, 60), HrSample(480, 50)),
        )
        assertTrue(earlyLow > lateLow)
    }

    @Test
    fun `with HRV present there are five contributors totaling 100`() {
        val result = RecoveryScore.score(TestFixtures.goodNight(hrvMs = 45.0), baselines, 80)
        assertEquals(5, result.contributors.size)
        assertEquals(100, result.contributors.sumOf { it.weightPct })
    }
}
