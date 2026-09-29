package io.legado.app.ui.book.read.epub

import io.legado.app.model.epub.EpubContentProjection
import io.legado.app.model.epub.EpubMappedContent
import io.legado.app.model.epub.EpubSourceDocument
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EpubMappingPersistenceTest {
    private fun record(text: String) = EpubOpeningMappingFile.Record("request", text, "signature",
        EpubMappedContent(text, listOf(EpubContentProjection.ProjectedDocument(
            EpubSourceDocument("chapter.xhtml", "<p>$text</p>", emptyList(), emptyList(), emptySet(), 0), emptyList(), emptyList()))))

    @Test fun delayedOldSerializationCannotReplaceNewFileAndCleansPartial() {
        val directory = Files.createTempDirectory("epub-mapping-test").toFile()
        try {
            val file = directory.resolve("mapping.bin")
            val old = EpubMappingPersistence.next()
            val current = EpubMappingPersistence.next()
            EpubOpeningMappingFile.write(file, record("new")) { EpubMappingPersistence.publish(current, it) }
            EpubOpeningMappingFile.write(file, record("old")) { EpubMappingPersistence.publish(old, it) }
            assertEquals("new", EpubOpeningMappingFile.read(file, "request")?.mapped?.text)
            assertEquals(listOf("mapping.bin"), directory.list()!!.toList())
        } finally { directory.listFiles()?.forEach { it.delete() }; directory.delete() }
    }
}
