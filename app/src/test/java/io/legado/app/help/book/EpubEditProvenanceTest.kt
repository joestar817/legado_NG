package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EpubEditProvenanceTest {
    @Test
    fun oldCacheWithOnlyOuterWhitespaceTrimKeepsExactPositions() {
        val source = "　　<img src=\"OPS/a.jpg\">\n正文\n　　"
        val cached = "<img src=\"OPS/a.jpg\">\n正文"

        val positions = checkNotNull(EpubEditProvenance.restore(File("unused"), source, cached))

        assertEquals(source, positions.source)
        assertEquals(cached, positions.text)
        val start = cached.indexOf("正文")
        assertEquals(source.indexOf("正文"), positions.sourcePosition(start, ContentPositionMap.Affinity.BEFORE))
        assertEquals(source.indexOf("正文") + 2,
            positions.sourcePosition(start + 2, ContentPositionMap.Affinity.AFTER))
    }

    @Test
    fun oldCacheWithoutEpubHeadingKeepsExactBodyPositions() {
        val cached = "　　<img src=\"OPS/a.jpg\">\n正文"
        val source = "人物简介\n${cached}\n　"

        val positions = checkNotNull(EpubEditProvenance.restore(File("unused"), source, cached, "人物简介"))

        assertEquals(cached, positions.text)
        val bodyStart = source.indexOf(cached)
        assertEquals(bodyStart, positions.sourcePosition(0, ContentPositionMap.Affinity.BEFORE))
        assertEquals(bodyStart + cached.length,
            positions.sourcePosition(cached.length, ContentPositionMap.Affinity.AFTER))
    }
}
