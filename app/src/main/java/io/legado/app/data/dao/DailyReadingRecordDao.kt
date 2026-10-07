package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.data.entities.DailyReadingRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyReadingRecordDao {
    @Query("SELECT * FROM dailyReadingRecords WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay")
    fun getBetween(startEpochDay: Long, endEpochDay: Long): List<DailyReadingRecord>

    @Query("SELECT * FROM dailyReadingRecords WHERE epochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY epochDay")
    fun observeBetween(startEpochDay: Long, endEpochDay: Long): Flow<List<DailyReadingRecord>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertDate(record: DailyReadingRecord)

    @Query("UPDATE dailyReadingRecords SET readTime = readTime + :readTime WHERE epochDay = :epochDay")
    fun increment(epochDay: Long, readTime: Long)

    @Transaction
    fun addTime(epochDay: Long, readTime: Long) {
        if (readTime <= 0L) return
        insertDate(DailyReadingRecord(epochDay))
        increment(epochDay, readTime)
    }

    /** A checkpoint spanning midnight is committed as one batch, never as partial days. */
    @Transaction
    fun addTimes(records: List<DailyReadingRecord>) {
        records.forEach { record ->
            if (record.readTime > 0L) {
                insertDate(DailyReadingRecord(record.epochDay))
                increment(record.epochDay, record.readTime)
            }
        }
    }
}
