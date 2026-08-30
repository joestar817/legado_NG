package io.legado.app.model.jsSource

import io.legado.app.exception.NoStackTraceException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsSourceConfigTest {

    private val validScript = """
        var config = {
            bookSourceUrl: "https://example.com",
            bookSourceName: "示例 JS 源",
            exploreUrl: [{title: "分类", url: "/list"}],
            lastUpdateTime: 0
        };
        function search(key, page) { return []; }
        function explore(url, page) { return []; }
        function getChapters(book) { return []; }
        function getContent(chapter, book, nextChapterUrl) { return "正文"; }
    """.trimIndent()

    @Test
    fun extractsConfigAndKeepsFullScript() {
        val source = JsSourceConfig.extract(validScript)
        assertEquals("https://example.com", source.bookSourceUrl)
        assertEquals("示例 JS 源", source.bookSourceName)
        assertEquals(validScript, source.mainJs)
        assertTrue(source.exploreUrl!!.contains("分类"))
        assertNull(source.ruleSearch)
    }

    @Test
    fun extractsReferenceStyleSourceUsingConstAndLookbehind() {
        val script = """
            const config = {
                bookSourceUrl: "banxia-reference",
                bookSourceName: "半夏兼容测试"
            };
            const compact = "中 文".replace(
                /(?<=[^\x00-\x7F])[t\x20](?=[^\x00-\x7F])/, ""
            );
            const punctuation = "中?文".replace(
                /(?<=[\u4e00-\u9fa5])\?(?=[\u4e00-\u9fa5])/, ""
            );
            if (compact !== "中文" || punctuation !== "中文") {
                throw new Error("lookbehind mismatch");
            }
            function search(key, page) { return []; }
            function getChapters(book) { return []; }
            function getContent(chapter, book) { return "正文"; }
        """.trimIndent()

        val source = JsSourceConfig.extract(script)

        assertEquals("banxia-reference", source.bookSourceUrl)
        assertEquals("半夏兼容测试", source.bookSourceName)
        assertEquals(script, source.mainJs)
    }

    @Test
    fun validatesFunctionPairsAndRequiredFunctions() {
        val missingExplore = validScript.replace(
            "function explore(url, page) { return []; }",
            ""
        )
        assertThrows(NoStackTraceException::class.java) {
            JsSourceConfig.extract(missingExplore)
        }

        val dynamicLogin = validScript + "\n" + """
            function loginUi(state) { return {rows:[{name:"登录",type:"button",action:"go"}]}; }
            function loginAction(action, state, form) { return {close:true}; }
        """.trimIndent()
        assertEquals("""{"version":2}""", JsSourceConfig.extract(dynamicLogin).loginUi)
    }

    @Test
    fun stampsManagedUpdateTimeWithoutTouchingOtherNumbers() {
        val stamped = JsSourceConfig.stampLastUpdateTime(validScript, 1234L)
        assertTrue(stamped!!.contains("lastUpdateTime: 1234"))
        assertTrue(stamped.contains("page)"))
    }
}
