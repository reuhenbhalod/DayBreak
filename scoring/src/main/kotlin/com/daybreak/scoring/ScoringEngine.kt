package com.daybreak.scoring

/**
 * Orchestrates the three scores for a given day. Applies the P0 calibration guard:
 * until [CALIBRATION_NIGHTS] of history exist, returns [ScoringState.Calibrating]
 * instead of a possibly-misleading number (PRD §8.2 / §10).
 *
 * Pure and Android-free so it can be unit-tested on the JVM (PRD §12).
 */
object ScoringEngine {

    /** Nights of history required before scores are shown (warmup period). */
    const val CALIBRATION_NIGHTS = 7

    /**
     * @param today the day being scored.
     * @param history prior nights, oldest-to-newest, excluding [today].
     */
    fun compute(today: NightData, history: List<NightData>): ScoringState {
        if (history.size < CALIBRATION_NIGHTS) {
            return ScoringState.Calibrating(history.size, CALIBRATION_NIGHTS)
        }

        val baselines = BaselineCalculator.fromHistory(history)
        val sleep = SleepScore.score(today, baselines)
        val recovery = RecoveryScore.score(today, baselines, sleep.value)
        val activity = ActivityScore.score(today, baselines, recovery.value)
        val summary = SummaryGenerator.generate(sleep, recovery, activity)

        return ScoringState.Ready(sleep, recovery, activity, summary)
    }
}
