package io.legado.app.ui.book.explore

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w360dp-h800dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ExplorePageNavigationTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun cachedPageJumpUpdatesPageAndEnablesPreviousImmediately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val kind = ExploreKind(title = "Fixture", url = "https://example.invalid/fixture")
        val books = (1..36).map { number ->
            SearchBook(bookUrl = "https://example.invalid/book/$number", name = "Book $number")
        }
        val pages = books.mapIndexed { index, book -> book.bookUrl to if (index < 18) 1 else 2 }.toMap()
        val state = ExploreShowUiState(
            kinds = listOf(kind), selectedKind = kind, books = books, bookPages = pages,
            isKindsLoading = false, firstLoadedPage = 1, lastLoadedPage = 2, hasMore = false,
        )
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                ExploreShowScreen(
                    state = state, layoutMode = ExploreShowLayoutMode.GRID,
                    onBack = {}, onRefresh = {}, onRefreshKinds = {}, onSelectKind = {},
                    onLayoutModeChange = {}, onSelectPage = {}, onJumpToPage = {},
                    onLoadNext = {}, onRetryContent = {}, onOpenBook = {}, onShowError = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription(context.getString(R.string.next_page)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.menu_page, 2)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.prev_page)).assertIsEnabled()
        composeRule.onNodeWithContentDescription(context.getString(R.string.prev_page)).performClick()
        composeRule.onNodeWithText(context.getString(R.string.menu_page, 1)).assertIsDisplayed()
    }
}
