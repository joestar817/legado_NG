package io.legado.app.ui.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w320dp-h720dp-xxhdpi", application = InterfaceFontTestApplication::class)
class AiModelSelectionSheetInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    private val providers = listOf(
        provider("a", "Provider A", "Alpha"),
        provider("b", "Provider B", "Beta"),
    )
    private val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
        primaryColor = 0xFFFFF1E8.toInt(), accentColor = 0xFF008577.toInt(),
        backgroundColor = 0xFFFFF9F5.toInt(), bottomBackground = 0xFFFFF1E8.toInt(),
        errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
    ))

    @Test
    fun tabsSwipeSearchAndLocatePreserveModelSelection() {
        val selected = mutableListOf<Pair<String, String>>()
        val state = AiModelSelectionSheetState(
            title = "Models", emptyText = "No models", providers = providers,
            selectedProviderId = "b", selectedModelId = "Beta",
        )
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                AiModelSelectionSheet(
                    state = state, isRefreshing = false, onRefresh = {},
                    onSelect = { provider, model -> selected += provider to model },
                )
            }
        }

        composeRule.onNodeWithText("Provider A").assertIsSelected()
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.onNodeWithText("Provider B").assertIsSelected()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()
        composeRule.runOnIdle { assertTrue(selected.isEmpty()) }

        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithContentDescription(context.getString(R.string.ai_search_model)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Alpha")
        composeRule.onNodeWithText("No matching models").assertIsDisplayed()
        composeRule.onNodeWithText("Provider A").performClick().assertIsSelected()
        composeRule.onNodeWithText("Alpha").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(context.getString(R.string.ai_locate_selected_model)).performClick()
        composeRule.onNodeWithText("Provider B").assertIsSelected()
        composeRule.onNodeWithText("Beta").assertIsDisplayed()
        composeRule.onNodeWithText("Beta").performClick()
        composeRule.runOnIdle { assertEquals(listOf("b" to "Beta"), selected) }
    }

    @Test
    fun emptyCatalogLoadsOnlyAfterOpeningAndCanBeRetried() {
        val calls = mutableListOf<String>()
        val empty = AiModelSelectionProviderUiModel(
            id = "empty", name = "Empty Provider", iconRes = R.drawable.ic_cfg_web,
            models = emptyList(), needsFetch = true,
        )
        var current by mutableStateOf(AiModelSelectionSheetState(
            title = "Models", emptyText = "No models", providers = listOf(providers[0], empty),
        ))
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                AiModelSelectionSheet(
                    state = current, isRefreshing = false, onRefresh = {},
                    onFetchProvider = { id ->
                        calls += id
                        if (calls.size == 1) error("test failure")
                        current = current.copy(providers = current.providers.map {
                            if (it.id == id) provider("empty", "Empty Provider", "Fetched") else it
                        })
                    },
                    onSelect = { _, _ -> },
                )
            }
        }

        composeRule.runOnIdle { assertTrue(calls.isEmpty()) }
        composeRule.onNodeWithText("Empty Provider").performClick()
        composeRule.onNodeWithText("Failed to fetch models").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("empty"), calls) }
        composeRule.onNodeWithText("Retry").performClick()
        composeRule.onNodeWithText("Fetched").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(listOf("empty", "empty"), calls) }
        composeRule.onNodeWithText("Provider A").performClick()
        composeRule.onNodeWithText("Empty Provider").performClick()
        composeRule.runOnIdle { assertEquals(listOf("empty", "empty"), calls) }
    }

    @Test
    fun anotherProviderCanLoadWhileAnEarlierRequestIsWaiting() {
        val calls = mutableListOf<String>()
        val slowRequest = CompletableDeferred<Unit>()
        val slow = AiModelSelectionProviderUiModel(
            id = "slow", name = "Slow Provider", iconRes = R.drawable.ic_cfg_web,
            models = emptyList(), needsFetch = true,
        )
        val fast = AiModelSelectionProviderUiModel(
            id = "fast", name = "Fast Provider", iconRes = R.drawable.ic_cfg_web,
            models = emptyList(), needsFetch = true,
        )
        val state = AiModelSelectionSheetState(
            title = "Models", emptyText = null, providers = listOf(providers[0], slow, fast),
        )
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                AiModelSelectionSheet(
                    state = state, isRefreshing = false, onRefresh = {},
                    onFetchProvider = { id ->
                        calls += id
                        if (id == "slow") slowRequest.await()
                    },
                    onSelect = { _, _ -> },
                )
            }
        }
        composeRule.onNodeWithText("Slow Provider").performClick()
        composeRule.runOnIdle { assertEquals(listOf("slow"), calls) }
        composeRule.onNodeWithText("Fast Provider").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals(listOf("slow", "fast"), calls) }
        slowRequest.complete(Unit)
    }

    @Test
    fun swipingIntoEmptyProviderFetchesOnlyThatProvider() {
        val calls = mutableListOf<String>()
        val empty = AiModelSelectionProviderUiModel(
            id = "empty", name = "Empty Provider", iconRes = R.drawable.ic_cfg_web,
            models = emptyList(), needsFetch = true,
        )
        val state = AiModelSelectionSheetState(
            title = "Models", emptyText = null, providers = listOf(providers[0], empty),
        )
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                AiModelSelectionSheet(
                    state = state, isRefreshing = false, onRefresh = {},
                    onFetchProvider = { calls += it }, onSelect = { _, _ -> },
                )
            }
        }
        composeRule.runOnIdle { assertTrue(calls.isEmpty()) }
        composeRule.onRoot().performTouchInput { swipeLeft() }
        composeRule.runOnIdle { assertEquals(listOf("empty"), calls) }
    }

    @Test
    fun searchOnAutomaticPageShowsNoMatchingModels() {
        val state = AiModelSelectionSheetState(
            title = "Models", emptyText = null, providers = providers,
            followAssistantLabel = "Follow chat model",
        )
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                AiModelSelectionSheet(
                    state = state, isRefreshing = false, onRefresh = {},
                    onSelect = { _, _ -> }, onFollowAssistant = {},
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        composeRule.onNodeWithContentDescription(context.getString(R.string.ai_search_model)).performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Beta")
        composeRule.onNodeWithText("No matching models").assertIsDisplayed()
    }

    @Test
    fun automaticPageKeepsFollowAssistantAction() {
        var followed = false
        val state = AiModelSelectionSheetState(
            title = "Models", emptyText = null, providers = providers,
            followAssistantLabel = "Follow chat model", followAssistantSelected = true,
        )
        composeRule.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                AiModelSelectionSheet(
                    state = state, isRefreshing = false, onRefresh = {}, onSelect = { _, _ -> },
                    onFollowAssistant = { followed = true },
                )
            }
        }
        composeRule.onNodeWithText("Automatic").assertIsSelected()
        composeRule.onNodeWithText("Follow chat model").performClick()
        composeRule.runOnIdle { assertTrue(followed) }
    }

    private fun provider(id: String, name: String, modelName: String) = AiModelSelectionProviderUiModel(
        id = id,
        name = name,
        iconRes = R.drawable.ic_cfg_web,
        models = listOf(AiModelSelectionItemUiModel(
            id = modelName,
            name = modelName,
            iconRes = R.drawable.ic_cfg_web,
            capabilities = emptyList(),
        )),
    )
}
