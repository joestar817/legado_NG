package io.legado.app.ui.book.info

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class BookInfoIntroInteropTest {
    @get:Rule val composeRule = createComposeRule()

    private val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))

    @Test
    fun previouslyAttachedIntroSurvivesRecompositionAndRenderModeChanges() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var previousParent: FrameLayout
        lateinit var intro: FrameLayout
        lateinit var content: TextView
        composeRule.runOnIdle {
            content = TextView(context).apply { text = "Existing introduction" }
            intro = FrameLayout(context).apply { addView(content) }
            previousParent = FrameLayout(context).apply { addView(intro) }
        }
        var visible by mutableStateOf(true)
        var state by mutableStateOf(BookInfoUiState())
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                if (visible) BookInfoIntro(state = state, introView = intro, withSurface = true)
            }
        }
        composeRule.runOnIdle {
            assertEquals(0, previousParent.childCount)
            assertNotSame(previousParent, intro.parent)
            assertSame(content, intro.getChildAt(0))
        }
        listOf(BookInfoIntroRenderMode.WEB, BookInfoIntroRenderMode.TEXT).forEach { mode ->
            composeRule.runOnIdle { visible = false }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                state = state.copy(introRenderMode = mode, introRevision = state.introRevision + 1)
                visible = true
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                assertTrue(intro.parent is ViewGroup)
                assertSame(content, intro.getChildAt(0))
                assertEquals("Existing introduction", content.text.toString())
            }
        }
    }

    @Test
    fun scrollingAwayAndResettingReattachesTheSameIntro() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        lateinit var intro: FrameLayout
        lateinit var content: TextView
        composeRule.runOnIdle {
            content = TextView(context).apply { text = "Introduction survives scroll" }
            intro = FrameLayout(context).apply { addView(content) }
        }
        lateinit var listState: LazyListState
        var resetToken by mutableIntStateOf(0)
        var state by mutableStateOf(BookInfoUiState())
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                listState = rememberLazyListState()
                LaunchedEffect(resetToken) { listState.scrollToItem(0) }
                LazyColumn(state = listState, modifier = Modifier.height(250.dp).testTag("introList")) {
                    item(key = "intro") {
                        BookInfoIntro(state = state, introView = intro, withSurface = true)
                    }
                    items(20) { Spacer(Modifier.fillMaxWidth().height(120.dp)) }
                }
            }
        }
        listOf(BookInfoIntroRenderMode.TEXT, BookInfoIntroRenderMode.WEB).forEach { mode ->
            composeRule.runOnIdle { state = state.copy(introRenderMode = mode) }
            repeat(3) {
                composeRule.onNodeWithTag("introList").performScrollToIndex(15)
                composeRule.runOnIdle { resetToken++ }
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    assertEquals(0, listState.firstVisibleItemIndex)
                    assertTrue(intro.parent is ViewGroup)
                    assertSame(content, intro.getChildAt(0))
                }
            }
        }
    }
}
