package com.daybreak.scoring

import kotlin.math.roundToInt

/**
 * Sleep Score (PRD §10.1). Weighted sum of seven contributors, each scored against
 * the wearer's own baseline. Deep/REM splits are weighted as directional, not precise,
 * because they inherit the ring's staging accuracy.
 */
object SleepScore {

    /** Age-based nightly sleep need in minutes (PRD §10.1: 7-9h target). */
    fun sleepNeedMin(ageYears: Int): Int = when {
        ageYears < 18 -> 540   // 9h
        ageYears < 65 -> 480   // 8h
        else -> 450            // 7.5h
    }

    fun score(today: NightData, baselines: Baselines): ScoreResult {
        val s = today.sleep
        val need = sleepNeedMin(today.ageYears)

        val duration = durationScore(s.totalSleepMin, need)
        val efficiency = Normalize.fraction(s.efficiency)
        val deep = Normalize.higherBetter(s.deepProportion, baselines.deepProportion, spread = 0.05)
        val rem = Normalize.higherBetter(s.remProportion, baselines.remProportion, spread = 0.05)
        val restfulness = restfulnessScore(s.wakeEvents)
        val timing = timingScore(s, baselines)
        val latency = latencyScore(s.sleepLatencyMin)

        val contributors = listOf(
            Contributor("Total sleep vs need", 25, duration),
            Contributor("Sleep efficiency", 15, efficiency),
            Contributor("Deep sleep vs baseline", 15, deep),
            Contributor("REM sleep vs baseline", 15, rem),
            Contributor("Restfulness", 10, restfulness),
            Contributor("Timing regularity", 10, timing),
            Contributor("Sleep latency", 10, latency),
        )
        return ScoreResult(weightedValue(contributors), contributors)
    }

    /** 100 at/above need, linear below; mild penalty for large oversleep. */
    internal fun durationScore(totalMin: Int, needMin: Int): Double {
        val ratio = totalMin.toDouble() / needMin
        return if (ratio >= 1.0) {
            (100.0 - (ratio - 1.0) * 50.0).coerceIn(80.0, 100.0)
        } else {
            (ratio * 100.0).coerceIn(0.0, 100.0)
        }
    }

    /** Fewer wake events is better; ~0 -> 100, ~6+ -> low. */
    internal fun restfulnessScore(wakeEvents: Int): Double =
        (100.0 - wakeEvents * 12.0).coerceIn(0.0, 100.0)

    /** Closeness of bed/wake times to the wearer's usual pattern. */
    internal fun timingScore(s: SleepInput, b: Baselines): Double {
        val bed = Normalize.closeness(s.bedTimeMinutesOfDay.toDouble(), b.bedTimeMinutesOfDay, tolerance = 90.0)
        val wake = Normalize.closeness(s.wakeTimeMinutesOfDay.toDouble(), b.wakeTimeMinutesOfDay, tolerance = 90.0)
        return (bed + wake) / 2.0
    }

    /** ~15 min latency is ideal; longer falls off. */
    internal fun latencyScore(latencyMin: Int): Double =
        Normalize.closeness(latencyMin.toDouble(), target = 12.0, tolerance = 45.0)
}

/** Sum of weighted contributors, rounded to a 0-100 integer. */
internal fun weightedValue(contributors: List<Contributor>): Int {
    val totalWeight = contributors.sumOf { it.weightPct }
    require(totalWeight == 100) { "Contributor weights must total 100, got $totalWeight" }
    return contributors.sumOf { it.contribution }.roundToInt().coerceIn(0, 100)
}
