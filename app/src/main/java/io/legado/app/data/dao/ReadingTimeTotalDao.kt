package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ReadingTimeTotalDao {
    @get:Query("SELECT COALESCE((SELECT readTime FROM readingTimeTotal WHERE id = 1), 0)")
    val totalReadTime: Long

    @Query("INSERT OR IGNORE INTO readingTimeTotal (id, readTime) VALUES (1, 0)")
    fun insertIfAbsent()

    @Query("UPDATE readingTimeTotal SET readTime = readTime + :readTime WHERE id = 1")
    fun increment(readTime: Long)

    @Transaction
    fun addTime(readTime: Long) {
        if (readTime <= 0L) return
        insertIfAbsent()
        increment(readTime)
    }

    @Query("UPDATE readingTimeTotal SET readTime = MAX(readTime, :readTime) WHERE id = 1")
    fun mergeExisting(readTime: Long)

    /** Repeated restoration never adds the same archive twice or reduces an existing total. */
    @Transaction
    fun mergeAtLeast(readTime: Long) {
        require(readTime >= 0L)
        insertIfAbsent()
        mergeExisting(readTime)
    }
}
