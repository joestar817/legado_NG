package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.components.compose.NgFormPanel
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
class ReadTipColorSettingsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun eachMenuUsesItsOwnModesAndCustomOpensTheCorrectPicker() {
        var textColor by mutableIntStateOf(0)
        var dividerColor by mutableIntStateOf(-1)
        var textPickerCalls = 0
        var dividerPickerCalls = 0
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                NgFormPanel {
                    ReadTipColorSettings(
                        textColor = textColor,
                        dividerColor = dividerColor,
                        contentColor = Color.Black,
                        onFollowTextColor = { textColor = 0 },
                        onDividerModeChanged = { dividerColor = it },
                        onPickTextColor = { textPickerCalls++ },
                        onPickDividerColor = { dividerPickerCalls++ },
                    )
                }
            }
        }
        fun option(label: String) = composeRule.onNode(
            hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
        )

        composeRule.onNodeWithText("文字颜色").assertExists().performClick()
        option("跟随内容").assertIsSelected()
        option("自定义").performClick()
        composeRule.runOnIdle {
            assertEquals(1, textPickerCalls)
            assertEquals(0, dividerPickerCalls)
            assertEquals(0, textColor)
            textColor = 0xff123456.toInt()
        }

        composeRule.onNodeWithText("分隔线颜色").performClick()
        option("默认").assertIsSelected()
        option("跟随内容").performClick()
        composeRule.runOnIdle { assertEquals(0, dividerColor) }
        composeRule.onNodeWithText("分隔线颜色").performClick()
        option("自定义").performClick()
        composeRule.runOnIdle {
            assertEquals(1, dividerPickerCalls)
            assertEquals(0, dividerColor)
            dividerColor = 0xff123456.toInt()
        }

        composeRule.onNodeWithText("文字颜色").performClick()
        option("自定义").assertIsSelected()
        option("跟随内容").performClick()
        composeRule.runOnIdle { assertEquals(0, textColor) }
        composeRule.onNodeWithText("分隔线颜色").performClick()
        option("自定义").assertIsSelected()
        option("默认").performClick()
        composeRule.runOnIdle { assertEquals(-1, dividerColor) }
    }
}
