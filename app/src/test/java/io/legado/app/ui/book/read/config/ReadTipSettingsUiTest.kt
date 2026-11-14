package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
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
class ReadTipSettingsUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun tabsSwitchWhileInformationSelectionAndSwitchesRemainIndependent() {
        var tab by mutableIntStateOf(0)
        var position by mutableIntStateOf(0)
        var value by mutableIntStateOf(1)
        var enabled by mutableStateOf(true)
        var backButton by mutableStateOf(true)
        var header by mutableStateOf(true)
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                Column {
                    ReadTipSettingsTabs(tab, { tab = it }, "页眉")
                    if (tab == 0) {
                        ReadTipInformationPanel(
                            enabledTitle = "显示信息栏",
                            enabled = enabled,
                            onEnabledChanged = { enabled = it },
                            headerBackButton = if (header) backButton else null,
                            onHeaderBackButtonChanged = { backButton = it },
                            position = position,
                            onPositionChanged = { position = it },
                            positionSummaries = listOf("左内容", "中内容", "右内容"),
                            choices = listOf(
                                ReadTipChoiceUi(1, "标题", R.drawable.ic_check, value == 1, null),
                                ReadTipChoiceUi(2, "书名", R.drawable.ic_check, value == 2,
                                    if (value == 2) null else "已用于页脚 · 左"),
                            ),
                            onChoiceSelected = { value = it },
                            areaLabel = if (header) "页眉" else "页脚",
                        )
                    } else {
                        Text(if (tab == 1) "布局内容" else "图片内容")
                    }
                }
            }
        }

        composeRule.onNodeWithText("信息").assertIsSelected()
        composeRule.onNodeWithText("返回按钮").assertExists()
        composeRule.onNodeWithText("已用于页脚 · 左").assertExists()
        composeRule.onNodeWithText("中").performClick()
        composeRule.onNodeWithText("中").assertIsSelected()
        composeRule.onNodeWithText("书名").performClick()
        composeRule.onNodeWithText("书名").assertIsSelected()
        composeRule.runOnIdle { assertEquals(1, position); assertEquals(2, value) }

        composeRule.onNodeWithText("布局").performClick()
        composeRule.onNodeWithText("布局内容").assertExists()
        composeRule.onNodeWithText("图片").performClick()
        composeRule.onNodeWithText("图片内容").assertExists()
        composeRule.onNodeWithText("信息").performClick()
        composeRule.onNodeWithText("中").assertIsSelected()
        composeRule.onNodeWithText("书名").assertIsSelected()

        composeRule.onNodeWithText("显示信息栏").performClick()
        composeRule.onNodeWithText("书名").assertDoesNotExist()
        composeRule.onNodeWithText("显示信息栏").performClick()
        composeRule.onNodeWithText("书名").assertIsSelected()
        composeRule.runOnIdle { header = false }
        composeRule.onNodeWithText("返回按钮").assertDoesNotExist()
    }
}
