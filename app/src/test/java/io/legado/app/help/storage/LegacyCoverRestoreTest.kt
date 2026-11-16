package io.legado.app.help.storage

import io.legado.app.data.entities.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyCoverRestoreTest {
    @Test fun unavailableLocalReferencesAreClearedForLegacyBackups() {
        val references = listOf(
            "/storage/emulated/0/Android/data/io.legado.app.yuedu.release/files/covers/a.png",
            "file:///storage/emulated/0/missing.png",
            "content://example/missing",
            "missing/relative.png",
        )
        references.forEach { reference ->
            assertNull(LegacyCoverRestore.restoredReference(reference, false) { false })
        }
    }

    @Test fun readableLocalReferencesRemainAvailable() {
        val references = listOf("/storage/emulated/0/cover.png", "file:///cover.png", "content://example/cover", "relative.png")
        references.forEach { reference ->
            assertEquals(reference, LegacyCoverRestore.restoredReference(reference, false) { true })
        }
    }

    @Test fun nativeBackupsAndNonLocalReferencesAreNotProbed() {
        val nativeReference = "/missing/native-cover.png"
        assertEquals(nativeReference, LegacyCoverRestore.restoredReference(nativeReference, true) {
            error("native backup must not be probed")
        })
        listOf(null, "", "https://example.com/cover.png", "data:image/png;base64,AA==",
            "assets://cover.png", "file:///android_asset/cover.png", "android.resource://app/cover")
            .forEach { reference ->
                assertEquals(reference, LegacyCoverRestore.restoredReference(reference, false) {
                    error("non-local cover must not be probed")
                })
            }
    }

    @Test fun clearingTheCustomReferenceFallsBackToTheBooksSourceCover() {
        val book = Book(coverUrl = "https://example.com/source.png", customCoverUrl = "/missing/cover.png")
        book.customCoverUrl = LegacyCoverRestore.restoredReference(book.customCoverUrl, false) { false }
        assertEquals("https://example.com/source.png", book.getDisplayCover())
    }
}
