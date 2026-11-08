package io.legado.app.help.font

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FontLibraryPathsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun acceptsOwnedFilesAndEquivalentFileUris() {
        val root = temporary.newFolder("font")
        val font = File(root, "中文.ttf")
        assertEquals(font.canonicalFile, FontLibraryPaths.managedFile(font.path, listOf(root)))
        assertEquals(font.canonicalFile, FontLibraryPaths.managedFile(font.toURI().toString(), listOf(root)))
    }

    @Test fun rejectsExternalFilesTraversalNestedFilesAndNonFontTargets() {
        val root = temporary.newFolder("font")
        listOf(File(root.parentFile, "outside.ttf").path, File(root, "../outside.ttf").path,
            File(root, "nested/font.ttf").path, File(root, "index.json").path,
            "content://provider/font.ttf", "relative.ttf").forEach { path ->
            assertNull(path, FontLibraryPaths.managedFile(path, listOf(root)))
        }
    }

    @Test fun recoveryCanResolveMissingFilesWithoutTakingOwnershipOfOtherDirectories() {
        val root = temporary.newFolder("font")
        val previous = temporary.newFolder("interface-fonts")
        val missing = File(previous, "${"a".repeat(64)}.font")
        assertFalse(missing.exists())
        assertEquals(missing.canonicalFile, FontLibraryPaths.managedFile(missing.path, listOf(root, previous)))
        assertNull(FontLibraryPaths.managedFile(missing.path, listOf(root)))
    }

    @Test fun hidesGeneratedResourceSuffixWithoutTruncatingOrdinaryNames() {
        val hash = "a".repeat(64)
        assertEquals("风云体 标题字形版", FontLibraryPaths.displayName("风云体_标题字形版_-${"b".repeat(20)}.ttf", hash))
        assertEquals("my_font", FontLibraryPaths.displayName("my_font.otf", hash))
        assertEquals("本地字体 aaaaaaaa", FontLibraryPaths.displayName("$hash.font", hash))
    }

    @Test fun labelProtocolUsesStableNamesAndNeverPersistsAbsoluteFilePaths() {
        val gson = Gson()
        val index = FontLibraryIndex(fonts = listOf(FontLibraryLabel("a".repeat(64), "示例")))
        val json = gson.toJson(index)
        assertTrue(json.contains("\"schemaVersion\""))
        assertTrue(json.contains("\"sha256\""))
        assertFalse(json.contains("path"))
        assertEquals(index, gson.fromJson(json, FontLibraryIndex::class.java))
    }
}
