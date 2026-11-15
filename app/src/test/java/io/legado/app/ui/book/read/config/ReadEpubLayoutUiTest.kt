package io.legado.app.ui.book.read.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.data.entities.Book
import io.legado.app.help.config.EpubLayoutPreferences
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadEpubLayoutUiTest {
    @get:Rule val composeRule = createComposeRule()

    private val firstBook = Book(bookUrl = "epub-inline-first")
    private val secondBook = Book(bookUrl = "epub-inline-second")
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    @After
    fun clearTestPreferences() {
        EpubLayoutPreferences.reset(firstBook.bookUrl, context)
        EpubLayoutPreferences.reset(secondBook.bookUrl, context)
    }

    @Test
    fun inlineSettingsStayIndependentPerBookAndResetOnlyTheCurrentBook() {
        var currentBook by mutableStateOf(firstBook)
        val changedBooks = mutableListOf<String>()
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                EpubLayoutSettings(book = currentBook, onStyleChanged = { changedBooks += currentBook.bookUrl })
            }
        }

        listOf("原书排版优先", "原书字体", "原书字号", "原书文字颜色", "原书背景与装饰",
            "原书段落格式", "标题特殊样式", "首字特殊样式").forEach { title ->
            composeRule.onNodeWithText(title).assertExists()
        }
        composeRule.onNodeWithText("原书字体").performClick()
        composeRule.onNodeWithText("原书排版优先").performClick()
        composeRule.runOnIdle {
            val first = EpubLayoutPreferences.read(firstBook.bookUrl, context)
            assertFalse(first.getValue("font"))
            assertFalse(first.getValue(EpubLayoutPreferences.PUBLISHER))
            assertTrue(first.getValue("title"))
            assertTrue(EpubLayoutPreferences.read(secondBook.bookUrl, context).values.all { it })
            currentBook = secondBook
        }

        composeRule.onNodeWithText("标题特殊样式").performClick()
        composeRule.runOnIdle {
            assertFalse(EpubLayoutPreferences.read(secondBook.bookUrl, context).getValue("title"))
            currentBook = firstBook
        }
        composeRule.onNodeWithText("恢复默认").performClick()
        composeRule.runOnIdle {
            assertTrue(EpubLayoutPreferences.read(firstBook.bookUrl, context).values.all { it })
            assertFalse(EpubLayoutPreferences.read(secondBook.bookUrl, context).getValue("title"))
            assertEquals(listOf(firstBook.bookUrl, firstBook.bookUrl, secondBook.bookUrl, firstBook.bookUrl), changedBooks)
        }
    }

    @Test
    fun epubDockIsVisibleOnlyForEpubAndOpensItsPrimaryPage() {
        var isEpub by mutableStateOf(true)
        var selected by mutableStateOf(ReadStylePage.ADJUST)
        composeRule.setContent {
            NgAppTheme(snapshot = testTheme(), updateSystemBars = false) {
                ReadLayoutDock(
                    page = selected, isEpub = isEpub, contentColor = Color.Black,
                    accentColor = Color(0xFF008577), onSelect = { selected = it },
                )
            }
        }
        composeRule.onNodeWithText("EPUB").performClick()
        composeRule.runOnIdle {
            assertEquals(ReadStylePage.PAGE, selected)
            selected = ReadStylePage.ADJUST
            isEpub = false
        }
        composeRule.onNodeWithText("EPUB").assertDoesNotExist()
    }

    private fun testTheme() = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))
}
