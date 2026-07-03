package com.daybreak.sync

/**
 * Catch-up backfill (PRD §8.1: "never lose a night"). Given how many days have passed
 * since the last successful sync, decides which day-offsets to re-pull (0 = today),
 * capped so a long gap doesn't pull forever. Pure logic so it's unit-testable.
 */
object CatchUp {
    const val DEFAULT_GAP = 2
    const val MAX_BACKFILL_DAYS = 7

    fun offsets(daysSinceLastSync: Int, cap: Int = MAX_BACKFILL_DAYS): List<Int> {
        val end = daysSinceLastSync.coerceIn(0, cap)
        return (0..end).toList()
    }
}
