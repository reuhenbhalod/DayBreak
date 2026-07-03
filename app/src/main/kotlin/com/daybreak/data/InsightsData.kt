package com.daybreak.data

import com.daybreak.scoring.ScoringState

/** One day's three scores, for the trend chart. */
data class DailyPoint(val label: String, val sleep: Int, val recovery: Int, val activity: Int)

/** Aggregated sleep-stage minutes for the stacked composition bar. */
data class StageComposition(val deepMin: Int, val remMin: Int, val lightMin: Int, val awakeMin: Int) {
    val total: Int get() = deepMin + remMin + lightMin + awakeMin
}

/** A wearer note/tag for a given day. */
data class DayNote(val date: String, val label: String)

/** Everything the Insights screen renders for a selected range (PRD §8.6). */
data class InsightsData(
    val rangeDays: Int,
    val trend: List<DailyPoint>,
    val restingHr: List<Float>,
    val restingHrDates: List<String> = emptyList(),
    val steps: List<Int>,
    val stepsDates: List<String> = emptyList(),
    val lastNightStages: StageComposition?,
    val overnightHr: List<Int>,
    val overnightHrTimes: List<String> = emptyList(),
    val today: ScoringState.Ready?,
    val notes: List<DayNote> = emptyList(),
    val taggedTrendIndices: Set<Int> = emptySet(),
    val spo2: List<Int> = emptyList(),
    val spo2Dates: List<String> = emptyList(),
    val lastNightSpo2Avg: Int? = null,
    val lastNightSpo2Min: Int? = null,
)
