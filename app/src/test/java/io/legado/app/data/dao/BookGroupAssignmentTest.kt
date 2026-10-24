package io.legado.app.data.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.legado.app.data.AppDatabase
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [35])
class BookGroupAssignmentTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun batchAddKeepsEachBooksOwnGroupsAndTheLatestReadingProgress() {
        val first = Book(bookUrl = "first", group = 1, durChapterPos = 10)
        val second = Book(bookUrl = "second", group = 2)
        val untouched = Book(bookUrl = "untouched", group = 8)
        db.bookDao.insert(first, second, untouched)
        // The selected book objects still contain the old position when the user confirms.
        db.bookDao.upProgress(first.bookUrl, 1234)
        db.runInTransaction {
            listOf(first, second).forEach { db.bookDao.addGroups(it.bookUrl, 4) }
        }

        assertEquals(5L, db.bookDao.getBook("first")!!.group)
        assertEquals(6L, db.bookDao.getBook("second")!!.group)
        assertEquals(1234, db.bookDao.getBook("first")!!.durChapterPos)
        assertEquals(8L, db.bookDao.getBook("untouched")!!.group)
    }

    @Test
    fun addingAPartlyJoinedGroupFillsMissingMembershipsAndIsIdempotent() {
        db.bookDao.insert(Book(bookUrl = "first", group = 3), Book(bookUrl = "second", group = 4))
        repeat(2) {
            db.runInTransaction {
                listOf("first", "second").forEach { db.bookDao.addGroups(it, 2) }
            }
        }
        assertEquals(3L, db.bookDao.getBook("first")!!.group)
        assertEquals(6L, db.bookDao.getBook("second")!!.group)
    }

    @Test
    fun movingToAnExistingMembershipRemovesTheOtherMemberships() {
        db.bookDao.insert(Book(bookUrl = "first", group = 3, durChapterPos = 765))
        db.bookDao.setGroups("first", 2)
        assertEquals(2L, db.bookDao.getBook("first")!!.group)
        assertEquals(765, db.bookDao.getBook("first")!!.durChapterPos)
    }

    @Test
    fun lastBitSupportsAddQueryNamesAndRemoval() = runBlocking {
        db.bookGroupDao.insert(
            BookGroup(groupId = BookGroup.IdAll, groupName = "全部"),
            BookGroup(groupId = 1, groupName = "在读", order = 1),
            BookGroup(groupId = Long.MIN_VALUE, groupName = "第64组", order = 2),
        )
        db.bookDao.insert(Book(bookUrl = "first", group = 1))
        db.bookDao.addGroups("first", Long.MIN_VALUE)

        assertEquals(listOf("first"), db.bookDao.getBooksByGroup(Long.MIN_VALUE).map { it.bookUrl })
        assertEquals(listOf("first"), db.bookDao.flowByUserGroup(Long.MIN_VALUE).first().map { it.bookUrl })
        assertEquals(listOf("在读", "第64组"), db.bookGroupDao.getGroupNames(1L or Long.MIN_VALUE))
        assertEquals(listOf(1L, Long.MIN_VALUE), db.bookGroupDao.flowSelect().first().map { it.groupId })

        db.bookDao.removeGroup(Long.MIN_VALUE)
        assertEquals(1L, db.bookDao.getBook("first")!!.group)
    }

    @Test
    fun allocationReusesALowGapWhenTheLastBitAlreadyExists() {
        db.bookGroupDao.insert(*((0 until 64).filter { it != 3 }.map {
            BookGroup(groupId = 1L shl it, order = it)
        }).toTypedArray())
        assertTrue(db.bookGroupDao.canAddGroup)
        assertEquals(8L, db.bookGroupDao.getUnusedId())
        assertEquals(63, db.bookGroupDao.maxOrder)
        db.bookGroupDao.insert(BookGroup(groupId = 8))
        assertFalse(db.bookGroupDao.canAddGroup)
    }
}
