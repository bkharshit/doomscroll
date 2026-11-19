package com.doomscroll.android.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Blocked attempts per interception rule per local day (day = LocalDate.toEpochDay()). */
@Entity(primaryKeys = ["day", "ruleId"])
data class DailyBlockCount(val day: Long, val ruleId: String, val count: Int)

/** Final foreground seconds per monitored app for a finished local day. */
@Entity(primaryKeys = ["day", "packageName"])
data class DailyUsage(val day: Long, val packageName: String, val seconds: Long)

@Dao
interface MetricsDao {
    @Query("INSERT OR IGNORE INTO DailyBlockCount(day, ruleId, count) VALUES(:day, :ruleId, 0)")
    suspend fun ensureBlockRow(day: Long, ruleId: String)

    @Query("UPDATE DailyBlockCount SET count = count + :by WHERE day = :day AND ruleId = :ruleId")
    suspend fun addToBlockRow(day: Long, ruleId: String, by: Int)

    /** Plain UPSERT needs SQLite 3.24 (API 30); minSdk 26 ships 3.18, so insert-or-ignore then update. */
    @Transaction
    suspend fun incrementBlock(day: Long, ruleId: String, by: Int = 1) {
        ensureBlockRow(day, ruleId)
        addToBlockRow(day, ruleId, by)
    }

    @Query("SELECT * FROM DailyBlockCount WHERE day = :day")
    fun blocksForDay(day: Long): Flow<List<DailyBlockCount>>

    @Query("SELECT COALESCE(SUM(count), 0) FROM DailyBlockCount")
    fun totalBlocks(): Flow<Int>

    @Query("SELECT * FROM DailyBlockCount WHERE day >= :fromDay")
    fun blocksSince(fromDay: Long): Flow<List<DailyBlockCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUsage(rows: List<DailyUsage>)

    @Query("SELECT * FROM DailyUsage WHERE day = :day")
    suspend fun usageForDay(day: Long): List<DailyUsage>
}

@Database(entities = [DailyBlockCount::class, DailyUsage::class], version = 1, exportSchema = false)
abstract class DoomscrollDatabase : RoomDatabase() {
    abstract fun metricsDao(): MetricsDao

    companion object {
        fun create(context: Context): DoomscrollDatabase =
            Room.databaseBuilder(context, DoomscrollDatabase::class.java, "doomscroll.db").build()
    }
}
