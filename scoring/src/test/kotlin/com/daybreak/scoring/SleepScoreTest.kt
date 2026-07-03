package com.daybreak.scoring

import org.junit.Assert.assertTrue
import org.junit.Test

class SleepScoreTest {

    private val baselines = TestFixtures.baselines()

    @Test
    fun `weights total 100 and value stays in range`() {
        val result = SleepScore.score(TestFixtures.goodNight(), baselines)
        assertTrue(result.contributors.sumOf { it.weightPct } == 100)
        assertTrue(result.value in 0..100)
    }

    @Test
    fun `more sleep scores higher than short sleep`() {
        val full = SleepScore.score(TestFixtures.goodNight(totalSleepMin = 480), baselines)
        val short = SleepScore.score(TestFixtures.goodNight(totalSleepMin = 300), baselines)
        assertTrue("full ${full.value} should beat short ${short.value}", full.value > short.value)
    }

    @Test
    fun `many wake events lower the score`() {
        val calm = SleepScore.score(TestFixtures.goodNight(wakeEvents = 0), baselines)
        val restless = SleepScore.score(TestFixtures.goodNight(wakeEvents = 8), baselines)
        assertTrue(calm.value > restless.value)
    }

    @Test
    fun `age changes the sleep need target`() {
        assertTrue(SleepScore.sleepNeedMin(12) > SleepScore.sleepNeedMin(40))
        assertTrue(SleepScore.sleepNeedMin(40) > SleepScore.sleepNeedMin(70))
    }
}
