package com.daybreak.sync

/**
 * Decides whether to fire the single gentle morning notification (PRD §8.5):
 * only during the morning window, and at most once per day. Pure logic so it's
 * unit-testable without Android.
 */
object MorningPolicy {
    /** Inclusive hour-of-day window considered "morning". */
    val MORNING_HOURS = 4..11

    fun shouldNotify(hourOfDay: Int, today: String, lastNotified: String?): Boolean =
        hourOfDay in MORNING_HOURS && today != lastNotified
}
