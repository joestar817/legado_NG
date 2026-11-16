package io.legado.app.ui.widget.dialog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
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
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class VariableEditorContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun dialogAcceptsReturnAndPreservesMultipleLines() {
        verifyMultilineInput(VariableEditorContentVariant.DIALOG)
    }

    @Test fun drawerAcceptsReturnAndPreservesMultipleLines() {
        verifyMultilineInput(VariableEditorContentVariant.DRAWER)
    }

    private fun verifyMultilineInput(variant: VariableEditorContentVariant) {
        var value by mutableStateOf("")
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                VariableEditorContent(value, "说明", { value = it }, variant)
            }
        }
        val field = composeRule.onNode(hasSetTextAction())
        field.performClick()
        field.performTextInput("first")
        field.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        field.performTextInput("second")
        composeRule.runOnIdle { assertEquals("first\nsecond", value) }
    }
}
