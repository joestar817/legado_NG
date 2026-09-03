package io.legado.app.model.jsSource

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.BookSource
import io.legado.app.model.analyzeRule.AnalyzeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsSourceQuickJsBindingInstrumentedTest {

    @Test
    fun quickJsSandboxExistsOnlyOnTheSingleFileJsJavaHost() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://example.com/js-runtime-binding",
            bookSourceName = "JS运行时绑定测试",
            mainJs = """
                function probe() {
                    return typeof java.getQuickJsSandbox + "|" + typeof isolatedJs;
                }
            """.trimIndent(),
        )

        val jsSourceResult = withContext(Dispatchers.IO) {
            JsSourceEngine(source).callFunction("probe", emptyList())
        }
        val jsonSource = BookSource(
            bookSourceUrl = "https://example.com/json-runtime-binding",
            bookSourceName = "JSON运行时绑定测试",
        )
        val ordinaryRhinoResult = AnalyzeRule(source = jsonSource)
            .evalJS("typeof java.getQuickJsSandbox + '|' + typeof isolatedJs")

        assertEquals("function|undefined", jsSourceResult)
        assertEquals("undefined|undefined", ordinaryRhinoResult)
    }

    @Test
    fun legacyScriptMayKeepItsOwnIsolatedJsGlobal() = runBlocking {
        val source = BookSource(
            bookSourceUrl = "https://example.com/js-global-compat",
            bookSourceName = "JS全局名兼容测试",
            mainJs = """
                var isolatedJs = {
                    evalString: function(value) { return "legacy-" + value; }
                };
                function probe() { return isolatedJs.evalString("value"); }
            """.trimIndent(),
        )

        val result = withContext(Dispatchers.IO) {
            JsSourceEngine(source).callFunction("probe", emptyList())
        }

        assertEquals("legacy-value", result)
    }

}
