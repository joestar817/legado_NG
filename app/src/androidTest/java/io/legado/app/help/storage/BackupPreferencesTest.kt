package io.legado.app.help.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupPreferencesTest {
    @Test fun samePreferenceNameUsesEachRequestedDirectory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "backup-preferences-test-$id")
        val first = File(root, "restore").apply { mkdirs() }
        val second = File(root, "backup").apply { mkdirs() }
        val beforeName = "codex-before-$id"
        val afterName = "codex-after-$id"
        val regularDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        try {
            assertTrue(context.getSharedPreferences(beforeName, Context.MODE_PRIVATE).edit().putBoolean("test", true).commit())
            assertTrue(File(regularDirectory, "$beforeName.xml").isFile)
            val restored = requireNotNull(BackupPreferences.open(first.path, "config"))
            val backup = requireNotNull(BackupPreferences.open(second.path, "config"))
            assertTrue(restored.edit().putString("test", "restore").commit())
            assertTrue(backup.edit().putString("test", "backup").commit())
            assertTrue(File(first, "config.xml").isFile)
            assertTrue(File(second, "config.xml").isFile)
            assertEquals("restore", requireNotNull(BackupPreferences.open(first.path, "config")).getString("test", null))
            assertEquals("backup", requireNotNull(BackupPreferences.open(second.path, "config")).getString("test", null))
            assertTrue(context.getSharedPreferences(afterName, Context.MODE_PRIVATE).edit().putBoolean("test", true).commit())
            assertTrue(File(regularDirectory, "$afterName.xml").isFile)
        } finally {
            context.deleteSharedPreferences(beforeName)
            context.deleteSharedPreferences(afterName)
            root.deleteRecursively()
        }
    }
}
