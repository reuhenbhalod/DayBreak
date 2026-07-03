package com.daybreak.protocol

/**
 * Parses the sleep big-data payload (after the 6-byte header is stripped).
 * Layout: `[daysInPacket]`, then per day:
 * `[daysAgo][dayBytes][sleepStart u16][sleepEnd u16][(stageType, durationMin)…]`,
 * where `dayBytes` counts from `sleepStart` to the end of that day's stages.
 */
object SleepParser {

    fun parse(payload: ByteArray): List<SleepDay> {
        if (payload.isEmpty()) return emptyList()

        val dayCount = ub(payload[0])
        val days = ArrayList<SleepDay>()
        var p = 1

        while (days.size < dayCount && p + 2 <= payload.size) {
            val daysAgo = ub(payload[p])
            val dayBytes = ub(payload[p + 1])
            val dayStart = p + 2
            if (dayBytes < 4 || dayStart + dayBytes > payload.size) break

            val sleepStart = ub(payload[dayStart]) or (ub(payload[dayStart + 1]) shl 8)
            val sleepEnd = ub(payload[dayStart + 2]) or (ub(payload[dayStart + 3]) shl 8)

            val stages = ArrayList<SleepStageSegment>()
            var j = 4
            while (j + 1 < dayBytes) {
                stages.add(SleepStageSegment(ub(payload[dayStart + j]), ub(payload[dayStart + j + 1])))
                j += 2
            }

            days.add(SleepDay(daysAgo, sleepStart, sleepEnd, stages))
            p = dayStart + dayBytes
        }
        return days
    }
}
