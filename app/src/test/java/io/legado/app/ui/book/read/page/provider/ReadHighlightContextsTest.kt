package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReadHighlightContextsTest {
    @Test
    fun `paragraph offsets retain indentation and UTF16 surrogate pairs`() {
        val paragraphs = listOf("　　“第一段😀", "　　第二段”,旁白")
        val slices = prepareReadHighlightContexts(paragraphs)
        val first = requireNotNull(slices[0])
        val second = requireNotNull(slices[1])
        assertSame(first.context, second.context)
        assertEquals(0, first.offset)
        assertEquals(paragraphs[0].length + 1, second.offset)
        assertEquals(paragraphs.joinToString("\n"), first.context.text)
        paragraphs.forEachIndexed { index, paragraph ->
            val slice = requireNotNull(slices[index])
            assertEquals(paragraph, slice.context.text.substring(slice.offset, slice.offset + paragraph.length))
        }
    }

    @Test
    fun `media and opaque blocks isolate neighbouring contexts`() {
        val slices = prepareReadHighlightContexts(listOf(null, "“之前", null, null, "之后”", null))
        assertNull(slices[0])
        assertNull(slices[2])
        assertNull(slices[3])
        assertNull(slices[5])
        val before = requireNotNull(slices[1])
        val after = requireNotNull(slices[4])
        assertNotSame(before.context, after.context)
        assertEquals("“之前", before.context.text)
        assertEquals("之后”", after.context.text)
        assertEquals(0, after.offset)
    }

    @Test
    fun `empty paragraphs preserve the source offsets`() {
        val slices = prepareReadHighlightContexts(listOf("第一段", "", "第三段"))
        assertEquals("第一段\n\n第三段", requireNotNull(slices[0]).context.text)
        assertEquals(4, requireNotNull(slices[1]).offset)
        assertEquals(5, requireNotNull(slices[2]).offset)
        assertEquals(emptyList<ReadHighlightContextSlice?>(), prepareReadHighlightContexts(emptyList()))
    }
}
