package com.daybreak.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: ProfileEntity): Long

    @Update
    suspend fun update(profile: ProfileEntity)

    @Query("SELECT * FROM profiles ORDER BY id ASC")
    suspend fun all(): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun byId(id: Long): ProfileEntity?
}

@Dao
interface SleepDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(night: NightlySleepEntity)

    @Query("SELECT * FROM nightly_sleep WHERE wearerId = :wearerId ORDER BY date ASC")
    suspend fun allOrdered(wearerId: Long): List<NightlySleepEntity>

    @Query("SELECT * FROM nightly_sleep WHERE wearerId = :wearerId AND date = :date")
    suspend fun forDate(wearerId: Long, date: String): NightlySleepEntity?
}

@Dao
interface HrSampleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<HrSampleEntity>)

    @Query("SELECT * FROM hr_samples WHERE wearerId = :wearerId AND date = :date ORDER BY epochMin ASC")
    suspend fun forDate(wearerId: Long, date: String): List<HrSampleEntity>
}

@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(activity: DailyActivityEntity)

    @Query("SELECT * FROM daily_activity WHERE wearerId = :wearerId AND date = :date")
    suspend fun forDate(wearerId: Long, date: String): DailyActivityEntity?
}

@Dao
interface RecoveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(recovery: RecoveryEntity)

    @Query("SELECT * FROM recovery WHERE wearerId = :wearerId AND date = :date")
    suspend fun forDate(wearerId: Long, date: String): RecoveryEntity?
}

@Dao
interface BaselineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(baselines: List<BaselineEntity>)

    @Query("SELECT * FROM baselines")
    suspend fun all(): List<BaselineEntity>
}

@Dao
interface SyncLogDao {
    @Insert
    suspend fun insert(entry: SyncLogEntity)

    @Query("SELECT * FROM sync_log ORDER BY timestampEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<SyncLogEntity>
}

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE wearerId = :wearerId ORDER BY date DESC, id DESC")
    suspend fun all(wearerId: Long): List<TagEntity>

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface Spo2Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(spo2: Spo2Entity)

    @Query("SELECT * FROM spo2 WHERE wearerId = :wearerId AND date = :date")
    suspend fun forDate(wearerId: Long, date: String): Spo2Entity?

    @Query("SELECT * FROM spo2 WHERE wearerId = :wearerId ORDER BY date ASC")
    suspend fun allOrdered(wearerId: Long): List<Spo2Entity>
}
