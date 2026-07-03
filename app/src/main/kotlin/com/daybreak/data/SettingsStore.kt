package com.daybreak.data

import android.content.Context

/**
 * Tiny persisted settings (SharedPreferences). Holds onboarding completion and the
 * wearer's age (which drives the sleep-need target, PRD §8.5/§10.1). No PII beyond age.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("daybreak_settings", Context.MODE_PRIVATE)

    var onboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var wearerAge: Int
        get() = prefs.getInt(KEY_AGE, DEFAULT_AGE)
        set(value) = prefs.edit().putInt(KEY_AGE, value).apply()

    /** ISO date of the last morning "night is ready" notification, or null. */
    var lastNotifiedMorning: String?
        get() = prefs.getString(KEY_LAST_NOTIFIED, null)
        set(value) = prefs.edit().putString(KEY_LAST_NOTIFIED, value).apply()

    /** ISO date of the last successful ring sync, or null (drives catch-up backfill). */
    var lastSuccessfulSync: String?
        get() = prefs.getString(KEY_LAST_SYNC, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC, value).apply()

    /** Epoch millis of the last data refresh, for the "Updated …" line (0 = never). */
    var lastUpdatedEpochMs: Long
        get() = prefs.getLong(KEY_LAST_UPDATED, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATED, value).apply()

    /** Last known ring battery percentage (-1 = unknown) and charging state. */
    var lastBatteryPct: Int
        get() = prefs.getInt(KEY_BATTERY, -1)
        set(value) = prefs.edit().putInt(KEY_BATTERY, value).apply()

    var lastBatteryCharging: Boolean
        get() = prefs.getBoolean(KEY_BATTERY_CHARGING, false)
        set(value) = prefs.edit().putBoolean(KEY_BATTERY_CHARGING, value).apply()

    /** Experimental on-device LLM daily summary; off by default (PRD §8.3 P2). */
    var aiSummaryEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_SUMMARY, false)
        set(value) = prefs.edit().putBoolean(KEY_AI_SUMMARY, value).apply()

    /** Id of the wearer profile currently being viewed (multi-wearer, PRD §15 P2). */
    var currentWearerId: Long
        get() = prefs.getLong(KEY_CURRENT_WEARER, 0L)
        set(value) = prefs.edit().putLong(KEY_CURRENT_WEARER, value).apply()

    companion object {
        const val DEFAULT_AGE = 70
        private const val KEY_ONBOARDED = "onboarding_complete"
        private const val KEY_AGE = "wearer_age"
        private const val KEY_LAST_NOTIFIED = "last_notified_morning"
        private const val KEY_LAST_SYNC = "last_successful_sync"
        private const val KEY_LAST_UPDATED = "last_updated_epoch_ms"
        private const val KEY_BATTERY = "last_battery_pct"
        private const val KEY_BATTERY_CHARGING = "last_battery_charging"
        private const val KEY_AI_SUMMARY = "ai_summary_enabled"
        private const val KEY_CURRENT_WEARER = "current_wearer_id"
    }
}
