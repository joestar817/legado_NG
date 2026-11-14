package io.legado.app.ui.book.read

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.data.entities.BookChapter
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
class DenseCatalogInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    private val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))

    @Test
    fun tabsSelectAndExposeTheirActiveState() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var selected by mutableStateOf(CatalogTab.Chapters)
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                DenseCatalogTabs(
                    tabs = CatalogTab.entries, selectedTab = selected,
                    mutedColor = Color.Gray, accentColor = Color.Green,
                    onTabSelected = { selected = it },
                )
            }
        }
        val chapters = composeRule.onNodeWithText(context.getString(R.string.chapter_list))
        val bookmarks = composeRule.onNodeWithText(context.getString(R.string.bookmark))
        chapters.assertIsSelected()
        bookmarks.assertIsNotSelected().performClick().assertIsSelected()
        chapters.assertIsNotSelected()
    }

    @Test
    fun chapterRowsKeepVipStateMetadataAndActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val purchased = BookChapter(title = "Purchased", isVip = true, isPay = true,
            tag = "Revised", wordCount = "2492 words")
        val uncached = BookChapter(title = "Uncached", isVip = true, isPay = false,
            tag = "2026-10-08", wordCount = "2052 words")
        val volume = BookChapter(title = "Volume", isVolume = true)
        var clicked = ""
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                Column {
                    DenseCatalogChapterRow(purchased, purchased.title, true, true, true, true,
                        Color.Gray, Color.Green, null) { clicked = purchased.title }
                    DenseCatalogChapterRow(uncached, uncached.title, false, false, true, true,
                        Color.Gray, Color.Green, null) { clicked = uncached.title }
                    DenseCatalogChapterRow(volume, volume.title, false, true, true, true,
                        Color.Gray, Color.Green, true) { clicked = volume.title }
                }
            }
        }
        composeRule.onNodeWithText("Purchased").assertIsSelected().performClick()
        composeRule.runOnIdle { assertEquals("Purchased", clicked) }
        composeRule.onNodeWithText("Uncached").assertIsNotSelected()
        composeRule.onNodeWithText("2492 words", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2052 words", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("Revised", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2026-10-08", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.read_catalog_vip_purchased),
            useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.read_catalog_vip_unpaid),
            useUnmergedTree = true).assertExists()
        composeRule.onNodeWithContentDescription(context.getString(R.string.read_catalog_collapse_volume),
            useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Volume").performClick()
        composeRule.runOnIdle { assertEquals("Volume", clicked) }
    }

    @Test
    fun catalogStyleValuesKeepTheOriginalStylesAndIdentifyTheNewOne() {
        assertEquals(ReadCatalogStyle.CARD, ReadCatalogStyle.fromValue("card"))
        assertEquals(ReadCatalogStyle.COMPACT_SIDE, ReadCatalogStyle.fromValue("compactSide"))
        assertEquals(ReadCatalogStyle.DENSE_LIST, ReadCatalogStyle.fromValue("denseList"))
    }
}
