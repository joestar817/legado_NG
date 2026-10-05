package io.legado.app.help.storage

import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.utils.fromJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Md3BackupCompatibilityTest {

    @Test
    fun detectsMd3BackupFromPreferenceTypesOrExclusiveGroups() {
        assertTrue(
            Md3BackupCompatibility.isBackup(
                mapOf(PreferKey.showBrightnessView to "0"),
                emptyList()
            )
        )
        assertTrue(
            Md3BackupCompatibility.isBackup(
                emptyMap<String, Any?>(),
                listOf(-20L)
            )
        )
        assertFalse(
            Md3BackupCompatibility.isBackup(
                mapOf(
                    PreferKey.showBrightnessView to true,
                    PreferKey.saveTabPosition to 2
                ),
                listOf(-1L, -2L, -3L, -6L, 1L)
            )
        )
    }

    @Test
    fun keepsOnlyUserGroupsFromMd3Backup() {
        assertTrue(Md3BackupCompatibility.shouldRestoreGroup(1L))
        assertFalse(Md3BackupCompatibility.shouldRestoreGroup(-1L))
        assertFalse(Md3BackupCompatibility.shouldRestoreGroup(-20L))
    }

    @Test
    fun normalizesPortableReaderPreferencesAndIgnoresShellState() {
        assertNull(
            Md3BackupCompatibility.normalizePreference(PreferKey.showBrightnessView, "0")
        )
        assertNull(
            Md3BackupCompatibility.normalizePreference(PreferKey.showBrightnessView, "2")
        )
        assertNull(
            Md3BackupCompatibility.normalizePreference(PreferKey.brightnessVwPos, "0")
        )
        assertNull(
            Md3BackupCompatibility.normalizePreference(PreferKey.readStyleSelect, 2L)
        )
        assertNull(
            Md3BackupCompatibility.normalizePreference(PreferKey.shareLayout, true)
        )
        assertEquals(
            46,
            Md3BackupCompatibility.normalizePreference(PreferKey.autoReadSpeed, 46L)
        )
        assertNull(
            Md3BackupCompatibility.normalizePreference(PreferKey.saveTabPosition, -8L)
        )
        assertNull(
            Md3BackupCompatibility.normalizePreference("mainNavigationOrder", "home,bookshelf")
        )
    }

    @Test
    fun parsesMd3BookSimulationDateWithoutDroppingTheBook() {
        val books = Md3BackupCompatibility.bookGson.fromJsonArray<Book>(
            """[{"bookUrl":"book","readConfig":{"readSimulating":true,"startDate":"2026-08-04"}}]"""
        ).getOrThrow()

        assertEquals(1, books.size)
        assertEquals(LocalDate.of(2026, 8, 4), books.single().readConfig?.startDate)
    }

    @Test
    fun bookGsonRoundTripsLocalDate() {
        val book = Book(bookUrl = "book").apply {
            config.startDate = LocalDate.of(2026, 8, 4)
        }
        val restored = Md3BackupCompatibility.bookGson.fromJson(
            Md3BackupCompatibility.bookGson.toJson(book),
            Book::class.java,
        )
        assertEquals(LocalDate.of(2026, 8, 4), restored.config.startDate)
    }
}
