package com.daybreak.scoring

/**
 * Recovery Score (PRD §10.2), Daybreak's Readiness analog. Leans on RHR trend, the
 * previous-night sleep score, and recovery-index timing. The HRV contributor is
 * optional: when the ring doesn't expose HRV we redistribute its 20% rather than
 * zero-filling it (resolved PRD §10.2 fallback: +10% RHR, +10% Sleep).
 */
object RecoveryScore {

    fun score(today: NightData, baselines: Baselines, previousNightSleepScore: Int): ScoreResult {
        val rhr = restingHrOf(today.overnightHr)
        val sleepNorm = previousNightSleepScore.toDouble()
        val rhrNorm = Normalize.lowerBetter(rhr, baselines.restingHr, spread = 6.0)
        val recoveryIndexNorm = recoveryIndexScore(today.overnightHr)
        val priorLoadNorm = priorLoadScore(today.activity.priorDayActivityLoad)

        val hrvAvailable = today.hrvMs != null && baselines.hrv != null

        val contributors = if (hrvAvailable) {
            val hrvNorm = Normalize.higherBetter(today.hrvMs!!, baselines.hrv!!, spread = 15.0)
            listOf(
                Contributor("Previous-night sleep", 30, sleepNorm),
                Contributor("Resting HR vs baseline", 25, rhrNorm),
                Contributor("HRV balance", 20, hrvNorm),
                Contributor("Recovery index", 15, recoveryIndexNorm),
                Contributor("Prior-day load", 10, priorLoadNorm),
            )
        } else {
            // HRV unavailable: redistribute its 20% -> +10% Sleep (40), +10% RHR (35).
            listOf(
                Contributor("Previous-night sleep", 40, sleepNorm),
                Contributor("Resting HR vs baseline", 35, rhrNorm),
                Contributor("Recovery index", 15, recoveryIndexNorm),
                Contributor("Prior-day load", 10, priorLoadNorm),
            )
        }
        return ScoreResult(weightedValue(contributors), contributors)
    }

    /**
     * Recovery index: how early in the night resting HR hit its low. Earlier low =
     * better recovery (PRD Appendix). Returns 0-100 from the low's position in the night.
     */
    internal fun recoveryIndexScore(samples: List<HrSample>): Double {
        if (samples.size < 2) return 65.0
        val sorted = samples.sortedBy { it.epochMin }
        val start = sorted.first().epochMin
        val end = sorted.last().epochMin
        val span = (end - start).toDouble()
        if (span <= 0.0) return 65.0
        val lowSample = sorted.minByOrNull { it.bpm }!!
        val position = (lowSample.epochMin - start) / span // 0 (early) .. 1 (late)
        return ((1.0 - position) * 100.0).coerceIn(0.0, 100.0)
    }

    /** Moderate prior-day load is neutral; overreaching (load > ~1.0) lowers recovery. */
    internal fun priorLoadScore(load: Double): Double =
        when {
            load <= 1.0 -> 75.0
            else -> (75.0 - (load - 1.0) * 100.0).coerceIn(0.0, 75.0)
        }
}
