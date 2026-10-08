package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.ReadRecord
import io.legado.app.data.entities.ReadRecordShow

@Dao
interface ReadRecordDao {

    @get:Query("select * from readRecord")
    val all: List<ReadRecord>

    @get:Query(
        """
        select bookName, sum(readTime) as readTime, max(lastRead) as lastRead 
        from readRecord 
        group by bookName 
        order by bookName collate localized"""
    )
    val allShow: List<ReadRecordShow>

    @get:Query("select sum(readTime) from readRecord")
    val allTime: Long

    @get:Query("select count(distinct bookName) from readRecord")
    val recordCount: Int

    @get:Query("select coalesce(sum(readTime), 0) from readRecord")
    val homeTotalReadTime: Long

    @get:Query(
        """
        select bookName, sum(readTime) as readTime, max(lastRead) as lastRead
        from readRecord
        group by bookName
        order by max(lastRead) desc, bookName collate localized, bookName
        limit 3"""
    )
    val recentForHome: List<ReadRecordShow>

    @Query(
        """
        select bookName, sum(readTime) as readTime, max(lastRead) as lastRead 
        from readRecord 
        where bookName like '%' || :searchKey || '%'
        group by bookName 
        order by bookName collate localized"""
    )
    fun search(searchKey: String): List<ReadRecordShow>

    @Query("select sum(readTime) from readRecord where bookName = :bookName")
    fun getReadTime(bookName: String): Long?

    @Query("select readTime from readRecord where deviceId = :androidId and bookName = :bookName")
    fun getReadTime(androidId: String, bookName: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg readRecord: ReadRecord)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIfAbsent(readRecord: ReadRecord)

    @Query(
        """
        update readRecord set readTime = readTime + :readTime, lastRead = max(lastRead, :lastRead)
        where deviceId = :deviceId and bookName = :bookName"""
    )
    fun increment(deviceId: String, bookName: String, readTime: Long, lastRead: Long)

    /** Add only the new local interval; records restored from other devices stay separate. */
    @Transaction
    fun addTime(bookName: String, readTime: Long, lastRead: Long) {
        if (readTime <= 0L) return
        insertIfAbsent(ReadRecord(deviceId = "", bookName = bookName, lastRead = lastRead))
        increment(deviceId = "", bookName = bookName, readTime = readTime, lastRead = lastRead)
    }

    @Update
    fun update(vararg record: ReadRecord)

    @Delete
    fun delete(vararg record: ReadRecord)

    @Query("delete from readRecord")
    fun clear()

    @Query("delete from readRecord where bookName = :bookName")
    fun deleteByName(bookName: String)

    @Query("delete from readRecord where bookName in (:bookNames)")
    fun deleteByNames(bookNames: List<String>)
}
