package io.legado.app.ui.book.changecover

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.ui.book.changesource.ChangeChapterSourceSettingsUi
import io.legado.app.ui.book.changesource.ChangeSourceSettingsContent
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
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ChangeCoverSettingsContentTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun dialogUsesBookTypeAndTheEditorsUnfinishedSelection() {
        val book = Book(
            name = "example",
            author = "author",
            type = BookType.image or BookType.local,
            readConfig = Book.ReadConfig(customBookType = BookType.audio),
        )
        assertEquals(BookSourceType.audio, ChangeCoverDialog(book).requireArguments().getInt("sourceType"))
        assertEquals(
            BookSourceType.video,
            ChangeCoverDialog(book, BookType.video).requireArguments().getInt("sourceType"),
        )
    }

    @Test
    fun coverSettingsOnlyExposeTheTwoRequestedSwitches() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var sameType by mutableStateOf(false)
        var blocked by mutableStateOf(false)
        val theme = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = theme, updateSystemBars = false) {
                ChangeSourceSettingsContent(
                    settings = ChangeChapterSourceSettingsUi(
                        checkAuthor = false,
                        loadWordCount = false,
                        loadInfo = false,
                        loadToc = false,
                        selectedGroup = "",
                        groups = emptyList(),
                        sameTypeOnly = sameType,
                    ),
                    blockSourceDialogs = blocked,
                    onBack = {},
                    onToggleSameTypeOnly = { sameType = !sameType },
                    onToggleBlockSourceDialogs = { blocked = !blocked },
                    onToggleCheckAuthor = {},
                    onToggleLoadWordCount = {},
                    onToggleLoadInfo = {},
                    onToggleLoadToc = {},
                    showAdditionalSettings = false,
                )
            }
        }
        composeRule.onNodeWithText(context.getString(R.string.change_source_same_type_only)).assertExists().performClick()
        composeRule.onNodeWithText(context.getString(R.string.block_source_dialogs)).assertExists().performClick()
        composeRule.onNodeWithText(context.getString(R.string.checkAuthor)).assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.load_info)).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals(true to true, sameType to blocked) }
    }
}
