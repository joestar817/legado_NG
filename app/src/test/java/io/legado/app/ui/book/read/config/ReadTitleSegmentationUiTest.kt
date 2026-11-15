package io.legado.app.ui.book.read.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.provider.ReadTitleSegmentType
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
class ReadTitleSegmentationUiTest {
    @get:Rule val composeRule = createComposeRule()

    private fun menuOption(label: String) = composeRule.onNode(
        hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected),
    )

    private fun selectMode(label: String) {
        composeRule.onNodeWithText("分段").performClick()
        menuOption(label).performClick()
    }

    @Test
    fun fiveModesShowTheirControlsInlineAndKeepExistingParameters() {
        val pattern = "^第[0-9]+章"
        val config = ReadBookConfig.Config(
            titleSegType = ReadTitleSegmentType.REGEX,
            titleSegFlag = pattern,
            titleSegDistance = 4,
            titleSegScaling = 1.2f,
        )
        var revision by mutableIntStateOf(0)
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                NgFormPanel {
                    ReadTitleSegmentationSetting(config, revision, Color.Black) { revision++ }
                }
            }
        }

        composeRule.onNodeWithText("正则分段").assertExists()
        composeRule.onNodeWithText("正则").assertExists()
        composeRule.onNodeWithText("后段字号").assertExists()
        composeRule.onNodeWithText("段间距").assertExists()
        composeRule.onNodeWithText("分段").performClick()
        for (label in listOf("不分段", "章号分段", "字数分段", "分隔符分段", "正则分段")) {
            menuOption(label).assertExists()
        }
        menuOption("正则分段").assertIsSelected()
        menuOption("章号分段").performClick()
        composeRule.runOnIdle {
            assertEquals(ReadTitleSegmentType.CHAPTER_NUMBER, config.titleSegType)
            assertEquals(ReadTitleSegmentType.REGEX, config.titleSegCustomType)
            assertEquals(pattern, config.titleSegFlag)
        }
        composeRule.onNodeWithText("章号分段").assertExists()
        composeRule.onNodeWithText("正则").assertDoesNotExist()
        composeRule.onNodeWithText("字数").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("后段字号").performClick()
        composeRule.onNodeWithContentDescription("后段字号").performTextReplacement("80")
        composeRule.onNodeWithContentDescription("后段字号").performImeAction()
        composeRule.onNodeWithContentDescription("段间距").performClick()
        composeRule.onNodeWithContentDescription("段间距").performTextReplacement("0.3")
        composeRule.onNodeWithContentDescription("段间距").performImeAction()

        selectMode("字数分段")
        composeRule.onNodeWithText("字数").assertExists()
        composeRule.onNodeWithText("后段字号").assertExists()
        composeRule.onNodeWithText("段间距").assertExists()
        composeRule.runOnIdle {
            assertEquals(ReadTitleSegmentType.CHARACTER_COUNT, config.titleSegType)
            assertEquals(4, config.titleSegDistance)
            assertEquals(pattern, config.titleSegFlag)
            assertEquals(0.8f, config.titleSegScaling, 0f)
            assertEquals(3, config.titleLineSpacingSub)
        }

        selectMode("分隔符分段")
        composeRule.onNodeWithText("分隔符").assertExists()
        composeRule.onNodeWithText("分隔符，多个用英文逗号分隔").assertExists()
        composeRule.onNodeWithText("后段字号").assertExists()
        composeRule.runOnIdle { assertEquals(ReadTitleSegmentType.DELIMITER, config.titleSegType) }

        selectMode("正则分段")
        composeRule.onNodeWithText("正则").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText(pattern)).performTextReplacement("[")
        composeRule.onNodeWithText("正则表达式无效").assertExists()
        composeRule.runOnIdle { assertEquals(pattern, config.titleSegFlag) }
        composeRule.onNode(hasSetTextAction() and hasText("[")).performTextReplacement("第[0-9]+章")
        composeRule.runOnIdle { assertEquals("第[0-9]+章", config.titleSegFlag) }

        selectMode("不分段")
        composeRule.onNodeWithText("不分段").assertExists()
        composeRule.onNodeWithText("字数").assertDoesNotExist()
        composeRule.onNodeWithText("正则").assertDoesNotExist()
        composeRule.onNodeWithText("后段字号").assertDoesNotExist()
        composeRule.onNodeWithText("段间距").assertDoesNotExist()
        composeRule.runOnIdle {
            assertEquals(ReadTitleSegmentType.NONE, config.titleSegType)
            assertEquals(ReadTitleSegmentType.REGEX, config.titleSegCustomType)
            assertEquals("第[0-9]+章", config.titleSegFlag)
        }
        selectMode("正则分段")
        composeRule.onNodeWithText("正则").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText("第[0-9]+章")).assertExists()
    }

    @Test
    fun selectingCountFromNoneImmediatelyShowsItsSettings() {
        val config = ReadBookConfig.Config(titleSegType = ReadTitleSegmentType.NONE, titleSegCustomType = 0)
        var revision by mutableIntStateOf(0)
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                NgFormPanel {
                    ReadTitleSegmentationSetting(config, revision, Color.Black) { revision++ }
                }
            }
        }
        selectMode("字数分段")
        composeRule.runOnIdle {
            assertEquals(ReadTitleSegmentType.CHARACTER_COUNT, config.titleSegType)
            assertEquals(ReadTitleSegmentType.CHARACTER_COUNT, config.titleSegCustomType)
        }
        composeRule.onNodeWithText("字数分段").assertExists()
        composeRule.onNodeWithText("字数").assertExists()
        composeRule.onNodeWithText("后段字号").assertExists()
        composeRule.onNodeWithText("段间距").assertExists()
    }
}
