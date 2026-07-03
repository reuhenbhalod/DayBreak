package com.daybreak.scoring

import kotlin.math.roundToInt

/** Shared builders for synthetic nights used across the scoring tests. */
object TestFixtures {

    // Baseline stage proportions a healthy night sits at; stages scale with total sleep.
    private const val DEEP_FRACTION = 0.1875
    private const val REM_FRACTION = 0.208

    /** Overnight HR with its low at the start of the night (early low = good recovery). */
    fun nightHr(minBpm: Int): List<HrSample> = listOf(
        HrSample(0, minBpm),
        HrSample(60, minBpm + 5),
        HrSample(120, minBpm + 8),
        HrSample(180, minBpm + 10),
        HrSample(240, minBpm + 12),
        HrSample(300, minBpm + 9),
        HrSample(360, minBpm + 11),
        HrSample(420, minBpm + 13),
        HrSample(480, minBpm + 14),
    )

    fun goodNight(
        totalSleepMin: Int = 480,
        deepMin: Int = (totalSleepMin * DEEP_FRACTION).roundToInt(),
        remMin: Int = (totalSleepMin * REM_FRACTION).roundToInt(),
        wakeEvents: Int = 1,
        latencyMin: Int = 12,
        bedTime: Int = 1380,
        wakeTime: Int = 420,
        minBpm: Int = 52,
        hrvMs: Double? = 45.0,
        steps: Int = 8000,
        activeMinutes: Int = 45,
        sedentaryMin: Int = 60,
        priorLoad: Double = 0.5,
        ageYears: Int = 70,
    ): NightData {
        val lightMin = (totalSleepMin - deepMin - remMin).coerceAtLeast(0)
        return NightData(
            sleep = SleepInput(
                stages = SleepStages(deepMin, remMin, lightMin, awakeMin = 20),
                timeInBedMin = totalSleepMin + 20,
                sleepLatencyMin = latencyMin,
                wakeEvents = wakeEvents,
                bedTimeMinutesOfDay = bedTime,
                wakeTimeMinutesOfDay = wakeTime,
            ),
            overnightHr = nightHr(minBpm),
            activity = ActivityInput(
                steps = steps,
                activeMinutes = activeMinutes,
                longestSedentaryStretchMin = sedentaryMin,
                priorDayActivityLoad = priorLoad,
            ),
            hrvMs = hrvMs,
            ageYears = ageYears,
        )
    }

    /** A history of [n] consistent good nights, suitable for stable baselines. */
    fun history(n: Int = ScoringEngine.CALIBRATION_NIGHTS, hrvMs: Double? = 45.0): List<NightData> =
        List(n) { goodNight(hrvMs = hrvMs) }

    fun baselines(hrvMs: Double? = 45.0): Baselines =
        BaselineCalculator.fromHistory(history(hrvMs = hrvMs))
}
