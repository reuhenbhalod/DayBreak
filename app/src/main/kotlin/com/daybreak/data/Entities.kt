package com.daybreak.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entities (PRD §11). Local SQLite only. Every per-day record is scoped to a
 * [ProfileEntity] via [wearerId] so a second wearer can share the app (PRD §15 P2).
 * Dates are ISO-8601 day strings ("2026-06-22").
 */

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val ageYears: Int,
)

@Entity(tableName = "nightly_sleep", primaryKeys = ["wearerId", "date"])
data class NightlySleepEntity(
    val wearerId: Long,
    val date: String,
    val deepMin: Int,
    val remMin: Int,
    val lightMin: Int,
    val awakeMin: Int,
    val timeInBedMin: Int,
    val sleepLatencyMin: Int,
    val wakeEvents: Int,
    val bedTimeMinutesOfDay: Int,
    val wakeTimeMinutesOfDay: Int,
    val hrvMs: Double?,
    val score: Int?,
)

@Entity(tableName = "hr_samples")
data class HrSampleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wearerId: Long,
    val date: String,
    val epochMin: Long,
    val bpm: Int,
)

@Entity(tableName = "daily_activity", primaryKeys = ["wearerId", "date"])
data class DailyActivityEntity(
    val wearerId: Long,
    val date: String,
    val steps: Int,
    val activeMinutes: Int,
    val longestSedentaryStretchMin: Int,
    val priorDayActivityLoad: Double,
    val score: Int?,
)

@Entity(tableName = "recovery", primaryKeys = ["wearerId", "date"])
data class RecoveryEntity(
    val wearerId: Long,
    val date: String,
    val score: Int,
    val contributorsJson: String,
)

@Entity(tableName = "baselines")
data class BaselineEntity(
    @PrimaryKey val metric: String,
    val value: Double,
    val nightsObserved: Int,
)

@Entity(tableName = "sync_log")
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpochMs: Long,
    val result: String,
)

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val wearerId: Long,
    val date: String,
    val label: String,
)

@Entity(tableName = "spo2", primaryKeys = ["wearerId", "date"])
data class Spo2Entity(
    val wearerId: Long,
    val date: String,
    val averagePct: Int,
    val minimumPct: Int,
)
