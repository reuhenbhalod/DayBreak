package com.daybreak.protocol

/**
 * Reassembles the multi-packet step/activity response (CMD_GET_STEP_SOMEDAY). Each
 * packet is one 15-minute interval:
 *  - `[1]` == 255 means no data for the requested day.
 *  - `[4]` time-interval index, `[5]` page, `[6]` total pages.
 *  - calories `[7..8]`, steps `[9..10]`, distance `[11..12]` (all LE uint16).
 * Completes when page == totalPages − 1.
 */
class StepsAssembler {

    data class Interval(
        val timeIndex: Int,
        val calories: Int,
        val steps: Int,
        val distanceMeters: Int,
    )

    private val intervals = mutableListOf<Interval>()

    var complete = false
        private set
    var noData = false
        private set

    /** Feed one packet; returns true once the day's intervals are complete. */
    fun offer(packet: ByteArray): Boolean {
        if (ub(packet[0]) != Command.GET_STEPS) return complete
        if (ub(packet[1]) == 255) {
            noData = true
            complete = true
            return true
        }

        intervals.add(
            Interval(
                timeIndex = ub(packet[4]),
                calories = u16le(packet, 7),
                steps = u16le(packet, 9),
                distanceMeters = u16le(packet, 11),
            ),
        )

        val page = ub(packet[5])
        val totalPages = ub(packet[6])
        if (page == totalPages - 1) complete = true
        return complete
    }

    fun intervals(): List<Interval> = intervals.toList()

    fun summary(): DailyActivitySummary =
        DailyActivitySummary(
            totalSteps = intervals.sumOf { it.steps },
            totalCalories = intervals.sumOf { it.calories },
            totalDistanceMeters = intervals.sumOf { it.distanceMeters },
            noData = noData,
        )
}
