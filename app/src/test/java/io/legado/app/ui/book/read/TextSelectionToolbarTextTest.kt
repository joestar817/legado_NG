package io.legado.app.ui.book.read

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class TextSelectionToolbarTextTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun toolbarKeepsItsOriginalSizeForHighlightEditors() {
        assertEquals(336, textSelectionToolbarWidthDp(5))
        assertEquals(336, textSelectionToolbarWidthDp(1))
        assertEquals(52, textSelectionToolbarHeightDp(showHighlightEditor = false))
        assertEquals(96, textSelectionToolbarHeightDp(showHighlightEditor = true))
        assertEquals(156, textSelectionToolbarHeightDp(showHighlightEditor = true, showNoteEditor = true))
    }

    @Test
    fun hidingTextKeepsActionAccessibleAndClickable() {
        var showText by mutableStateOf(true)
        var clicks = 0
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                TextSelectionToolbar(
                    primaryActions = listOf(TextSelectionAction("Copy action", R.drawable.ic_copy) { clicks++ }),
                    showText = showText,
                    currentPage = 0,
                    onPageChange = {},
                    moreMenuVisible = false,
                    onMoreMenuVisibleChange = {},
                    onLongClick = {},
                )
            }
        }
        composeRule.onNodeWithText("Copy action").assertExists()
        composeRule.runOnIdle { showText = false }
        composeRule.onNodeWithText("Copy action").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Copy action", useUnmergedTree = true)
            .assertWidthIsEqualTo(24.dp)
        composeRule.onNodeWithContentDescription("Copy action").performClick()
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test
    fun noteEditorGrowsAndShrinksWithinTheAvailableHeight() {
        var note by mutableStateOf("short")
        var maximumHeight by mutableStateOf(220.dp)
        var contentHeight = 0
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                TextSelectionToolbar(
                    primaryActions = emptyList(),
                    showText = true,
                    currentPage = 0,
                    onPageChange = {},
                    moreMenuVisible = false,
                    onMoreMenuVisibleChange = {},
                    onLongClick = {},
                    textHighlight = Bookmark(),
                    noteEditorVisible = true,
                    noteEditorMaxHeight = maximumHeight,
                    noteDraft = note,
                    onNoteDraftChange = { note = it },
                    onContentHeightChanged = { contentHeight = it },
                )
            }
        }

        var shortHeight = 0
        var multilineHeight = 0
        var cappedHeight = 0
        composeRule.runOnIdle { shortHeight = contentHeight }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("one\ntwo\nthree\nfour")
        composeRule.runOnIdle {
            multilineHeight = contentHeight
            assertTrue("Explicit newlines should expand the editor", multilineHeight > shortHeight)
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("long text ".repeat(100))
        composeRule.runOnIdle {
            cappedHeight = contentHeight
            assertTrue("Wrapped text should also expand the editor", cappedHeight > multilineHeight)
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("long text ".repeat(200))
        composeRule.runOnIdle {
            assertEquals("Long notes should stop expanding at the available height", cappedHeight, contentHeight)
            maximumHeight = 120.dp
        }
        composeRule.runOnIdle {
            assertTrue("The editor should adapt when available space decreases", contentHeight < cappedHeight)
        }
        composeRule.onNode(hasSetTextAction()).performTextReplacement("short")
        composeRule.runOnIdle {
            assertEquals("Removing text should shrink the editor", shortHeight, contentHeight)
        }
        val field = composeRule.onNode(hasSetTextAction())
        field.performTextReplacement("")
        field.performClick()
        field.performTextInput("one")
        field.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        field.performTextInput("two")
        composeRule.runOnIdle { assertEquals("one\ntwo", note) }
    }

    private fun testTheme() = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFFB3261E.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))
}
