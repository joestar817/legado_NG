package io.legado.app.model.localBook

import me.ag2s.epublib.epub.NCXDocumentV2
import me.ag2s.epublib.epub.NCXDocumentV3
import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 回归验证：NCX 目录节点缺少 navLabel/a/span 标签时，
 * 解析不应抛出 AssertionError 或中断整本书导入，而是返回空章节名。
 */
class NcxReadNavLabelTest {

    private fun parseElement(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        return factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
            .documentElement
    }

    private fun invokeReadNavLabel(clazz: Class<*>, element: Element): String {
        val method = clazz.getDeclaredMethod("readNavLabel", Element::class.java)
        method.isAccessible = true
        return method.invoke(null, element) as String
    }

    @Test
    fun v2MissingNavLabelReturnsEmpty() {
        val element = parseElement(
            "<navPoint xmlns=\"http://www.daisy.org/z3986/2005/ncx/\">" +
                "<content src=\"c1.xhtml\"/></navPoint>"
        )
        assertEquals("", invokeReadNavLabel(NCXDocumentV2::class.java, element))
    }

    @Test
    fun v2NavLabelWithoutTextReturnsEmpty() {
        val element = parseElement(
            "<navPoint xmlns=\"http://www.daisy.org/z3986/2005/ncx/\">" +
                "<navLabel></navLabel><content src=\"c1.xhtml\"/></navPoint>"
        )
        assertEquals("", invokeReadNavLabel(NCXDocumentV2::class.java, element))
    }

    @Test
    fun v2NavLabelWithTextReturnsText() {
        val element = parseElement(
            "<navPoint xmlns=\"http://www.daisy.org/z3986/2005/ncx/\">" +
                "<navLabel><text>第一章</text></navLabel><content src=\"c1.xhtml\"/></navPoint>"
        )
        assertEquals("第一章", invokeReadNavLabel(NCXDocumentV2::class.java, element))
    }

    @Test
    fun v3MissingAnchorAndSpanReturnsEmpty() {
        val element = parseElement("<li><p>分组节点</p></li>")
        assertEquals("", invokeReadNavLabel(NCXDocumentV3::class.java, element))
    }

    @Test
    fun v3AnchorLabelReturnsText() {
        val element = parseElement("<li><a href=\"c1.xhtml\">第一章</a></li>")
        assertEquals("第一章", invokeReadNavLabel(NCXDocumentV3::class.java, element))
    }

    @Test
    fun v3BlankAnchorFallsBackToSpan() {
        val element = parseElement("<li><a href=\"c1.xhtml\"></a><span>第一章</span></li>")
        assertEquals("第一章", invokeReadNavLabel(NCXDocumentV3::class.java, element))
    }
}
