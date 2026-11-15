package io.legado.app.ui.book.read.page.provider

import io.legado.app.ui.book.read.page.entities.ReadTitleSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadTitleStyleParserTest {
    @Test
    fun `chapter number mode breaks once after a complete prefix of any width`() {
        val names = listOf("第1章", "第112章", "第1000章", "第一千章", "第１０００章", "第 12 回")
        val chapterName = "章名里还有第3章"
        for (number in names) {
            assertEquals(
                listOf(ReadTitleSegment(number, main = true, 1f), ReadTitleSegment(chapterName, main = false, 0.9f)),
                parse("　$number $chapterName", ReadTitleSegmentType.CHAPTER_NUMBER),
            )
        }
    }

    @Test
    fun `chapter number mode leaves titles without a prefix or name intact`() {
        for (title in listOf("楔子 第12章", "第112章", "第一千章：", "第十二章\n章名", "第章 章名", "第九卷 章名")) {
            assertEquals(
                listOf(ReadTitleSegment(title, main = true, 1f)),
                parse(title, ReadTitleSegmentType.CHAPTER_NUMBER),
            )
        }
        assertEquals(
            listOf("第112章", "不堪一击！比比东的惊喜"),
            parse("第112章不堪一击！比比东的惊喜", ReadTitleSegmentType.CHAPTER_NUMBER).map { it.text },
        )
    }

    @Test
    fun `serialized MD3 modes keep fixed positions and all matching delimiters`() {
        val title = "第112章 新章启程"
        assertEquals(listOf(title), parse(title, 0).map { it.text })
        assertEquals(listOf("第112", "章 新章启程"), parse(title, 1).map { it.text })
        assertEquals(listOf("第112章", "新章", "启程"), parse(title, 2, "章").map { it.text })
        assertEquals(listOf("第112章", "新章启程"), parse(title, 3, "^第[0-9]{1,6}章").map { it.text })
        assertEquals(listOf(title), parse(title, 3, "[").map { it.text })
    }

    private fun parse(title: String, type: Int, flags: String = "") =
        ReadTitleStyleParser.parse(title, type, distance = 4, flags = flags, subTitleScale = 0.9f)
}
