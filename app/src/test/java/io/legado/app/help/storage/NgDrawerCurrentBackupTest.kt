package io.legado.app.help.storage

import com.google.gson.JsonParser
import io.legado.app.constant.PreferKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NgDrawerCurrentBackupTest {
    @Test
    fun `current profile images are portable without requiring a saved theme`() {
        val profile = JsonParser.parseString("""{
            "source":"custom_image",
            "light":{"imagePath":"/private/current/day.webp"},
            "dark":{"imagePath":"/private/current/night.webp","backgroundColor":42}
        }""").asJsonObject
        BackupResources.collectDrawerProfileImages(profile) { "backup-resource://resources/${it.substringAfterLast('/')}" }
        val restored = BackupResources.transform(profile) { it.replace("backup-resource://", "/restored/") }.asJsonObject
        assertEquals("/restored/resources/day.webp", restored.getAsJsonObject("light")["imagePath"].asString)
        assertEquals("/restored/resources/night.webp", restored.getAsJsonObject("dark")["imagePath"].asString)
        assertEquals("custom_image", restored["source"].asString)
        assertEquals(42, restored.getAsJsonObject("dark")["backgroundColor"].asInt)
    }

    @Test
    fun `current drawer preferences use appearance backup and theme ignore policy`() {
        listOf(PreferKey.ngDrawerBackground).forEach { key ->
            assertEquals(BackupModule.APPEARANCE, BackupModules.preferenceModule(key))
            assertTrue(key in BackupRestorePolicy.themeConfigPreferenceKeys)
            assertFalse(BackupRestorePolicy.shouldRestorePreference(key, isMd3Backup = true))
            assertTrue(BackupRestorePolicy.shouldRestorePreference(key, isMd3Backup = false, nativePackage = true))
        }
    }
}
