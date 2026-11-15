package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
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
class ReadTypographyExpandableSettingTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun expandAndAdjustEachSettingWithoutChangingTheOther() {
        var indent by mutableIntStateOf(2)
        var weight by mutableIntStateOf(500)
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                Column {
                    ReadTypographyExpandableSetting(
                        title = "首行缩进", summary = "${indent}字", value = indent,
                        range = 0..4, steps = 3, contentColor = Color.Black,
                        onValueChanged = { indent = it },
                    )
                    ReadTypographyExpandableSetting(
                        title = "字重", summary = weight.toString(), value = weight,
                        range = 100..900, steps = 7, contentColor = Color.Black,
                        onValueChanged = { weight = it },
                    )
                }
            }
        }

        composeRule.onNodeWithContentDescription("首行缩进").assertDoesNotExist()
        composeRule.onNodeWithText("首行缩进").performClick()
        composeRule.onNodeWithContentDescription("首行缩进")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2.8f) }
        composeRule.runOnIdle {
            assertEquals(3, indent)
            assertEquals(500, weight)
        }

        composeRule.onNodeWithText("字重").performClick()
        composeRule.onNodeWithContentDescription("字重")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(680f) }
        composeRule.runOnIdle {
            assertEquals(3, indent)
            assertEquals(700, weight)
        }

        composeRule.onNodeWithText("首行缩进").performClick()
        composeRule.onNodeWithContentDescription("首行缩进").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("字重")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(900f) }
        composeRule.runOnIdle { assertEquals(900, weight) }
    }
}
