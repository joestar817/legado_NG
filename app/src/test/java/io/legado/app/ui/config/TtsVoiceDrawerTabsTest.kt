package io.legado.app.ui.config

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.tts.TtsEngineSetting
import io.legado.app.help.tts.TtsEngineType
import io.legado.app.help.tts.TtsVoice
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
@Config(sdk = [35], qualifiers = "en-rUS-w320dp-h720dp-xxhdpi", application = InterfaceFontTestApplication::class)
class TtsVoiceDrawerTabsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun selectedVoiceOpensFirstAndOtherTabsStillScrollAndSwipe() {
        val opened = mutableListOf<String>()
        val groups = (1..7).map { number ->
            val id = "engine-$number"
            val engine = TtsEngineSetting(id = id, name = "Engine $number Long", type = TtsEngineType.SCRIPT)
            val option = TtsVoiceOption(engine, TtsVoice(id = "voice-$number", name = "Voice $number"), false)
            val chosen = number == 2
            val earlierVoices = if (chosen) {
                List(20) { index ->
                    TtsVoiceOption(engine, TtsVoice(id = "earlier-$index", name = "Earlier voice $index"), false)
                        .toDrawerCard(selected = false)
                }
            } else emptyList()
            TtsVoiceDrawerGroup(
                engineId = id,
                engineName = engine.name,
                cards = earlierVoices + option.toDrawerCard(selected = chosen),
            )
        }
        var state by mutableStateOf(TtsVoiceDrawerState())
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                TtsVoiceSelectionDrawerContent(
                    title = "Voices",
                    searchHint = "Search voices",
                    emptyText = "No voices",
                    state = state,
                    onSelect = {},
                    onPreview = {},
                    onEngineOpen = { opened.add(it) },
                )
            }
        }

        composeRule.runOnIdle { state = TtsVoiceDrawerState(loading = false, groups = groups) }
        composeRule.onNodeWithText("Engine 2 Long").assertIsSelected()
        composeRule.onNodeWithText("Voice 2").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(emptyList<String>(), opened) }
        composeRule.onNodeWithText("Engine 2 Long").performClick()
        composeRule.runOnIdle {
            assertEquals(listOf("engine-2"), opened)
            opened.clear()
        }
        composeRule.onNodeWithText("Engine 7 Long").performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(listOf("engine-7"), opened) }
        composeRule.onNodeWithText("Voice 7").assertIsDisplayed()
        composeRule.onNodeWithText("Engine 1 Long").performScrollTo().performClick().assertIsSelected()
        composeRule.runOnIdle { assertEquals(listOf("engine-7", "engine-1"), opened) }
        composeRule.onNodeWithText("Voice 1").assertIsDisplayed()
        composeRule.runOnIdle { opened.clear() }
        composeRule.onRoot().performTouchInput {
            swipeLeft(startX = width * 0.8f, endX = width * 0.2f)
        }
        composeRule.onNodeWithText("Engine 3 Long").assertIsSelected()
        composeRule.runOnIdle { assertEquals(listOf("engine-3"), opened) }
    }
}
