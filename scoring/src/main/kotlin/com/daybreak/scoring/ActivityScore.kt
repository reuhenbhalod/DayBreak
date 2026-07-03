package com.daybreak.scoring

/**
 * Activity Score (PRD §10.3). The step goal lowers on low-recovery days, so this
 * scorer takes the recovery value as input — fixing the compute order
 * Sleep -> Recovery -> Activity.
 */
object ActivityScore {

    private const val DEFAULT_STEP_GOAL = 8000.0

    fun score(today: NightData, baselines: Baselines, recoveryValue: Int): ScoreResult {
        val a = today.activity
        val goal = stepGoal(baselines.steps, recoveryValue)

        val stepsNorm = Normalize.fraction(a.steps / goal)
        val activeNorm = Normalize.higherBetter(
            a.activeMinutes.toDouble(), baselines.activeMinutes, spread = 30.0,
        )
        val sedentaryNorm = sedentaryScore(a.longestSedentaryStretchMin)
        val balanceNorm = Normalize.closeness(
            a.steps.toDouble(), target = baselines.steps, tolerance = baselines.steps.coerceAtLeast(1.0),
        )

        val contributors = listOf(
            Contributor("Steps vs goal", 35, stepsNorm),
            Contributor("Active minutes", 25, activeNorm),
            Contributor("Sedentary penalty", 20, sedentaryNorm),
            Contributor("Activity balance", 20, balanceNorm),
        )
        return ScoreResult(weightedValue(contributors), contributors)
    }

    /** Goal is the wearer's baseline step count, lowered to 70% on low-recovery days. */
    internal fun stepGoal(baselineSteps: Double, recoveryValue: Int): Double {
        val base = if (baselineSteps > 0) baselineSteps else DEFAULT_STEP_GOAL
        val factor = if (recoveryValue < 50) 0.7 else 1.0
        return (base * factor).coerceAtLeast(1.0)
    }

    /** Longer uninterrupted sedentary stretches lower the score. */
    internal fun sedentaryScore(longestStretchMin: Int): Double =
        (100.0 - (longestStretchMin - 60).coerceAtLeast(0) * 0.25).coerceIn(0.0, 100.0)
}
