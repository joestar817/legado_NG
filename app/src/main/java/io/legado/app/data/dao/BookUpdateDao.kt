package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookUpdate
import io.legado.app.data.entities.mergeBookUpdateIfNewer

@Dao
interface BookUpdateDao {
    @Query("SELECT * FROM bookUpdates WHERE epochDay = :epochDay ORDER BY foundAt DESC, bookUrl ASC, origin ASC")
    fun getForDay(epochDay: Long): List<BookUpdate>

    /** Batch the current shelf side of the join without exposing another Book host capability. */
    @Query("""
        SELECT books.* FROM books INNER JOIN bookUpdates
        ON books.bookUrl = bookUpdates.bookUrl AND books.origin = bookUpdates.origin
        WHERE bookUpdates.epochDay = :epochDay
        AND books.type & ${BookType.local} = 0 AND books.type & ${BookType.notShelf} = 0
        AND books.origin != '${BookType.localTag}' AND books.origin NOT LIKE '${BookType.webDavTag}%'
        ORDER BY bookUpdates.foundAt DESC, books.bookUrl ASC
    """)
    fun getBooksForDay(epochDay: Long): List<Book>

    @Query("SELECT * FROM bookUpdates WHERE epochDay = :epochDay AND bookUrl = :bookUrl AND origin = :origin")
    fun get(epochDay: Long, bookUrl: String, origin: String): BookUpdate?

    @Query("""
        SELECT * FROM bookUpdates
        WHERE epochDay < :epochDay AND bookUrl = :bookUrl AND origin = :origin
        ORDER BY epochDay DESC LIMIT 1
    """)
    fun getLatestBeforeDay(epochDay: Long, bookUrl: String, origin: String): BookUpdate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(record: BookUpdate)

    /** Repeated responses do not add counts; an older unknown baseline stays unknown all day. */
    @Transaction
    fun recordIfNewer(record: BookUpdate) {
        val previous = get(record.epochDay, record.bookUrl, record.origin)
        mergeBookUpdateIfNewer(previous, record)?.let(::insert)
    }
}
