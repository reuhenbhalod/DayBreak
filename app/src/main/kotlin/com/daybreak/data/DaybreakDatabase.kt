package com.daybreak.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        NightlySleepEntity::class,
        HrSampleEntity::class,
        DailyActivityEntity::class,
        RecoveryEntity::class,
        BaselineEntity::class,
        SyncLogEntity::class,
        TagEntity::class,
        Spo2Entity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class DaybreakDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun sleepDao(): SleepDao
    abstract fun hrSampleDao(): HrSampleDao
    abstract fun activityDao(): ActivityDao
    abstract fun recoveryDao(): RecoveryDao
    abstract fun baselineDao(): BaselineDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun tagDao(): TagDao
    abstract fun spo2Dao(): Spo2Dao

    companion object {
        @Volatile
        private var instance: DaybreakDatabase? = null

        fun get(context: Context): DaybreakDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DaybreakDatabase::class.java,
                    "daybreak.db",
                ).fallbackToDestructiveMigration().build().also { instance = it }
            }
    }
}
