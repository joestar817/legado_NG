package io.legado.app.ui.book.read.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w372dp-h826dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadTypographyControlsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun bodyPaddingControlsUpdateTheirOwnSideAndKeepTheOriginalBounds() {
        var padding by mutableStateOf(ReadBodyPadding(top = 6, bottom = 6, left = 16, right = 16))
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun label(id: Int) = context.getString(id)
        fun arrow(id: Int, action: Int) = "${label(id)}，${label(action)}"

        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadBodyPaddingControls(padding, Color.Black) { side, value ->
                    padding = when (side) {
                        ReadBodyPaddingSide.TOP -> padding.copy(top = value)
                        ReadBodyPaddingSide.BOTTOM -> padding.copy(bottom = value)
                        ReadBodyPaddingSide.LEFT -> padding.copy(left = value)
                        ReadBodyPaddingSide.RIGHT -> padding.copy(right = value)
                    }
                }
            }
        }
        composeRule.onNodeWithContentDescription(arrow(R.string.padding_top, R.string.plus)).performClick()
        composeRule.onNodeWithContentDescription(arrow(R.string.padding_right, R.string.reduce)).performClick()
        composeRule.runOnIdle { assertEquals(ReadBodyPadding(7, 6, 16, 15), padding) }

        composeRule.onNodeWithContentDescription(label(R.string.padding_top)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.padding_top)).performTextReplacement("201")
        composeRule.runOnIdle { assertEquals(7, padding.top) }
        composeRule.onNodeWithContentDescription(label(R.string.padding_top)).performImeAction()
        composeRule.runOnIdle { assertEquals(ReadBodyPadding(200, 6, 16, 15), padding) }

        composeRule.onNodeWithContentDescription(label(R.string.padding_left)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.padding_left)).performTextReplacement("101")
        composeRule.onNodeWithContentDescription(label(R.string.padding_left)).performImeAction()
        composeRule.runOnIdle { assertEquals(ReadBodyPadding(200, 6, 100, 15), padding) }
    }

    @Test
    fun arrowsAndTypedValuesUpdateTheirOwnSettings() {
        var size by mutableIntStateOf(18)
        var letterSpacing by mutableFloatStateOf(0.05f)
        var lineSpacing by mutableIntStateOf(16)
        var paragraphSpacing by mutableIntStateOf(8)
        var paragraphIndent by mutableIntStateOf(2)
        var textWeight by mutableIntStateOf(400)
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun label(id: Int) = context.getString(id)
        fun arrow(id: Int, action: Int) = "${label(id)}，${label(action)}"

        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadTypographyControls(
                    textSize = size, letterSpacing = letterSpacing,
                    lineSpacingExtra = lineSpacing, paragraphSpacing = paragraphSpacing,
                    paragraphIndentLevel = paragraphIndent, textWeight = textWeight,
                    contentColor = Color.Black,
                    onTextSizeChanged = { size = it },
                    onLetterSpacingChanged = { letterSpacing = it },
                    onLineSpacingChanged = { lineSpacing = it },
                    onParagraphSpacingChanged = { paragraphSpacing = it },
                    onParagraphIndentChanged = { paragraphIndent = it },
                    onTextWeightChanged = { textWeight = it },
                )
            }
        }
        composeRule.onNodeWithContentDescription(arrow(R.string.text_size, R.string.plus)).performClick()
        composeRule.onNodeWithContentDescription(arrow(R.string.line_size, R.string.reduce)).performClick()
        composeRule.runOnIdle {
            assertEquals(19, size)
            assertEquals(15, lineSpacing)
            assertEquals(0.05f, letterSpacing, 0.0001f)
            assertEquals(8, paragraphSpacing)
        }
        composeRule.onNodeWithContentDescription(label(R.string.text_size)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.text_size)).performTextReplacement("22")
        composeRule.onNodeWithContentDescription(label(R.string.text_size)).performKeyInput {
            keyDown(Key.Enter)
            keyUp(Key.Enter)
        }
        composeRule.runOnIdle { assertEquals(22, size) }
        composeRule.onNodeWithContentDescription(label(R.string.text_letter_spacing)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.text_letter_spacing)).performTextReplacement("-0.12")
        composeRule.onNodeWithContentDescription(label(R.string.text_letter_spacing)).performImeAction()
        composeRule.runOnIdle { assertEquals(-0.12f, letterSpacing, 0.0001f) }

        composeRule.onNodeWithContentDescription(label(R.string.paragraph_size)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.paragraph_size)).performTextReplacement("9")
        composeRule.runOnIdle { assertEquals(8, paragraphSpacing) }
        composeRule.onNodeWithContentDescription(label(R.string.paragraph_size)).performImeAction()
        composeRule.runOnIdle { assertEquals(20, paragraphSpacing) }

        composeRule.onNodeWithContentDescription(label(R.string.paragraph_first_line_indent))
            .assertTextEquals("2字")
        composeRule.onNodeWithContentDescription(arrow(R.string.paragraph_first_line_indent, R.string.plus)).performClick()
        composeRule.onNodeWithContentDescription(arrow(R.string.font_weight, R.string.plus)).performClick()
        composeRule.runOnIdle {
            assertEquals(3, paragraphIndent)
            assertEquals(500, textWeight)
        }
        composeRule.onNodeWithContentDescription(label(R.string.paragraph_first_line_indent)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.paragraph_first_line_indent)).performTextReplacement("99")
        composeRule.runOnIdle { assertEquals(3, paragraphIndent) }
        composeRule.onNodeWithContentDescription(label(R.string.paragraph_first_line_indent)).performImeAction()
        composeRule.runOnIdle { assertEquals(4, paragraphIndent) }
        composeRule.onNodeWithContentDescription(label(R.string.paragraph_first_line_indent)).assertTextEquals("4字")

        composeRule.onNodeWithContentDescription(label(R.string.font_weight)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.font_weight)).performTextReplacement("450")
        composeRule.onNodeWithContentDescription(label(R.string.font_weight)).performImeAction()
        composeRule.runOnIdle { assertEquals(500, textWeight) }
        composeRule.onNodeWithContentDescription(label(R.string.font_weight)).performClick()
        composeRule.onNodeWithContentDescription(label(R.string.font_weight)).performTextReplacement("9999")
        composeRule.onNodeWithContentDescription(label(R.string.font_weight)).performImeAction()
        composeRule.runOnIdle { assertEquals(900, textWeight) }
    }

    @Test
    fun longPressRepeatsByMetricStepAndStopsOnRelease() {
        var weight by mutableIntStateOf(400)
        val changes = mutableListOf<Int>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val plus = "标题字重，${context.getString(R.string.plus)}"
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadTypographyStandaloneMetric(
                    item = TypographyItem("标题字重", ReadTypographyMetric.TITLE_WEIGHT, weight) {
                        weight = it
                        changes += it
                    },
                    labelWidth = 70.dp,
                    contentColor = Color.Black,
                )
            }
        }
        composeRule.mainClock.autoAdvance = false
        val arrow = composeRule.onNodeWithContentDescription(plus)
        arrow.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000)
        var heldValue = 0
        var heldChanges = 0
        composeRule.runOnIdle {
            assertTrue("long press should make several steps", changes.size >= 2)
            assertTrue(changes.zipWithNext().all { (first, second) -> second - first == 100 })
            heldValue = weight
            heldChanges = changes.size
        }
        arrow.performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.runOnIdle {
            assertEquals(heldValue, weight)
            assertEquals(heldChanges, changes.size)
        }
    }

    @Test
    fun longPressRespectsBoundsAndStopsAfterCancellation() {
        var spacing by mutableIntStateOf(99)
        val changes = mutableListOf<Int>()
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun arrow(action: Int) = composeRule.onNodeWithContentDescription(
            "段间距，${context.getString(action)}",
        )
        composeRule.setContent {
            NgAppTheme(updateSystemBars = false) {
                ReadTypographyStandaloneMetric(
                    item = TypographyItem("段间距", ReadTypographyMetric.TITLE_SEGMENT_SPACING, spacing) {
                        spacing = it
                        changes += it
                    },
                    labelWidth = 60.dp,
                    contentColor = Color.Black,
                )
            }
        }
        composeRule.mainClock.autoAdvance = false
        val plus = arrow(R.string.plus)
        plus.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000)
        plus.performTouchInput { up() }
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.runOnIdle {
            assertEquals(100, spacing)
            assertEquals(listOf(100), changes)
        }
        val minus = arrow(R.string.reduce)
        minus.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(900)
        var cancelledValue = 0
        var cancelledChanges = 0
        composeRule.runOnIdle {
            assertTrue(spacing < 99)
            cancelledValue = spacing
            cancelledChanges = changes.size
        }
        minus.performTouchInput { cancel() }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.runOnIdle {
            assertEquals(cancelledValue, spacing)
            assertEquals(cancelledChanges, changes.size)
        }
    }

}
