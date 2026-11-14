package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w372dp-h826dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadImageSettingsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun selectionAndRemovalAreIndependentForHeaderAndFooterWithoutSwitches() {
        var position by mutableStateOf(ReadImagePosition.HEADER)
        var headerPath by mutableStateOf("")
        var footerPath by mutableStateOf("")
        var choices = 0
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadImageSettings(ReadImageSettingsState(
                    position = position,
                    path = if (position == ReadImagePosition.HEADER) headerPath else footerPath,
                    width = 400,
                    height = 100,
                    importing = false,
                    error = null,
                    onChoose = {
                        choices++
                        if (position == ReadImagePosition.HEADER) headerPath = "header.png"
                        else footerPath = "footer.png"
                    },
                    onRemove = {
                        if (position == ReadImagePosition.HEADER) headerPath = "" else footerPath = ""
                    },
                ))
            }
        }
        val switches = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)
        composeRule.onAllNodes(switches, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("图片页眉").assertExists()
        composeRule.onNodeWithText("移除").assertDoesNotExist()
        composeRule.onNodeWithText("选择图片").performClick()
        composeRule.onNodeWithText("更换图片").performClick()
        composeRule.runOnIdle {
            assertEquals("header.png", headerPath)
            assertEquals(2, choices)
            position = ReadImagePosition.FOOTER
        }
        composeRule.onAllNodes(switches, useUnmergedTree = true).assertCountEquals(0)
        composeRule.onNodeWithText("图片页脚").assertExists()
        composeRule.onNodeWithText("选择图片").performClick()
        composeRule.onNodeWithText("移除").performClick()
        composeRule.runOnIdle {
            assertEquals("", footerPath)
            assertEquals("header.png", headerPath)
            position = ReadImagePosition.HEADER
        }
        composeRule.onNodeWithText("更换图片").assertExists()
        composeRule.onNodeWithText("移除").performClick()
        composeRule.onNodeWithText("选择图片").assertExists()
        composeRule.runOnIdle { assertEquals("", headerPath) }
    }
}
