package io.legado.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyAgentStateRemovalMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate105To106RemovesLegacyStateTablesAndPreservesMemoriesAsNotes() {
        helper.createDatabase(TEST_DB, 105).apply {
            execSQL(
                "INSERT INTO agentMemories(id, memoryType, title, content) " +
                    "VALUES ('legacy-memory', 'checkpoint', '旧状态', '保留内容')"
            )
            execSQL("INSERT INTO agentCheckpoints(id) VALUES ('legacy-state')")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            106,
            true,
            *DatabaseMigrations.migrations
        ).use { database ->
            database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' " +
                    "AND name IN ('agentCheckpoints', 'agentCheckpointCommits', " +
                    "'agentCheckpointReceiptLinks')"
            ).use { cursor ->
                assertEquals(false, cursor.moveToFirst())
            }
            database.query(
                "SELECT memoryType, title, content FROM agentMemories WHERE id = 'legacy-memory'"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("note", cursor.getString(0))
                assertEquals("旧状态", cursor.getString(1))
                assertEquals("保留内容", cursor.getString(2))
            }
        }
    }

    companion object {
        private const val TEST_DB = "legacy-agent-state-removal-migration-test"
    }
}
