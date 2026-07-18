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
class AgentToolReceiptAcknowledgementMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate104To105CreatesGenericReceiptAcknowledgementTable() {
        helper.createDatabase(TEST_DB, 104).close()

        helper.runMigrationsAndValidate(
            TEST_DB,
            105,
            true,
            *DatabaseMigrations.migrations
        ).use { database ->
            database.query(
                "SELECT name FROM sqlite_master " +
                    "WHERE type = 'table' AND name = 'agentToolReceiptAcknowledgements'"
            ).use { cursor ->
                assertEquals(true, cursor.moveToFirst())
            }
        }
    }

    companion object {
        private const val TEST_DB = "agent-tool-receipt-ack-migration-test"
    }
}
