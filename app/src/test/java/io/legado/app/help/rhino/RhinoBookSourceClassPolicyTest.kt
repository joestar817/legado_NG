package io.legado.app.help.rhino

import com.script.rhino.RhinoClassShutter
import com.script.rhino.RhinoScriptEngine
import com.script.rhino.RhinoWrapFactory
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.model.SharedJsScope
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class UnlistedAppClassFixture {
    fun value(): String = "ok"
}

class RhinoBookSourceClassPolicyTest {

    private val fixtureClassName = UnlistedAppClassFixture::class.java.name
    private val fixtureScriptName =
        "Packages.io.legado.app.help.rhino.UnlistedAppClassFixture"

    @Test
    fun bookSourceDirectImportsUseDefaultDenyAllowlist() {
        val source = BookSource(
            bookSourceUrl = "https://example.com#book",
            bookSourceName = "测试书源"
        )

        val blocked = source.evalJS("String($fixtureScriptName)")
        val taijiStyle = source.evalJS(
            """
                var url = "https://example.com/taiji";
                var src = "taiji-body";
                Packages.io.legado.app.help.http.StrResponse(url, src).body();
            """.trimIndent()
        )
        val shuqiStyle = source.evalJS(
            """
                var result = Packages.io.legado.app.help.http.StrResponse(
                    "https://example.com/shuqi",
                    "shuqi-body"
                );
                Packages.io.legado.app.help.http.StrResponse(
                    result.url(),
                    String(result.body()).toUpperCase()
                ).body();
            """.trimIndent()
        )

        assertTrue(blocked.toString().startsWith("[JavaPackage "))
        assertEquals("taiji-body", taijiStyle)
        assertEquals("SHUQI-BODY", shuqiStyle)
    }

    @Test
    fun analyzeRuleAndAnalyzeUrlActivateBookSourcePolicy() {
        val source = BookSource(
            bookSourceUrl = "https://example.com#rules",
            bookSourceName = "规则书源"
        )

        val ruleResult = AnalyzeRule(source = source)
            .evalJS("String($fixtureScriptName)")
        val urlResult = AnalyzeUrl(
            mUrl = "https://example.com/",
            source = source,
            hasLoginHeader = false,
            headerMapF = emptyMap()
        ).evalJS("String($fixtureScriptName)")

        assertTrue(ruleResult.toString().startsWith("[JavaPackage "))
        assertTrue(urlResult.toString().startsWith("[JavaPackage "))
    }

    @Test
    fun hostObjectWrappingDoesNotOpenItsClassToPackages() {
        val source = BookSource(
            bookSourceUrl = "https://example.com#host",
            bookSourceName = "宿主书源"
        )

        val result = AnalyzeRule(source = source).evalJS(
            """
                [
                    typeof java.getString,
                    source.bookSourceName,
                    String(Packages.io.legado.app.model.analyzeRule.AnalyzeRule)
                ].join("|")
            """.trimIndent()
        ).toString().split("|", limit = 3)

        assertEquals("function", result[0])
        assertEquals("宿主书源", result[1])
        assertTrue(result[2], result[2].startsWith("[JavaPackage "))
    }

    @Test
    fun sharedJsScopeIsSeparatedFromUnrestrictedConsumers() {
        val jsLib = "var fixtureClass = String($fixtureScriptName);"
        val rssSource = RssSource(
            sourceUrl = "https://example.com/rss",
            sourceName = "RSS",
            jsLib = jsLib
        )
        val bookSource = BookSource(
            bookSourceUrl = "https://example.com/book",
            bookSourceName = "书源",
            jsLib = jsLib
        )

        try {
            val rssResult = rssSource.evalJS("fixtureClass")
            val bookResult = bookSource.evalJS("fixtureClass")

            assertFalse(rssResult.toString().startsWith("[JavaPackage "))
            assertTrue(bookResult.toString().startsWith("[JavaPackage "))
        } finally {
            SharedJsScope.remove(jsLib)
        }
    }

    @Test
    fun sharedJsScopeIsSeparatedBetweenBookSourcesButKeptForSameUrl() {
        val jsLib = """
            var codexScopeOwner = "";
            function claimCodexScope(owner) {
                if (codexScopeOwner === "") codexScopeOwner = String(owner);
                return codexScopeOwner;
            }
        """.trimIndent()
        val sourceA = BookSource(
            bookSourceUrl = "https://example.com/source#a",
            bookSourceName = "A",
            jsLib = jsLib
        )
        val sourceAAfterUpdate = sourceA.copy(bookSourceName = "A更新后")
        val sourceB = sourceA.copy(
            bookSourceUrl = "https://example.com/source#b",
            bookSourceName = "B"
        )

        try {
            assertEquals("A", sourceA.evalJS("claimCodexScope('A')"))
            assertEquals("A", sourceAAfterUpdate.evalJS("claimCodexScope('A更新后')"))
            assertEquals("B", sourceB.evalJS("claimCodexScope('B')"))
        } finally {
            SharedJsScope.remove(jsLib)
        }
    }

    @Test
    fun ttsAndRssRemainOutsideBookSourcePolicy() {
        val bookSource = BookSource(
            bookSourceUrl = "https://example.com/book",
            bookSourceName = "书源"
        )
        val rssSource = RssSource(
            sourceUrl = "https://example.com/rss",
            sourceName = "RSS"
        )
        val ttsEngine = TtsEngineSetting(
            id = "test-script",
            name = "测试脚本引擎",
            type = TtsEngineType.SCRIPT
        )

        bookSource.evalJS("String($fixtureScriptName)")
        val rssResult = rssSource.evalJS("new $fixtureScriptName().value()")
        val ttsResult = ttsEngine.evalJS("new $fixtureScriptName().value()")

        assertEquals("ok", rssResult)
        assertEquals("ok", ttsResult)
    }

    @Test
    fun policyStateIsClearedAfterScriptFailure() {
        val source = BookSource(
            bookSourceUrl = "https://example.com#error",
            bookSourceName = "异常书源"
        )

        runCatching {
            source.evalJS("throw new Error('expected')")
        }

        assertTrue(RhinoClassShutter.visibleToScripts(fixtureClassName))
    }

    @Test
    fun authorityBearingClassesAreDeniedOnlyInsideBookSourcePolicy() {
        val blockedClasses = listOf(
            "io.legado.app.help.source.SourceHelp",
            "io.legado.app.api.controller.BookSourceController",
            "io.legado.app.api.controller.BookController",
            "io.legado.app.help.ai.AiProviderStore",
            "io.legado.app.help.AppWebDav",
            "io.legado.app.help.tts.TtsEngineStore",
            "io.legado.app.help.http.NetworkLog",
            "io.legado.app.help.config.AppConfig",
            "io.legado.app.web.mcp.McpServer"
        )

        RhinoClassShutter.withBookSourceClassPolicy(true) {
            blockedClasses.forEach { className ->
                assertFalse(className, RhinoClassShutter.visibleToScripts(className))
            }
            assertTrue(
                RhinoClassShutter.visibleToScripts(
                    "io.legado.app.help.http.StrResponse"
                )
            )
            assertTrue(RhinoClassShutter.visibleToScripts("java.lang.String"))
        }

        assertTrue(RhinoClassShutter.visibleToScripts(fixtureClassName))
    }

    @Test
    fun bookSourceCannotBypassCookieFacadeThroughAndroidSingleton() {
        val blockedClasses = listOf(
            "android.webkit.CookieManager",
            "android.webkit.CookieSyncManager"
        )

        RhinoClassShutter.withBookSourceClassPolicy(true) {
            blockedClasses.forEach { className ->
                assertFalse(className, RhinoClassShutter.visibleToScripts(className))
            }
        }

        blockedClasses.forEach { className ->
            assertTrue(className, RhinoClassShutter.visibleToScripts(className))
        }

        val source = BookSource(
            bookSourceUrl = "https://example.com/cookie",
            bookSourceName = "Cookie测试"
        )
        blockedClasses.forEach { className ->
            val result = source.evalJS("String(Packages.$className)").toString()
            assertTrue(result, result.startsWith("[JavaPackage "))
        }
    }

    @Test
    fun htmlUnitMigrationKeepsEngineAndDangerousClassesBlocked() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/htmlunit-policy",
            bookSourceName = "HtmlUnit策略测试",
        )
        val blockedClasses = listOf(
            "org.htmlunit.corejs.javascript.Context",
            "org.htmlunit.corejs.javascript.DefiningClassLoader",
            "java.lang.Runtime",
            "java.io.File",
        )

        blockedClasses.forEach { className ->
            val result = source.evalJS("String(Packages.$className)").toString()
            assertTrue(result, result.startsWith("[JavaPackage "))
        }
        assertEquals(
            "ok",
            source.evalJS("Packages.java.lang.Thread.sleep(1); 'ok'")
        )
    }

    @Test
    fun diagnosticSourceLabelSupportsNestingAndCleanup() {
        assertNull(RhinoClassShutter.currentBookSourceLabel())

        RhinoClassShutter.withBookSourceClassPolicy(true, "外层书源") {
            assertEquals("外层书源", RhinoClassShutter.currentBookSourceLabel())
            RhinoClassShutter.withBookSourceClassPolicy(true) {
                assertEquals("外层书源", RhinoClassShutter.currentBookSourceLabel())
            }
            RhinoClassShutter.withBookSourceClassPolicy(true, "内层书源") {
                assertEquals("内层书源", RhinoClassShutter.currentBookSourceLabel())
            }
            assertEquals("外层书源", RhinoClassShutter.currentBookSourceLabel())
        }

        assertNull(RhinoClassShutter.currentBookSourceLabel())
    }

    @Test
    fun mainJsIsReadableButCannotBeMutatedFromBookSourceScript() {
        val source = BookSource(
            bookSourceUrl = "https://example.com/js-guard",
            bookSourceName = "JS 守卫",
            mainJs = "original",
        )

        assertEquals("original", source.evalJS("String(source.mainJs)"))
        assertEquals("undefined", source.evalJS("typeof source.setMainJs"))
        source.evalJS("source.mainJs = 'changed'")
        assertEquals("original", source.mainJs)
    }

    companion object {
        @JvmStatic
        @BeforeClass
        fun registerSourceWrappers() {
            RhinoWrapFactory.register(BookSource::class.java, NativeBaseSource.factory)
            RhinoWrapFactory.register(RssSource::class.java, NativeBaseSource.factory)
            RhinoScriptEngine
        }
    }
}
