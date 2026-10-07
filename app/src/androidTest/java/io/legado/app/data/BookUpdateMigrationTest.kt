package io.legado.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookUpdate
import io.legado.app.data.entities.bookUpdateChapterCount
import io.legado.app.model.bookUpdateDiscoveryFloor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookUpdateMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test fun migrationKeepsBooksAndDailyReadingButDoesNotInferNewsFromTheirTimestamps() {
        val name = "book-update-migration-test"
        helper.createDatabase(name, 117).apply {
            execSQL("INSERT INTO books(bookUrl, origin, name, latestChapterTime, lastCheckCount, totalChapterNum) " +
                "VALUES ('book', 'source', '既有书籍', 1791216000000, 4, 104)")
            execSQL("INSERT INTO dailyReadingRecords(epochDay, readTime) VALUES (100, 900000)")
            close()
        }
        helper.runMigrationsAndValidate(name, 119, true).use { database ->
            database.query("SELECT latestChapterTime, lastCheckCount, totalChapterNum FROM books WHERE bookUrl = 'book'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1791216000000L, cursor.getLong(0))
                assertEquals(4, cursor.getInt(1))
                assertEquals(104, cursor.getInt(2))
            }
            database.query("SELECT readTime FROM dailyReadingRecords WHERE epochDay = 100").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(900000L, cursor.getLong(0))
            }
            database.query("SELECT count(*) FROM bookUpdates").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        }
    }

    @Test fun migrationKeepsExistingDiscoveredUpdatesWithoutInventingChapterCounts() {
        val name = "book-update-count-migration-test"
        helper.createDatabase(name, 118).apply {
            execSQL("INSERT INTO bookUpdates(epochDay, bookUrl, origin, latestChapterTitle, foundAt, totalChapterNum) " +
                "VALUES (100, 'book', 'source', '第105章', 1000, 105)")
            close()
        }
        helper.runMigrationsAndValidate(name, 119, true).use { database ->
            database.query("SELECT epochDay, bookUrl, origin, latestChapterTitle, foundAt, totalChapterNum, " +
                "baselineChapterNum FROM bookUpdates").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(100L, cursor.getLong(0))
                assertEquals("book", cursor.getString(1))
                assertEquals("source", cursor.getString(2))
                assertEquals("第105章", cursor.getString(3))
                assertEquals(1000L, cursor.getLong(4))
                assertEquals(105, cursor.getInt(5))
                assertTrue(cursor.isNull(6))
                assertEquals(1, cursor.count)
            }
        }
    }

    @Test fun sameDayGrowthMergesWithoutOlderResponsesChangingTheTitleOrTime() {
        withDatabase { database ->
            val dao = database.bookUpdateDao
            val first = BookUpdate(100, "a", "source", "第101章", 1000, 101, 100)
            val latest = first.copy(latestChapterTitle = "第105章", foundAt = 2000, totalChapterNum = 105)
            dao.recordIfNewer(first)
            dao.recordIfNewer(latest.copy(baselineChapterNum = 101))
            dao.recordIfNewer(first.copy(foundAt = 3000))
            dao.recordIfNewer(latest.copy(latestChapterTitle = "重复响应", foundAt = 4000))
            assertEquals(listOf(latest), dao.getForDay(100))
            assertEquals(5, bookUpdateChapterCount(dao.getForDay(100).single()))
            val second = latest.copy(bookUrl = "b")
            dao.recordIfNewer(second)
            assertEquals(listOf(latest, second), dao.getForDay(100))
            dao.recordIfNewer(first.copy(epochDay = 101))
            dao.recordIfNewer(first.copy(origin = "another-source"))
            assertEquals(listOf(first.copy(epochDay = 101)), dao.getForDay(101))
            assertEquals(3, dao.getForDay(100).size)
        }
    }

    @Test fun previousDayHighPointPreventsUncommittedDirectoryGrowthBeingCountedTwice() {
        withDatabase { database ->
            val dao = database.bookUpdateDao
            val previous = BookUpdate(100, "book", "source", "第105章", 1000, 105, 100)
            dao.recordIfNewer(previous.copy(epochDay = 99, totalChapterNum = 102))
            dao.recordIfNewer(previous)
            dao.recordIfNewer(previous.copy(bookUrl = "other-book", totalChapterNum = 300))
            dao.recordIfNewer(previous.copy(origin = "other-source", totalChapterNum = 400))
            assertEquals(previous, dao.getLatestBeforeDay(101, "book", "source"))
            val floor = bookUpdateDiscoveryFloor(100,
                dao.getLatestBeforeDay(101, "book", "source")?.totalChapterNum)
            val today = previous.copy(epochDay = 101, totalChapterNum = 108, baselineChapterNum = floor)
            dao.recordIfNewer(today)
            assertEquals(3, bookUpdateChapterCount(dao.getForDay(101).single()))
            assertEquals(previous, dao.getLatestBeforeDay(101, "book", "source"))
            assertEquals(today, dao.getLatestBeforeDay(102, "book", "source"))
            assertNull(dao.getLatestBeforeDay(99, "book", "source"))
            assertNull(dao.getLatestBeforeDay(101, "missing", "source"))
        }
    }

    @Test fun anotherGrowthKeepsAnUnknownDayUnknownInsteadOfShowingOnlyTheLastIncrement() {
        withDatabase { database ->
            val dao = database.bookUpdateDao
            val previous = BookUpdate(100, "book", "source", "第105章", 1000, 105)
            dao.recordIfNewer(previous)
            val next = previous.copy(latestChapterTitle = "第108章", foundAt = 2000,
                totalChapterNum = 108, baselineChapterNum = 105)
            dao.recordIfNewer(next)
            assertNull(dao.getForDay(100).single().baselineChapterNum)
            assertNull(bookUpdateChapterCount(dao.getForDay(100).single()))
            dao.recordIfNewer(next.copy(epochDay = 101, totalChapterNum = 110, baselineChapterNum = 108))
            assertEquals(2, bookUpdateChapterCount(dao.getForDay(101).single()))
        }
    }

    @Test fun currentShelfJoinKeepsReadBooksButHidesDeletedChangedSourceLocalAndTemporaryBooks() {
        withDatabase { database ->
            val dao = database.bookUpdateDao
            val book = Book(bookUrl = "kept", origin = "source", name = "已读", totalChapterNum = 101)
            val switched = Book(bookUrl = "switched", origin = "new-source", name = "换源")
            val local = Book(bookUrl = "local", origin = "source", name = "本地", type = BookType.text or BookType.local)
            val temporary = Book(bookUrl = "temporary", origin = "source", name = "临时", type = BookType.text or BookType.notShelf)
            database.bookDao.insert(book, switched, local, temporary)
            listOf("kept", "switched", "local", "temporary", "deleted").forEach { url ->
                dao.recordIfNewer(BookUpdate(100, url, "source", "新章", 1000, 101, 100))
            }
            assertEquals(listOf("kept"), dao.getBooksForDay(100).map { it.bookUrl })
            assertEquals(1, bookUpdateChapterCount(dao.get(100, "kept", "source")!!))
            val read = book.copy(durChapterIndex = 100, lastCheckCount = 0)
            database.bookDao.replace(book, read)
            assertEquals(listOf("kept"), dao.getBooksForDay(100).map { it.bookUrl })
            assertEquals(5, dao.getForDay(100).size)
            database.bookDao.delete(read)
            assertTrue(dao.getBooksForDay(100).isEmpty())
            assertEquals(5, dao.getForDay(100).size)
        }
    }

    private fun withDatabase(block: (AppDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
