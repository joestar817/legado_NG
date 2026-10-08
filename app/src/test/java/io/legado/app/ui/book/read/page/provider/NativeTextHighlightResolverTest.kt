package io.legado.app.ui.book.read.page.provider

import io.legado.app.data.entities.Bookmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeTextHighlightResolverTest {

    @Test
    fun actualReportedOffsetsRecoverTheEntireQuoteWithOneOrNoIndent() {
        val sentence = "山羊面具的眼睛处挖了两个空洞，露出了他那狡黠的双眼。"
        // Same UTF-16 geometry as the captured chapter: title 7, 16 preceding paragraphs,
        // 470 body characters, one comment token and one newline per preceding paragraph.
        val paragraphs = List(15) { "前文\uFFFC" } + "前".repeat(440).plus("\uFFFC") +
                "$sentence\uFFFC" + "下一段正文\uFFFC"
        val old = layout(paragraphs, indent = "　　", title = "第1章 空屋")
        val mark = legacy(542, 569, "　$sentence")
        assertEquals(mark.bookText, old.displayText.substring(542, 569))
        assertEquals(range(542, 569), resolve(mark, old))

        val one = layout(paragraphs, indent = "　", title = "第1章 空屋")
        assertEquals(range(526, 552), resolve(mark, one))
        assertEquals(sentence, selected(mark, one))
        assertEquals(sentence, selected(mark, layout(paragraphs, indent = "", title = "第1章 空屋")))
    }

    @Test
    fun canonicalCoordinatesSurviveEveryIndentAndAnEditedQuote() {
        val paragraphs = listOf("甲段", "目标😀文字", "尾段")
        val original = layout(paragraphs)
        val start = original.canonicalText.indexOf("目标")
        val mark = canonical(start, start + "目标😀文字".length, "用户修改过的书签摘录")
        for (indent in listOf("", " ", "　", "　　", "    ")) {
            assertEquals("目标😀文字", selected(mark, layout(paragraphs, indent)))
        }
    }

    @Test
    fun canonicalRangeSurvivesHiddenAndReflowedTitle() {
        val paragraphs = listOf("目标文字")
        val map = layout(paragraphs, title = "长标题")
        val start = map.canonicalText.indexOf("目标")
        val mark = canonical(start, start + 4)
        assertEquals("目标文字", selected(mark, layout(paragraphs, title = "长标题", showTitle = false)))
        val title = NativeHighlightPositionMap.Builder("长标题\n目标文字\n")
            .append("长\n", 0, 1).append("标\n", 1, 2).append("题\n", 2, 4)
            .append("　　", 4, 4).append("目标文字\n", 4, 9).snapshot()
        assertEquals("目标文字", selected(mark, title))
    }

    @Test
    fun legacyDuplicateQuotePreservesVerifiedOriginalOccurrence() {
        val map = layout(listOf("重复文字", "中间", "重复文字"))
        val second = map.displayText.lastIndexOf("重复文字")
        val mark = legacy(second, second + 4, "重复文字")
        assertEquals(range(second, second + 4), resolve(mark, map))
    }

    @Test
    fun ambiguousLegacyQuoteDoesNotGuessTheFirstOccurrence() {
        val map = layout(listOf("重复文字", "中间", "重复文字"))
        val mark = legacy(1, 3, "重复文字")
        assertEquals(range(1, 3), resolve(mark, map))
    }

    @Test
    fun editedLegacyQuoteRetainsOldCoordinatesWithoutChangingBookmark() {
        val map = layout(listOf("原始文字"))
        val mark = legacy(2, 5, "修改过的摘录")
        val copy = mark.copy()
        assertEquals(range(2, 5), resolve(mark, map))
        assertEquals(copy, mark)
    }

    @Test
    fun spacesInsideTheLineAreSignificant() {
        val map = layout(listOf("ab", "a b"), indent = "")
        val mark = legacy(0, 1, "a b")
        assertEquals("a b", selected(mark, map))
        assertEquals(map.displayText.indexOf("a b"), resolve(mark, map)!!.start)
    }

    @Test
    fun lineBreaksHavePriorityOverFlattenedMatches() {
        val map = layout(listOf("甲乙", "甲", "乙"), indent = "　")
        val mark = legacy(0, 1, "甲\r\n　乙")
        assertEquals("甲\n　乙", selected(mark, map))
    }

    @Test
    fun oldQuoteWithoutBreakAfterCommentStillRecoversBothParagraphs() {
        val map = layout(listOf("前段\uFFFC", "后段\uFFFC"))
        val mark = legacy(0, 1, "前段后段")
        assertEquals("前段꧁\n　　后段", selected(mark, map))
    }

    @Test
    fun flattenedDuplicateQuoteIsNotMoved() {
        val map = layout(listOf("甲\uFFFC", "乙", "甲\uFFFC", "乙"))
        val mark = legacy(0, 1, "甲乙")
        assertEquals(range(0, 1), resolve(mark, map))
    }

    @Test
    fun emojiAndCombiningCharactersKeepUtf16Offsets() {
        val map = layout(listOf("前段", "中😀e\u0301文后段"), indent = "　")
        val mark = legacy(0, 1, "😀e\u0301文")
        val start = map.displayText.indexOf("😀")
        assertEquals(range(start, start + 5), resolve(mark, map))
        assertEquals("😀e\u0301文", selected(mark, map))
    }

    @Test
    fun legacyCrossChapterMatchesWholeSuffixAndPrefixAcrossParagraphs() {
        val startMap = layout(listOf("未选", "章尾一", "章尾二"), indent = "　")
        val endMap = layout(listOf("下章一", "下章二", "未选"), indent = "　")
        val mark = legacy(0, 1, "章尾一\n　　章尾二\n标题\n　　下章一\n　　下章二")
            .apply { chapterIndex = 2; endChapterIndex = 3 }
        val start = NativeTextHighlightResolver.resolve(mark, 2, startMap)!!
        assertEquals(startMap.displayText.indexOf("章尾一"), start.start)
        assertEquals(startMap.displayText.length, start.endExclusive)
        val end = NativeTextHighlightResolver.resolve(mark, 3, endMap)!!
        assertEquals(0, end.start)
        assertEquals(endMap.displayText.indexOf("下章二") + 3, end.endExclusive)
    }

    @Test
    fun crossChapterMiddleIsFullyCoveredAndOutsideChaptersAreIgnored() {
        val map = layout(listOf("整章覆盖"))
        val mark = canonical(2, 3).apply { chapterIndex = 1; endChapterIndex = 3 }
        assertEquals(range(0, map.displayText.length), NativeTextHighlightResolver.resolve(mark, 2, map))
        assertNull(NativeTextHighlightResolver.resolve(mark, 0, map))
        assertNull(NativeTextHighlightResolver.resolve(mark, 4, map))
    }

    @Test
    fun canonicalSelectionEndingAtNextChapterStartDoesNotColorThatChapter() {
        val mark = canonical(1, 0).apply { chapterIndex = 1; endChapterIndex = 2 }
        assertNull(NativeTextHighlightResolver.resolve(mark, 2, layout(listOf("不可覆盖"))))
    }

    @Test
    fun validLegacyAnchorNotPublishedYetDoesNotUseObsoleteVisibleCoordinates() {
        val builder = NativeHighlightPositionMap.Builder("前段\n目标文字\n")
            .append("前段\n", 0, 3)
        val mark = legacy(0, 2, "目标文字")
        assertNull(resolve(mark, builder.snapshot()))
        val map = builder.append("　", 3, 3).append("目标文字\n", 3, 8).snapshot()
        assertEquals("目标文字", selected(mark, map))
    }

    @Test
    fun hiddenCanonicalSelectionIsNotPaintedOverBody() {
        val map = layout(listOf("正文"), showTitle = false)
        assertNull(resolve(canonical(0, 2), map))
    }

    @Test
    fun partiallyPublishedCanonicalRangeDoesNotPublishItsNoteEarly() {
        val builder = NativeHighlightPositionMap.Builder("开头\n末尾\n")
            .append("开头\n", 0, 3)
        val mark = canonical(0, 5)
        assertEquals(range(0, 3), resolve(mark, builder.snapshot()))
        assertNull(NativeTextHighlightResolver.resolveEnd(mark, 0, builder.snapshot()))
        val complete = builder.append("　", 3, 3).append("末尾\n", 3, 6).snapshot()
        assertEquals(6, NativeTextHighlightResolver.resolveEnd(mark, 0, complete))
    }

    @Test
    fun recoveredLegacyRangeDoesNotPublishItsNoteEarly() {
        val builder = NativeHighlightPositionMap.Builder("标题\n开头\n末尾\n")
            .append("标题\n", 0, 3).append("　", 3, 3).append("开头\n", 3, 6)
        val mark = legacy(0, 1, "开头\n末尾")
        assertEquals(range(4, 7), resolve(mark, builder.snapshot()))
        assertNull(NativeTextHighlightResolver.resolveEnd(mark, 0, builder.snapshot()))
        val complete = builder.append("　", 6, 6).append("末尾\n", 6, 9).snapshot()
        assertEquals(10, NativeTextHighlightResolver.resolveEnd(mark, 0, complete))
    }

    @Test
    fun unmatchedLegacyQuoteKeepsRealUnclippedNotePosition() {
        val builder = NativeHighlightPositionMap.Builder("前段\n后段\n").append("前段\n", 0, 3)
        val mark = legacy(1, 5, "用户修改了摘录")
        assertEquals(range(1, 3), resolve(mark, builder.snapshot()))
        assertNull(NativeTextHighlightResolver.resolveEnd(mark, 0, builder.snapshot()))
        val complete = builder.append("后段\n", 3, 6).snapshot()
        assertEquals(5, NativeTextHighlightResolver.resolveEnd(mark, 0, complete))
        mark.chapterIndex = 0
        mark.endChapterIndex = 1
        assertNull(NativeTextHighlightResolver.resolveEnd(mark, 0, complete))
    }

    private fun layout(
        paragraphs: List<String>,
        indent: String = "　　",
        title: String = "标题",
        showTitle: Boolean = true,
    ): NativeHighlightPositionMap {
        val canonical = "$title\n" + paragraphs.joinToString("\n", postfix = "\n")
        val builder = NativeHighlightPositionMap.Builder(canonical)
        var offset = title.length + 1
        if (showTitle) builder.append("$title\n", 0, offset)
        for (paragraph in paragraphs) {
            builder.append(indent, offset, offset)
            val displayed = paragraph.replace('\uFFFC', '\uA9C1') + "\n"
            builder.append(displayed, offset, offset + displayed.length)
            offset += displayed.length
        }
        return builder.snapshot()
    }

    private fun legacy(start: Int, end: Int, quote: String) = Bookmark(
        chapterPos = start,
        endChapterPos = end,
        bookText = quote,
        bookmarkType = Bookmark.TYPE_TEXT_HIGHLIGHT,
    )

    private fun canonical(start: Int, end: Int, quote: String = "") = legacy(start, end, quote)
        .apply { bookmarkType = Bookmark.TYPE_TEXT_HIGHLIGHT_CANONICAL }

    private fun resolve(mark: Bookmark, map: NativeHighlightPositionMap) =
        NativeTextHighlightResolver.resolve(mark, mark.chapterIndex, map)

    private fun selected(mark: Bookmark, map: NativeHighlightPositionMap): String {
        val range = resolve(mark, map)!!
        return map.displayText.substring(range.start, range.endExclusive)
    }

    private fun range(start: Int, end: Int) = NativeHighlightPositionMap.Range(start, end)
}
