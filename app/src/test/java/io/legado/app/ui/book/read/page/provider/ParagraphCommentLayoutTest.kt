package io.legado.app.ui.book.read.page.provider

import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookContent
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.ParagraphCommentBubbleSettings
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Base64

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ParagraphCommentLayoutTest {
    private val book = Book(bookUrl = "test:paragraph-icons", name = "段评排版")
    private val click = "showQQCmt('book','chapter','paragraph','123456')"

    private fun svg(label: String) =
        """<svg xmlns="http://www.w3.org/2000/svg" width="54" height="39"><text x="2" y="20">$label</text></svg>"""

    private fun source(label: String, action: String): String {
        val data = Base64.getEncoder().encodeToString(svg(label).toByteArray())
        val src = "data:image/svg+xml;base64,$data," + Gson().toJson(mapOf("style" to "TEXT", "click" to action))
        BookHelp.writeImage(book, src, svg(label).toByteArray())
        return src
    }

    private fun layout(paragraph: String, enabled: Boolean, size: Int = 100, titleImage: String? = null): TextChapter {
        val library = ParagraphCommentBubbleSettings.load()
        ParagraphCommentBubbleSettings.save(library, library.copy(enabled = enabled, appearance = library.appearance.copy(size = size)))
        ChapterProvider.upViewSize(900, 1800)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val chapter = BookChapter(bookUrl = book.bookUrl, title = "标题", imgUrl = titleImage)
        return try {
            ChapterProvider.getTextChapterAsync(scope, book, chapter, chapter.title, BookContent(false, listOf(paragraph), null), 1)
                .also { runBlocking { withTimeout(15000) { it.layoutChannel.toList() } } }
        } finally {
            scope.cancel()
        }
    }

    private fun images(chapter: TextChapter) =
        chapter.pages.flatMap { page -> page.lines.flatMap { it.columns.filterIsInstance<ImageColumn>() } }

    @Test
    fun imageTextModeKeepsClickAndAnActualMarkerCharacterDoesNotConsumeTheNextImage() {
        book.setImageStyle(Book.imgStyleText)
        val src = source("28", click)
        val content = "正文😀꧁甲<img src=\"$src\">乙"
        val original = layout(content, false)
        val replaced = layout(content, true)
        assertEquals(original.highlightPositionMap.canonicalText, replaced.highlightPositionMap.canonicalText)
        for (chapter in listOf(original, replaced)) {
            val image = images(chapter).single()
            assertEquals(src, image.src)
            assertEquals(click, image.click)
            val columns = image.textLine.columns
            val preceding = columns.takeWhile { it !== image }.filterIsInstance<TextColumn>().joinToString("") { it.charData }
            assertTrue(preceding, preceding.endsWith("😀꧁甲"))
        }
        assertNull(images(original).single().paragraphCommentIcon)
        assertEquals("28", images(replaced).single().paragraphCommentIcon?.text)
    }

    @Test
    fun htmlBubbleAtEndOfLineUsesTheFullSpanWidthAndOriginalClick() {
        val src = source("19", "fqShowReviews('book','chapter',3,'')")
        val html = "<usehtml>段末<img src=\"${TextUtils.htmlEncode(src)}\"></usehtml>"
        for (size in listOf(70, 150)) {
            val image = images(layout(html, true, size)).single()
            val expected = ChapterProvider.contentPaint.measureText(ChapterProvider.srcReplaceStr) * 1.5556f * size / 100
            assertEquals(expected, image.end - image.start, 1f)
            assertEquals("19", image.paragraphCommentIcon?.text)
            assertEquals("fqShowReviews('book','chapter',3,'')", image.click)
        }
    }

    @Test
    fun generatedCommentUsesTheActualSourceImageAndKeepsNeighbouringImages() {
        val normal = source("43", "openPicture()")
        val options = mapOf("style" to "TEXT", "type" to "lofter", "js" to "getSvg(0)", "click" to "toReview(0,1,2,'p')")
        val generated = "data:image/svg+xml;base64,," + Gson().toJson(options)
        BookHelp.writeImage(book, generated, svg("无").toByteArray())
        val title = source("99+", click)
        val inline = images(layout("甲<img src=\"$normal\">乙<img src=\"$generated\">丙", true, titleImage = title))
        assertEquals(listOf(title, normal, generated), inline.map { it.src })
        assertEquals(listOf("99+", null, "无"), inline.map { it.paragraphCommentIcon?.text })
        assertEquals(listOf(click, "openPicture()", options["click"]), inline.map { it.click })
    }

    @Test
    fun explicitlyMarkedPrivateActionsUseTheSourceImageAndKeepUnlabelledPicturesOriginal() {
        val options = mapOf("type" to "paragraph", "style" to "TEXT", "click" to "openPrivatePanel()")
        val marked = "https://fixture.invalid/count.svg," + Gson().toJson(options)
        val unlabelled = "https://fixture.invalid/art.svg," + Gson().toJson(options)
        BookHelp.writeImage(book, marked, svg("1.2万+").toByteArray())
        BookHelp.writeImage(book, unlabelled, svg("查看").toByteArray())
        val columns = images(layout("甲<img src=\"$marked\">乙<img src=\"$unlabelled\">", true))
        assertEquals(listOf("1.2万+", null), columns.map { it.paragraphCommentIcon?.text })
        assertEquals(listOf(marked, unlabelled), columns.map { it.src })
        assertTrue(columns.all { it.click == "openPrivatePanel()" })
    }

    @Test
    fun visibleReplacementDoesNotWaitForTheOriginalButUnreadableTemplatesUseIt() {
        val raw = svg("37")
        val src = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(raw.toByteArray()) +
            "," + Gson().toJson(mapOf("style" to "TEXT", "click" to click))
        val paragraph = "甲<img src=\"$src\">乙"
        val replacement = images(layout(paragraph, true)).single()
        assertEquals("37", replacement.paragraphCommentIcon?.text)
        assertFalse(BookHelp.getImage(book, src).isFile)

        BookHelp.writeImage(book, src, raw.toByteArray())
        val library = ParagraphCommentBubbleSettings.load()
        val broken = ParagraphCommentBubbleSettings.newBubble("broken.svg", "<svg")
        ParagraphCommentBubbleSettings.save(library, library.put(broken, select = true))
        val fallback = images(layout(paragraph, true)).single()
        assertNull(fallback.paragraphCommentIcon)
        assertEquals(src, fallback.src)
        assertEquals(click, fallback.click)
        assertTrue(BookHelp.getImage(book, src).isFile)
    }
}
