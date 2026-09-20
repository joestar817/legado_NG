package io.legado.app.ui.book.read

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentEditPositionTest {
    @Test fun selectionStartsAfterIndentationAndEarlierText() {
        val content = "　　首段\n　　陆鸣则是绕去后院，找到了静修中的陆瑾。"
        val target = content.indexOf("找到了")
        assertEquals(target, contentEditSelectionOffset(content, 1, 12, "找到了静修中的陆瑾。"))
    }

    @Test fun repeatedTextWithinOneParagraphUsesSelectionOffset() {
        val content = "　　相同文字\n　　相同文字，过渡，相同文字。"
        val start = content.indexOf('\n') + 1
        val secondMatch = content.lastIndexOf("相同文字")
        assertEquals(secondMatch, contentEditSelectionOffset(content, 1, secondMatch - start, "相同文字"))
    }

    @Test fun wrappedAndCrossParagraphSelectionUsesItsFirstCharacter() {
        val content = "　　😀前文，选中的文字\n　　下一段"
        val target = content.indexOf("选中的")
        assertEquals(target, contentEditSelectionOffset(content, 0, target, "选中的文字\n　　下一段"))
    }

    @Test fun missingTextStaysInsideItsParagraph() {
        assertEquals(5, contentEditSelectionOffset("　　第一段\n下一段", 0, 999, "不存在"))
        assertEquals(0, contentEditSelectionOffset("", 2, 99, "不存在"))
    }

    @Test fun repeatedParagraphsUseTheirPositionRatherThanFirstTextMatch() {
        val content = "　　相同段落\n　　过渡段落\n　　相同段落"
        assertEquals(content.lastIndexOf("　　相同段落"), contentEditParagraphOffset(content, 2))
    }

    @Test fun imagesAndPageBreaksRetainTheirSourceParagraphSlots() {
        val content = "　　前文\n　　<img src='cover.jpg'>\n　　[newpage]\n　　目标段落"
        assertEquals(content.indexOf("　　目标段落"), contentEditParagraphOffset(content, 3))
    }

    @Test fun unicodeAndIndentationUseEditorUtf16Offsets() {
        val content = "　　😀第一段\n　　第二段"
        assertEquals(content.indexOf("　　第二段"), contentEditParagraphOffset(content, 1))
    }

    @Test fun firstEmptyAndOutOfRangePositionsStayWithinDocument() {
        assertEquals(0, contentEditParagraphOffset("首段", 0))
        assertEquals(0, contentEditParagraphOffset("首段", -1))
        assertEquals(0, contentEditParagraphOffset("", 3))
        assertEquals(2, contentEditParagraphOffset("首段", 3))
    }
}
