package io.legado.app.ui.config

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiReasoningLevel
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.main.bookshelf.bookshelfMenuItems
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class AiAssistantModelSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bookAiEntriesFollowTheSavedModelChoice() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        compose.setContent {
            Text(if (rememberHasAssistantModelSelection()) "configured" else "missing selection")
        }
        compose.onNodeWithText("missing selection").assertIsDisplayed()
        compose.runOnIdle { prefs.edit { putString(PreferKey.aiAssistantProviderId, "fixture-provider") } }
        compose.onNodeWithText("missing selection").assertIsDisplayed()
        compose.runOnIdle { prefs.edit { putString(PreferKey.aiAssistantModelId, "fixture-model") } }
        compose.onNodeWithText("configured").assertIsDisplayed()
        compose.runOnIdle { AiConfig.clearAssistantModel() }
        compose.onNodeWithText("missing selection").assertIsDisplayed()
        compose.runOnIdle {
            assertFalse(prefs.contains(PreferKey.aiAssistantProviderId))
            assertFalse(prefs.contains(PreferKey.aiAssistantModelId))
        }
    }

    @Test
    fun clearingAssistantRemovesTheSelectedModelsReasoningAndInheritedChoices() {
        val prefs = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        val provider = checkNotNull(AiProviderStore.provider("deepseek"))
        val selected = AiModel(id = "removed-model")
        val other = AiModel(id = "other-model")
        val inheritedKeys = listOf(
            PreferKey.aiAssistantReasoningLevel,
            PreferKey.aiAssistantDeepSeekReasoningEffort,
            PreferKey.aiAssistantModelReasoningEffort,
        )
        prefs.edit {
            putString(PreferKey.aiAssistantProviderId, provider.id)
            putString(PreferKey.aiAssistantModelId, selected.id)
            inheritedKeys.forEach { putString(it, "high") }
        }
        val choicePrefix = "${PreferKey.aiAssistantReasoningChoice}."
        AiConfig.saveAssistantModelReasoning(provider, other, "low")
        val otherKey = prefs.all.keys.single { it.startsWith(choicePrefix) }
        AiConfig.saveAssistantModelReasoning(provider, selected, "high")
        val selectedKey = prefs.all.keys.single { it.startsWith(choicePrefix) && it != otherKey }

        AiConfig.clearAssistantModel()

        assertFalse(AiConfig.hasAssistantModelSelection)
        inheritedKeys.forEach { assertFalse(prefs.contains(it)) }
        assertFalse(prefs.contains(selectedKey))
        assertEquals("low", prefs.getString(otherKey, null))
    }

    @Test
    fun clearingPurifyAndStoryboardAlsoResetsTheirReasoning() {
        AiConfig.savePurifyModel("provider", "purify")
        AiConfig.purifyReasoningLevel = AiReasoningLevel.HIGH
        AiConfig.saveReadAloudStoryboardModel("provider", "storyboard")
        AiConfig.readAloudStoryboardReasoningLevel = AiReasoningLevel.MEDIUM

        AiConfig.clearPurifyModel()

        assertTrue(AiConfig.purifyProviderId.isEmpty())
        assertTrue(AiConfig.purifyModelId.isEmpty())
        assertEquals(AiReasoningLevel.OFF, AiConfig.purifyReasoningLevel)
        assertEquals("storyboard", AiConfig.readAloudStoryboardModelId)
        assertEquals(AiReasoningLevel.MEDIUM, AiConfig.readAloudStoryboardReasoningLevel)

        AiConfig.clearReadAloudStoryboardModel()

        assertTrue(AiConfig.readAloudStoryboardProviderId.isEmpty())
        assertTrue(AiConfig.readAloudStoryboardModelId.isEmpty())
        assertEquals(AiReasoningLevel.OFF, AiConfig.readAloudStoryboardReasoningLevel)
    }

    @Test
    fun bookshelfMenuHidesOnlyTheAssistantEntry() {
        val menu = bookshelfMenuItems(includeBrowseHistory = true, includeAiManagement = false)
        assertFalse(menu.any { it.itemId == R.id.menu_ai_assistant })
        assertTrue(menu.any { it.itemId == R.id.menu_read_record })
        assertTrue(menu.any { it.itemId == R.id.menu_add_books })
        assertTrue(bookshelfMenuItems(includeBrowseHistory = true, includeAiManagement = true)
            .any { it.itemId == R.id.menu_ai_assistant })
    }
}
