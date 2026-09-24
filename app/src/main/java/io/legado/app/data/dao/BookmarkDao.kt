package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.Bookmark
import kotlinx.coroutines.flow.Flow


@Dao
interface BookmarkDao {

    @get:Query(
        """
        select * from bookmarks order by bookName collate localized, bookAuthor collate localized, chapterIndex, chapterPos
    """
    )
    val all: List<Bookmark>

    @Query("select * from bookmarks order by bookName collate localized, bookAuthor collate localized, chapterIndex, chapterPos")
    fun flowAll(): Flow<List<Bookmark>>

    @Query(
        """select * from bookmarks 
        where bookName = :bookName and bookAuthor = :bookAuthor 
        order by chapterIndex"""
    )
    fun flowByBook(bookName: String, bookAuthor: String): Flow<List<Bookmark>>

    @Query(
        """SELECT * FROM bookmarks 
        where bookName = :bookName and bookAuthor = :bookAuthor 
        and chapterName like '%'||:key||'%' or content like '%'||:key||'%'
        order by chapterIndex"""
    )
    fun flowSearch(bookName: String, bookAuthor: String, key: String): Flow<List<Bookmark>>

    @Query(
        """select * from bookmarks 
        where bookName = :bookName and bookAuthor = :bookAuthor 
        order by chapterIndex"""
    )
    fun getByBook(bookName: String, bookAuthor: String): List<Bookmark>

    @Query(
        """SELECT * FROM bookmarks 
        where bookName = :bookName and bookAuthor = :bookAuthor 
        and chapterName like '%'||:key||'%' or content like '%'||:key||'%'
        order by chapterIndex"""
    )
    fun search(bookName: String, bookAuthor: String, key: String): List<Bookmark>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg bookmark: Bookmark)

    @Query("""SELECT * FROM bookmarks WHERE bookmarkType = 1
        AND bookName = :bookName AND bookAuthor = :bookAuthor
        AND chapterIndex = :chapterIndex AND chapterPos = :chapterPos
        AND endChapterIndex = :endChapterIndex
        AND (endChapterIndex > chapterIndex OR endChapterPos > chapterPos)
        AND (endChapterPos = :endChapterPos OR bookText = :bookText)
        ORDER BY time DESC LIMIT 1""")
    fun findTextHighlight(bookName: String, bookAuthor: String, chapterIndex: Int,
                          chapterPos: Int, endChapterIndex: Int, endChapterPos: Int, bookText: String): Bookmark?

    @Transaction
    fun getOrInsertTextHighlight(bookmark: Bookmark): Bookmark {
        require(bookmark.isTextHighlight)
        return findTextHighlight(bookmark.bookName, bookmark.bookAuthor, bookmark.chapterIndex,
            bookmark.chapterPos, bookmark.endChapterIndex, bookmark.endChapterPos, bookmark.bookText)
            ?: bookmark.also { insert(it) }
    }

    @Update
    fun update(bookmark: Bookmark)

    @Delete
    fun delete(vararg bookmark: Bookmark)

}
