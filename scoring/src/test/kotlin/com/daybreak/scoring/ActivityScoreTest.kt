package com.daybreak.scoring

import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityScoreTest {

    private val baselines = TestFixtures.baselines()

    @Test
    fun `weights total 100 and value stays in range`() {
        val result = ActivityScore.score(TestFixtures.goodNight(), baselines, recoveryValue = 80)
        assertTrue(result.contributors.sumOf { it.weightPct } == 100)
        assertTrue(result.value in 0..100)
    }

    @Test
    fun `low recovery lowers the step goal so the same steps score higher`() {
        val highGoal = ActivityScore.stepGoal(baselineSteps = 8000.0, recoveryValue = 80)
        val lowGoal = ActivityScore.stepGoal(baselineSteps = 8000.0, recoveryValue = 40)
        assertTrue("low-recovery goal $lowGoal should be below normal goal $highGoal", lowGoal < highGoal)
    }

    @Test
    fun `a long sedentary stretch lowers the score`() {
        val active = ActivityScore.score(TestFixtures.goodNight(sedentaryMin = 60), baselines, 80)
        val sluggish = ActivityScore.score(TestFixtures.goodNight(sedentaryMin = 300), baselines, 80)
        assertTrue(active.value > sluggish.value)
    }
}
