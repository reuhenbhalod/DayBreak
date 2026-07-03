package com.daybreak.scoring

import kotlin.math.abs

/**
 * The wearer's own rolling averages, used to score every contributor relative to
 * personal history rather than absolutes (PRD §10, Appendix "Baseline").
 */
data class Baselines(
    val restingHr: Double,
    val deepProportion: Double,
    val remProportion: Double,
    val totalSleepMin: Double,
    val bedTimeMinutesOfDay: Double,
    val wakeTimeMinutesOfDay: Double,
    /** Mean HRV over nights that had it; null if the ring never reported HRV. */
    val hrv: Double?,
    val steps: Double,
    val activeMinutes: Double,
    val nightsObserved: Int,
)

object BaselineCalculator {
    /** Rolling window length for baselines (PRD §10 / Appendix). */
    const val WINDOW_NIGHTS = 28

    fun fromHistory(history: List<NightData>): Baselines {
        val window = history.takeLast(WINDOW_NIGHTS)
        require(window.isNotEmpty()) { "Cannot compute baselines from empty history" }

        val hrvValues = window.mapNotNull { it.hrvMs }

        return Baselines(
            restingHr = window.map { restingHrOf(it.overnightHr) }.average(),
            deepProportion = window.map { it.sleep.deepProportion }.average(),
            remProportion = window.map { it.sleep.remProportion }.average(),
            totalSleepMin = window.map { it.sleep.totalSleepMin.toDouble() }.average(),
            bedTimeMinutesOfDay = window.map { it.sleep.bedTimeMinutesOfDay.toDouble() }.average(),
            wakeTimeMinutesOfDay = window.map { it.sleep.wakeTimeMinutesOfDay.toDouble() }.average(),
            hrv = if (hrvValues.isEmpty()) null else hrvValues.average(),
            steps = window.map { it.activity.steps.toDouble() }.average(),
            activeMinutes = window.map { it.activity.activeMinutes.toDouble() }.average(),
            nightsObserved = history.size,
        )
    }
}

/** Resting HR proxy: the lowest sustained overnight reading (nightly low). */
internal fun restingHrOf(samples: List<HrSample>): Double {
    if (samples.isEmpty()) return 0.0
    return samples.minOf { it.bpm }.toDouble()
}

/**
 * Normalization helpers — all return a 0-100 score. They are intentionally simple,
 * monotonic, and clamped; the staging/HRV inputs are directional, not clinical (PRD §10).
 */
object Normalize {
    /** Centered at [baseline] -> 65; each [spread] above adds ~35, below subtracts. Higher is better. */
    fun higherBetter(value: Double, baseline: Double, spread: Double): Double {
        if (spread <= 0.0) return 65.0
        return (65.0 + (value - baseline) / spread * 35.0).coerceIn(0.0, 100.0)
    }

    /** Mirror of [higherBetter] for metrics where lower is better (e.g. resting HR). */
    fun lowerBetter(value: Double, baseline: Double, spread: Double): Double =
        higherBetter(baseline, value, spread)

    /** Peaks (100) when [value] is near [target]; falls off either side by [tolerance]. */
    fun closeness(value: Double, target: Double, tolerance: Double): Double {
        if (tolerance <= 0.0) return 100.0
        return (100.0 - abs(value - target) / tolerance * 100.0).coerceIn(0.0, 100.0)
    }

    /** Score a fraction (0..1) directly onto 0..100. */
    fun fraction(value: Double): Double = (value * 100.0).coerceIn(0.0, 100.0)
}
