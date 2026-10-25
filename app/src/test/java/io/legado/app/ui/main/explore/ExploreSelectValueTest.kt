package io.legado.app.ui.main.explore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.help.CacheManager
import io.legado.app.utils.InfoMap
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ExploreSelectValueTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun changingModeReplacesAnUnavailableProviderWithTheSourceDefault() {
        val infoMap = infoMap("changed")
        infoMap["源站"] = "懒人听书"
        var options by mutableStateOf(listOf("聚合搜索", "懒人听书"))
        var default by mutableStateOf("懒人听书")
        var selected = ""
        composeRule.setContent {
            selected = rememberExploreSelectValue(
                ExploreKind(title = "源站", default = default), options, infoMap
            ).value
        }
        assertEquals("懒人听书", selected)

        composeRule.runOnIdle {
            options = listOf("聚合搜索", "番茄小说", "QQ阅读")
            default = "聚合搜索"
        }
        composeRule.waitForIdle()
        assertEquals("聚合搜索", selected)
        assertEquals("聚合搜索", infoMap["源站"])
    }

    @Test
    fun refreshingOptionsPreservesASelectionThatIsStillAvailable() {
        val infoMap = infoMap("retained")
        infoMap["源站"] = "QQ阅读"
        var options by mutableStateOf(listOf("聚合搜索", "QQ阅读"))
        var selected = ""
        composeRule.setContent {
            selected = rememberExploreSelectValue(
                ExploreKind(title = "源站", default = "聚合搜索"), options, infoMap
            ).value
        }

        composeRule.runOnIdle { options = listOf("聚合搜索", "番茄小说", "QQ阅读") }
        composeRule.waitForIdle()
        assertEquals("QQ阅读", selected)
        assertEquals("QQ阅读", infoMap["源站"])
    }

    @Test
    fun unavailableSelectionAndDefaultUseTheFirstAvailableOption() {
        val infoMap = infoMap("fallback")
        infoMap["源站"] = "懒人听书"
        var selected = ""
        composeRule.setContent {
            selected = rememberExploreSelectValue(
                ExploreKind(title = "源站", default = "听书"),
                listOf("聚合搜索", "QQ阅读"), infoMap
            ).value
        }
        assertEquals("聚合搜索", selected)
        assertEquals("聚合搜索", infoMap["源站"])
    }

    private fun infoMap(name: String): InfoMap {
        val url = "explore-select-value-$name"
        CacheManager.putMemory("infoMap_$url", "{}")
        return InfoMap(url)
    }
}
