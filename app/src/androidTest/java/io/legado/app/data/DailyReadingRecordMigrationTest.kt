package io.legado.app.data

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.entities.DailyReadingRecord
import io.legado.app.data.entities.ReadRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DailyReadingRecordMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrationPreservesCumulativeRecordsWithoutInventingDailyHistory() {
        val name = "daily-reading-migration-test"
        helper.createDatabase(name, 116).apply {
            execSQL("INSERT INTO readRecord(deviceId, bookName, readTime, lastRead) VALUES ('device', '旧书', 900000, 1791158400000)")
            close()
        }
        helper.runMigrationsAndValidate(name, 117, true).use { database ->
            database.query("SELECT readTime, lastRead FROM readRecord WHERE bookName = '旧书'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(900000L, cursor.getLong(0))
                assertEquals(1791158400000L, cursor.getLong(1))
            }
            database.query("SELECT count(*) FROM dailyReadingRecords").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0L, cursor.getLong(0))
            }
        }
    }

    @Test
    fun checkpointsAccumulateWithinDateRangeAndRollbackAsOneBatch() {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val dao = database.dailyReadingRecordDao
            dao.addTimes(listOf(DailyReadingRecord(100, 2000), DailyReadingRecord(101, 3000)))
            dao.addTime(100, 500)
            dao.addTime(102, 0)
            dao.addTime(103, -1)
            assertEquals(listOf(DailyReadingRecord(100, 2500)), dao.getBetween(100, 100))
            assertEquals(listOf(DailyReadingRecord(101, 3000)), dao.getBetween(101, 110))
            assertTrue(dao.getBetween(102, 110).isEmpty())
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_second_day BEFORE UPDATE ON dailyReadingRecords " +
                    "WHEN NEW.epochDay = 102 BEGIN SELECT RAISE(ABORT, 'Abort checkpoint'); END",
            )
            try {
                // No outer transaction: this verifies the DAO batch itself rolls back day 100.
                dao.addTimes(listOf(DailyReadingRecord(100, 700), DailyReadingRecord(102, 800)))
                fail("The second day's update must abort the batch")
            } catch (_: SQLiteException) {
                // Expected storage failure after the first date has already been updated.
            } finally {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_second_day")
            }
            assertEquals(
                listOf(DailyReadingRecord(100, 2500), DailyReadingRecord(101, 3000)),
                dao.getBetween(100, 110),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun cumulativeAndDailyCheckpointRollBackTogetherAndRetryOnlyOnce() {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val records = database.readRecordDao
            val daily = database.dailyReadingRecordDao
            records.insert(ReadRecord(bookName = "当前书", readTime = 5000, lastRead = 1000))
            daily.addTime(100, 5000)
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_second_day BEFORE UPDATE ON dailyReadingRecords " +
                    "WHEN NEW.epochDay = 101 BEGIN SELECT RAISE(ABORT, 'Abort checkpoint'); END",
            )
            val writeCheckpoint = {
                database.runInTransaction {
                    records.addTime("当前书", 1500, 2000)
                    daily.addTimes(listOf(DailyReadingRecord(100, 700), DailyReadingRecord(101, 800)))
                }
            }
            try {
                writeCheckpoint()
                fail("A failed date must roll back the cumulative record and every date")
            } catch (_: SQLiteException) {
                // The cumulative update and first date preceded the forced failure.
            } finally {
                database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_second_day")
            }
            assertEquals(
                listOf(ReadRecord(bookName = "当前书", readTime = 5000, lastRead = 1000)),
                records.all,
            )
            assertEquals(listOf(DailyReadingRecord(100, 5000)), daily.getBetween(100, 101))

            writeCheckpoint()
            assertEquals(
                listOf(ReadRecord(bookName = "当前书", readTime = 6500, lastRead = 2000)),
                records.all,
            )
            assertEquals(
                listOf(DailyReadingRecord(100, 5700), DailyReadingRecord(101, 800)),
                daily.getBetween(100, 101),
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun localIncrementPreservesOtherDevicesAndNeverMovesLastReadBackwards() {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        try {
            val records = database.readRecordDao
            val remote = ReadRecord("other-device", "同名书", 9000, 8000)
            records.insert(remote)
            records.addTime("同名书", 500, 2000)
            records.addTime("同名书", 300, 1000)
            records.addTime("不应创建", 0, 3000)
            records.addTime("不应创建", -1, 3000)
            val byDevice = records.all.associateBy { it.deviceId }
            assertEquals(2, byDevice.size)
            assertEquals(remote, byDevice["other-device"])
            assertEquals(
                ReadRecord(bookName = "同名书", readTime = 800, lastRead = 2000),
                byDevice[""],
            )
            assertEquals(9800L, records.getReadTime("同名书"))
        } finally {
            database.close()
        }
    }
}
