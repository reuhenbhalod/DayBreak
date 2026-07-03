package com.daybreak.data

import com.daybreak.scoring.ScoringState

/**
 * Everything the home dashboard shows. Scores appear once calibrated; the raw signals
 * (battery, steps, sleep, HR, SpO2) show as soon as the ring has them — no waiting for
 * calibration.
 */
data class HomeData(
    val scoring: ScoringState,
    val batteryPct: Int?,
    val charging: Boolean,
    val stepsToday: Int?,
    val sleepLastNightMin: Int?,
    val restingHr: Int?,
    val spo2: Int?,
    val lastUpdated: String,
    val calibrationNightsObserved: Int,
    val calibrationNightsRequired: Int,
)
