package io.legado.app.ui.font

import io.legado.app.help.font.FontFileEntry
import io.legado.app.help.font.SYSTEM_DEFAULT_FONT
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ReaderFontFilesTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun currentPackageFontAppearsWithoutACatalogEntry() {
        val current = folder.newFile("MaShanZheng-Regular.ttf").toURI().toString()
        val catalogFont = entry(folder.newFile("catalog.ttf"))

        val rows = readerFontFiles(listOf(catalogFont), current, preferred = null)

        assertEquals("MaShanZheng-Regular", rows.first().document.name)
        assertEquals(current, rows.first().document.uri.toString())
        assertNull(rows.first().entry)
        assertEquals(catalogFont, rows.last().entry)
    }

    @Test
    fun currentFontAlreadyInCatalogKeepsItsNameAndAppearsOnce() {
        val file = folder.newFile("download.ttf")
        val catalogFont = entry(file).copy(name = "Download name")

        val rows = readerFontFiles(listOf(catalogFont), file.toURI().toString(), preferred = null)

        assertEquals(catalogFont, rows.single().entry)
        assertEquals("Download name", rows.single().document.name)
    }

    @Test
    fun preferredCatalogFontStaysFirstAndMissingOrSystemChoicesDoNotAddRows() {
        val first = entry(folder.newFile("first.ttf"))
        val preferred = entry(folder.newFile("preferred.ttf"))
        val entries = listOf(first, preferred)
        val current = folder.newFile("current.ttf").toURI().toString()
        val missing = File(folder.root, "missing.ttf").toURI().toString()

        assertEquals(listOf(preferred, first, null), readerFontFiles(entries, current, preferred.uri).map { it.entry })
        assertEquals(listOf(preferred, first), readerFontFiles(entries, missing, preferred.uri).map { it.entry })
        assertEquals(listOf(preferred, first), readerFontFiles(entries, SYSTEM_DEFAULT_FONT, preferred.uri).map { it.entry })
    }

    private fun entry(file: File) = FontFileEntry(
        id = file.canonicalPath,
        name = file.name,
        uri = file.toURI().toString(),
        size = file.length(),
        managed = null,
        canDelete = true,
        directoryUri = null,
        sha256 = null,
    )
}
