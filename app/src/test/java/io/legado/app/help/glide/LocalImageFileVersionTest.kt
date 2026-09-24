package io.legado.app.help.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalImageFileVersionTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun unchangedFileKeepsItsCacheVersion() {
        val file = temporary.newFile("cover.jpg").apply { writeText("cover") }
        assertEquals(localImageFileVersion(file), localImageFileVersion(file))
    }

    @Test fun samePathAndSizeWithNewModificationTimeInvalidatesCache() {
        val file = temporary.newFile("cover.jpg").apply { writeText("red") }
        assertTrue(file.setLastModified(1_700_000_000_000L))
        val before = localImageFileVersion(file)
        file.writeText("tan")
        assertTrue(file.setLastModified(1_700_000_010_000L))
        assertNotEquals(before, localImageFileVersion(file))
    }

    @Test fun changedSizeInvalidatesCacheEvenWhenTimestampIsPreserved() {
        val file = temporary.newFile("cover.jpg").apply { writeText("red") }
        val timestamp = file.lastModified()
        val before = localImageFileVersion(file)
        file.writeText("blue")
        assertTrue(file.setLastModified(timestamp))
        assertNotEquals(before, localImageFileVersion(file))
    }
}
