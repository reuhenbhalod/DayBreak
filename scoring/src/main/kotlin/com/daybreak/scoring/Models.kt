package com.daybreak.scoring

/**
 * Domain inputs for the scoring engine. All types are plain data — no Android
 * dependencies — so the engine is portable and unit-testable (PRD §12).
 */

/** Minutes spent in each sleep stage for one night. */
data class SleepStages(
    val deepMin: Int,
    val remMin: Int,
    val lightMin: Int,
    val awakeMin: Int,
) {
    val asleepMin: Int get() = deepMin + remMin + lightMin
}

/** One night of sleep, as decoded from the ring (stages, timing, latency). */
data class SleepInput(
    val stages: SleepStages,
    val timeInBedMin: Int,
    val sleepLatencyMin: Int,
    val wakeEvents: Int,
    /** Minutes from midnight the wearer went to bed (e.g. 23:10 -> 1390). */
    val bedTimeMinutesOfDay: Int,
    /** Minutes from midnight the wearer woke (e.g. 06:40 -> 400). */
    val wakeTimeMinutesOfDay: Int,
) {
    val totalSleepMin: Int get() = stages.asleepMin
    val deepProportion: Double get() = ratio(stages.deepMin, totalSleepMin)
    val remProportion: Double get() = ratio(stages.remMin, totalSleepMin)
    val efficiency: Double get() = ratio(totalSleepMin, timeInBedMin)
}

/** A single overnight heart-rate reading. */
data class HrSample(val epochMin: Long, val bpm: Int)

/** One day of activity, as decoded from the ring's accelerometer/step counter. */
data class ActivityInput(
    val steps: Int,
    val activeMinutes: Int,
    val longestSedentaryStretchMin: Int,
    /** Normalized prior-day load, ~0 (rest) .. ~1.5 (overreaching). */
    val priorDayActivityLoad: Double,
)

/** Everything captured for a single day/night, the unit the engine scores. */
data class NightData(
    val sleep: SleepInput,
    val overnightHr: List<HrSample>,
    val activity: ActivityInput,
    /** Heart-rate variability in ms, or null when the firmware doesn't expose it (PRD §10.2). */
    val hrvMs: Double?,
    val ageYears: Int,
)

/** One weighted contributor to a score: its 0-100 normalized value and weight %. */
data class Contributor(
    val name: String,
    val weightPct: Int,
    val normalized: Double,
) {
    val contribution: Double get() = weightPct * normalized / 100.0
}

/** A computed 0-100 score plus the breakdown the maintainer can inspect (PRD §8.2). */
data class ScoreResult(
    val value: Int,
    val contributors: List<Contributor>,
) {
    /** The weakest contributor — the dominant drag on this score. */
    val dominantDriver: Contributor get() = contributors.minByOrNull { it.normalized }!!
}

/**
 * Engine output. Until baselines stabilize we surface [Calibrating] rather than a
 * possibly-misleading number — the P0 calibration guard (PRD §8.2 / §10).
 */
sealed interface ScoringState {
    data class Calibrating(val nightsObserved: Int, val nightsRequired: Int) : ScoringState

    data class Ready(
        val sleep: ScoreResult,
        val recovery: ScoreResult,
        val activity: ScoreResult,
        val summary: String,
    ) : ScoringState
}

internal fun ratio(part: Int, whole: Int): Double =
    if (whole <= 0) 0.0 else part.toDouble() / whole.toDouble()
