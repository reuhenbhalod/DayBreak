package com.daybreak.protocol

/** Sleep stage type codes (Gadgetbridge ColmiR0xConstants). */
object SleepStageType {
    const val LIGHT = 0x02
    const val DEEP = 0x03
    const val REM = 0x04
    const val AWAKE = 0x05
}

/** One contiguous sleep stage segment. */
data class SleepStageSegment(val type: Int, val durationMin: Int)

/**
 * One night's decoded sleep. [sleepStartMin]/[sleepEndMin] are minutes after midnight;
 * end may be "earlier" than start when sleep crosses midnight (handled by [spanMin]).
 */
data class SleepDay(
    val daysAgo: Int,
    val sleepStartMin: Int,
    val sleepEndMin: Int,
    val stages: List<SleepStageSegment>,
) {
    fun minutesOf(stageType: Int): Int = stages.filter { it.type == stageType }.sumOf { it.durationMin }

    val deepMin: Int get() = minutesOf(SleepStageType.DEEP)
    val remMin: Int get() = minutesOf(SleepStageType.REM)
    val lightMin: Int get() = minutesOf(SleepStageType.LIGHT)
    val awakeMin: Int get() = minutesOf(SleepStageType.AWAKE)

    /** Number of distinct awake periods during the night. */
    val wakeEvents: Int get() = stages.count { it.type == SleepStageType.AWAKE }

    /** Total time in bed in minutes, accounting for crossing midnight. */
    val spanMin: Int
        get() = if (sleepEndMin >= sleepStartMin) sleepEndMin - sleepStartMin
        else sleepEndMin + 1440 - sleepStartMin
}
