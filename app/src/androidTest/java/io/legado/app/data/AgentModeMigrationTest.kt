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
class AgentModeMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate103To104AddsModeRevisionWithoutChangingLegacyAssistantId() {
        helper.createDatabase(TEST_DB, 103).apply {
            execSQL("INSERT INTO aiChatConversations(id) VALUES ('legacy-session')")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            104,
            true,
            *DatabaseMigrations.migrations
        ).use { database ->
            database.query(
                "SELECT assistantId, agentModeRevision FROM aiChatConversations " +
                    "WHERE id = 'legacy-session'"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("default", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
        }
    }

    @Test
    fun migrate106To107AddsModeEntryContextAndStartMarker() {
        helper.createDatabase(TEST_DB, 106).apply {
            execSQL("INSERT INTO aiChatConversations(id) VALUES ('entry-session')")
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DB,
            107,
            true,
            *DatabaseMigrations.migrations
        ).use { database ->
            database.query(
                "SELECT modeEntryContext, modeEntryStarted FROM aiChatConversations " +
                    "WHERE id = 'entry-session'"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals(0, cursor.getInt(1))
            }
        }
    }

    companion object {
        private const val TEST_DB = "agent-mode-migration-test"
    }
}
