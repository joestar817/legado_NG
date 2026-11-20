package io.legado.app.ui.config

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(
    sdk = [35],
    qualifiers = "en-rUS-w320dp-h720dp-xxhdpi",
    application = InterfaceFontTestApplication::class,
)
class AiCompactionThresholdDialogTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun sliderCommitsPercentagesAndKeepsOffAtZero() {
        val committed = mutableListOf<Int>()
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                AiCompactionThresholdDialogContent(initialPercent = 90) { committed += it }
            }
        }
        composeRule.onNodeWithText("90%").assertIsDisplayed()
        val slider = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        composeRule.onNodeWithText("95%").assertIsDisplayed()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        composeRule.onNodeWithText("55%").assertIsDisplayed()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        composeRule.onNodeWithText("50%").assertIsDisplayed()
        slider.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        composeRule.onNodeWithText("Off").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf(95, 55, 50, 0), committed) }
    }
}
