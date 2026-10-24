package io.legado.app.ui.main.explore

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.CacheManager
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.InfoMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ExploreSourceRefreshCallbackTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun refreshCallbackSurvivesGarbageCollection() {
        val infoMap = infoMap("gc")
        var refreshes = 0
        lateinit var bridge: SourceLoginJsExtensions
        composeRule.setContent {
            bridge = rememberExploreSourceJsExtensions(null, infoMap) { refreshes++ }
        }

        composeRule.runOnIdle {
            repeat(3) {
                System.gc()
                System.runFinalization()
            }
            bridge.refreshExplore()
        }
        composeRule.waitForIdle()
        assertEquals(1, refreshes)
    }

    @Test
    fun rememberedBridgeUsesTheCurrentRefreshCallback() {
        val infoMap = infoMap("updated")
        var firstRefreshes = 0
        var currentRefreshes = 0
        var onRefresh by mutableStateOf<() -> Unit>({ firstRefreshes++ })
        lateinit var bridge: SourceLoginJsExtensions
        composeRule.setContent {
            bridge = rememberExploreSourceJsExtensions(null, infoMap, onRefresh)
        }
        val initialBridge = bridge

        composeRule.runOnIdle { onRefresh = { currentRefreshes++ } }
        composeRule.waitForIdle()
        assertSame(initialBridge, bridge)
        composeRule.runOnIdle { bridge.refreshExplore() }
        composeRule.waitForIdle()
        assertEquals(0, firstRefreshes)
        assertEquals(1, currentRefreshes)
    }

    @Test
    fun callbackFromThePreviousSourceCannotRefreshTheNewSource() {
        var currentInfoMap by mutableStateOf(infoMap("first"))
        val nextInfoMap = infoMap("second")
        val refreshedSources = arrayListOf<String>()
        lateinit var bridge: SourceLoginJsExtensions
        composeRule.setContent {
            val current = currentInfoMap
            bridge = rememberExploreSourceJsExtensions(null, current) {
                refreshedSources += current.sourceUrl
            }
        }
        val previousBridge = bridge

        composeRule.runOnIdle { currentInfoMap = nextInfoMap }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            previousBridge.refreshExplore()
            bridge.refreshExplore()
        }
        composeRule.waitForIdle()
        assertEquals(listOf(nextInfoMap.sourceUrl), refreshedSources)
    }

    private fun infoMap(name: String): InfoMap {
        val url = "explore-refresh-callback-$name"
        CacheManager.putMemory("infoMap_$url", "{}")
        return InfoMap(url)
    }
}
