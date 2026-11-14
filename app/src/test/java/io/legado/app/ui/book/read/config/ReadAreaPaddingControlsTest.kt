package io.legado.app.ui.book.read.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.help.config.ReadBookConfig
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
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadAreaPaddingControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun bodyNumberInputUpdatesItsOwnPaddingWithoutChangingHeaderOrFooter() {
        val config = ReadBookConfig.config
        val original = config.copy()
        val context = ApplicationProvider.getApplicationContext<Context>()
        try {
            config.paddingTop = 10
            config.paddingBottom = 11
            config.paddingLeft = 12
            config.paddingRight = 13
            config.headerPaddingBottom = 21
            config.footerPaddingBottom = 31
            composeRule.setContent {
                NgAppTheme(updateSystemBars = false) { ReadAreaPaddingControls(ReadPaddingArea.BODY) }
            }
            val bottomLabel = context.getString(R.string.padding_bottom)
            composeRule.onNodeWithContentDescription(bottomLabel).performClick()
            composeRule.onNodeWithContentDescription(bottomLabel).performTextReplacement("24")
            composeRule.onNodeWithContentDescription(bottomLabel).performImeAction()
            composeRule.runOnIdle {
                assertEquals(listOf(10, 24, 12, 13), listOf(config.paddingTop, config.paddingBottom, config.paddingLeft, config.paddingRight))
                assertEquals(21, config.headerPaddingBottom)
                assertEquals(31, config.footerPaddingBottom)
            }
        } finally {
            config.paddingTop = original.paddingTop; config.paddingBottom = original.paddingBottom
            config.paddingLeft = original.paddingLeft; config.paddingRight = original.paddingRight
            config.headerPaddingBottom = original.headerPaddingBottom
            config.footerPaddingBottom = original.footerPaddingBottom
        }
    }

    @Test
    fun headerAndFooterNumberInputUseTheirOwnRangeAndValues() {
        val config = ReadBookConfig.config
        val original = config.copy()
        val context = ApplicationProvider.getApplicationContext<Context>()
        var area by mutableStateOf(ReadPaddingArea.HEADER)
        val topLabel = context.getString(R.string.padding_top)
        try {
            config.headerPaddingTop = 10
            config.footerPaddingTop = 20
            composeRule.setContent {
                NgAppTheme(updateSystemBars = false) { ReadAreaPaddingControls(area) }
            }
            composeRule.onNodeWithContentDescription(topLabel).performClick()
            composeRule.onNodeWithContentDescription(topLabel).performTextReplacement("101")
            composeRule.onNodeWithContentDescription(topLabel).performImeAction()
            composeRule.runOnIdle {
                assertEquals(100, config.headerPaddingTop)
                assertEquals(20, config.footerPaddingTop)
                area = ReadPaddingArea.FOOTER
            }
            composeRule.onNodeWithContentDescription(topLabel).performClick()
            composeRule.onNodeWithContentDescription(topLabel).performTextReplacement("0")
            composeRule.onNodeWithContentDescription(topLabel).performImeAction()
            composeRule.runOnIdle {
                assertEquals(100, config.headerPaddingTop)
                assertEquals(0, config.footerPaddingTop)
            }
        } finally {
            config.headerPaddingTop = original.headerPaddingTop
            config.footerPaddingTop = original.footerPaddingTop
        }
    }

    @Test
    fun controlsUpdateOnlyTheirOwnAreaAndDivider() {
        val config = ReadBookConfig.config
        val original = config.copy()
        val context = ApplicationProvider.getApplicationContext<Context>()
        var area by mutableStateOf(ReadPaddingArea.BODY)

        fun plus(label: Int) {
            val description = "${context.getString(label)}，${context.getString(R.string.plus)}"
            composeRule.onNodeWithContentDescription(description).performClick()
        }
        fun increaseAllMargins() {
            plus(R.string.padding_top)
            plus(R.string.padding_bottom)
            plus(R.string.padding_left)
            plus(R.string.padding_right)
        }

        try {
            config.paddingTop = 10; config.paddingBottom = 11; config.paddingLeft = 12; config.paddingRight = 13
            config.headerPaddingTop = 20; config.headerPaddingBottom = 21
            config.headerPaddingLeft = 22; config.headerPaddingRight = 23
            config.footerPaddingTop = 30; config.footerPaddingBottom = 31
            config.footerPaddingLeft = 32; config.footerPaddingRight = 33
            config.showHeaderLine = false; config.showFooterLine = false

            composeRule.setContent {
                NgAppTheme(updateSystemBars = false) { ReadAreaPaddingControls(area) }
            }
            composeRule.onNodeWithText(context.getString(R.string.divider_line)).assertDoesNotExist()
            increaseAllMargins()
            composeRule.runOnIdle {
                assertEquals(listOf(11, 12, 13, 14), listOf(config.paddingTop, config.paddingBottom, config.paddingLeft, config.paddingRight))
                assertEquals(listOf(20, 21, 22, 23), listOf(config.headerPaddingTop, config.headerPaddingBottom, config.headerPaddingLeft, config.headerPaddingRight))
                assertEquals(listOf(30, 31, 32, 33), listOf(config.footerPaddingTop, config.footerPaddingBottom, config.footerPaddingLeft, config.footerPaddingRight))
                area = ReadPaddingArea.HEADER
            }
            increaseAllMargins()
            composeRule.onNodeWithText(context.getString(R.string.divider_line)).performClick()
            composeRule.runOnIdle {
                assertEquals(listOf(21, 22, 23, 24), listOf(config.headerPaddingTop, config.headerPaddingBottom, config.headerPaddingLeft, config.headerPaddingRight))
                assertEquals(listOf(30, 31, 32, 33), listOf(config.footerPaddingTop, config.footerPaddingBottom, config.footerPaddingLeft, config.footerPaddingRight))
                assertEquals(true, config.showHeaderLine)
                assertEquals(false, config.showFooterLine)
                area = ReadPaddingArea.FOOTER
            }
            increaseAllMargins()
            composeRule.onNodeWithText(context.getString(R.string.divider_line)).performClick()
            composeRule.runOnIdle {
                assertEquals(listOf(31, 32, 33, 34), listOf(config.footerPaddingTop, config.footerPaddingBottom, config.footerPaddingLeft, config.footerPaddingRight))
                assertEquals(listOf(21, 22, 23, 24), listOf(config.headerPaddingTop, config.headerPaddingBottom, config.headerPaddingLeft, config.headerPaddingRight))
                assertEquals(true, config.showHeaderLine)
                assertEquals(true, config.showFooterLine)
            }
        } finally {
            config.paddingTop = original.paddingTop; config.paddingBottom = original.paddingBottom
            config.paddingLeft = original.paddingLeft; config.paddingRight = original.paddingRight
            config.headerPaddingTop = original.headerPaddingTop; config.headerPaddingBottom = original.headerPaddingBottom
            config.headerPaddingLeft = original.headerPaddingLeft; config.headerPaddingRight = original.headerPaddingRight
            config.footerPaddingTop = original.footerPaddingTop; config.footerPaddingBottom = original.footerPaddingBottom
            config.footerPaddingLeft = original.footerPaddingLeft; config.footerPaddingRight = original.footerPaddingRight
            config.showHeaderLine = original.showHeaderLine; config.showFooterLine = original.showFooterLine
        }
    }
}
