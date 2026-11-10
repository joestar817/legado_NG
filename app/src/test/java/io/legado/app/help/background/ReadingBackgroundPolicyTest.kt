package io.legado.app.help.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ReadingBackgroundPolicyTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun onlyDirectAppCopiesAreDeletable() {
        val root = temporary.newFolder("bg")
        val downloaded = File(root, "resource-center").apply { mkdirs() }
        val external = temporary.newFolder("gallery")
        val roots = listOf(root, downloaded)
        assertTrue(ReadingBackgroundLibrary.isOwned(File(root, "paper.png"), roots))
        assertTrue(ReadingBackgroundLibrary.isOwned(File(downloaded, "paper.png"), roots))
        assertFalse(ReadingBackgroundLibrary.isOwned(File(external, "paper.png"), roots))
        assertFalse(ReadingBackgroundLibrary.isOwned(File(root, "nested/paper.png"), roots))
        assertFalse(ReadingBackgroundLibrary.isOwned(File(root, "../gallery/paper.png"), roots))
    }

    @Test fun identicalNamesAndBytesDoNotShareUsage() {
        val first = temporary.newFolder("first").resolve("paper.png").apply { writeText("same bytes") }.canonicalPath
        val second = temporary.newFolder("second").resolve("paper.png").apply { writeText("same bytes") }.canonicalPath
        val result = ReadingBackgroundUsage.match(listOf(first, second), listOf(first to "某书·夜间"))
        assertEquals(listOf("某书·夜间"), result[first])
        assertTrue(result.getValue(second).isEmpty())
    }

    @Test fun allReferencesRemainVisibleAndDuplicateLocationsAreCollapsed() {
        val path = temporary.root.resolve("paper.png").canonicalPath
        val result = ReadingBackgroundUsage.match(listOf(path), listOf(
            path to "日间", path to "夜间", path to "墨水屏", path to "页眉", path to "日间",
        ))
        assertEquals(listOf("日间", "夜间", "墨水屏", "页眉"), result[path])
    }
}
