package io.legado.app.ui.config

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.ai.AiDefaultProviders
import io.legado.app.help.ai.AiExternalSearchClient
import io.legado.app.help.ai.AiExternalSearchDebugResult
import io.legado.app.help.ai.AiExternalSearchFailure
import io.legado.app.help.ai.AiExternalSearchProvider
import io.legado.app.help.ai.AiExternalSearchService
import io.legado.app.help.ai.AiExternalSearchSettings
import io.legado.app.help.ai.AiExternalSearchStore
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelAbility
import io.legado.app.help.ai.AiTavilySearchDepth
import io.legado.app.help.ai.AiWebAccess
import io.legado.app.help.ai.AiWebRoutingMode
import io.legado.app.help.ai.webAccess
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.components.compose.NgListState
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgLegacyThemeInput
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.getPrefString
import io.noties.markwon.ext.tables.TableRowSpan
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.appCtx
import java.util.concurrent.LinkedBlockingQueue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w390dp-h480dp-xxhdpi", application = InterfaceFontTestApplication::class)
class AiSearchServiceConfigInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val requests = LinkedBlockingQueue<Pair<String, JsonObject>>()
    private val shownResponses = mutableListOf<AiExternalSearchDebugResult<*>>()
    private var service by mutableStateOf(AiExternalSearchService(apiKey = "fixture-key"))
    private val model = AiSearchServiceDebugModel(AiExternalSearchClient(fixtureClient()))

    @Test
    fun assistantShowsOneWebSettingsEntryAndServicePageStillOpensConfiguration() {
        val tavily = AiExternalSearchService(AiExternalSearchProvider.TAVILY, apiKey = "tavily-key")
        var settings by mutableStateOf(AiExternalSearchSettings().withService(tavily))
        var showAssistant by mutableStateOf(true)
        var openedService: AiExternalSearchProvider? = null
        val actions = mutableListOf<AiAssistantModelSettingsAction>()
        showInTheme {
            if (showAssistant) {
                AiAssistantModelSettingsScreen(
                    AiAssistantModelSettingsScreenState(contextWindowSummary = "1M tokens"), settings,
                ) { actions += it }
            } else {
                AiSearchServiceList(
                    settings.services,
                    onOpen = { openedService = it },
                    onBack = {},
                    onReorder = { settings = settings.withServiceOrder(it) },
                )
            }
        }

        val reference = compose.onNodeWithText("Context window").getUnclippedBoundsInRoot()
        val titleLeft = compose.onNodeWithText("Context window", useUnmergedTree = true).getUnclippedBoundsInRoot().left
        val entry = compose.onNodeWithText("Web search").performScrollTo().assertIsDisplayed()
        val bounds = entry.getUnclippedBoundsInRoot()
        assertEquals(reference.left.value, bounds.left.value, 0.5f)
        assertEquals(reference.right.value, bounds.right.value, 0.5f)
        assertEquals((reference.bottom - reference.top).value, (bounds.bottom - bounds.top).value, 0.5f)
        assertEquals(titleLeft, compose.onNodeWithText("Web search", useUnmergedTree = true).getUnclippedBoundsInRoot().left)
        entry.assertTextContains("Auto · Built-in first").assertTextContains("›").performClick()
        compose.runOnIdle {
            assertEquals(AiAssistantModelSettingsAction.OpenWebSettings, actions.single())
            showAssistant = false
        }
        entry.assertDoesNotExist()
        compose.onNodeWithText("Tavily").performScrollTo().assertTextContains("Enabled").performClick()
        compose.runOnIdle { assertEquals(AiExternalSearchProvider.TAVILY, openedService) }
    }

    @Test
    fun externalSearchGlobeOverlaysTheWhaleWithinTheModelIconsHeight() {
        val blue = Color(0xFF4D6BFE)
        val teal = Color(0xFF008874)
        showInTheme {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = teal)) {
                Row {
                    Box(Modifier.size(34.dp).testTag("modelIcon"), contentAlignment = Alignment.Center) {
                        Image(painterResource(R.drawable.ic_provider_deepseek), null, Modifier.size(22.dp))
                    }
                    AiChatWebButton(AiWebAccess.External(AiExternalSearchProvider.DEEPSEEK, "DeepSeek")) {}
                }
            }
        }

        val rendered = compose.runOnIdle {
            val root = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                .also { root.draw(Canvas(it)) }
                .asImageBitmap()
        }
        fun iconPixels(node: SemanticsNodeInteraction): PixelMap {
            val bounds = node.fetchSemanticsNode().boundsInRoot
            return rendered.toPixelMap(
                startX = bounds.left.toInt(),
                startY = bounds.top.toInt(),
                width = bounds.width.toInt(),
                height = bounds.height.toInt(),
            )
        }

        val model = iconPixels(compose.onNodeWithTag("modelIcon"))
        val search = iconPixels(compose.onNodeWithContentDescription("Web search · DeepSeek"))
        val modelWhale = model.pixelsWithColor(blue)
        val searchWhale = search.pixelsWithColor(blue)
        val modelTop = modelWhale.minOf { it.y }
        val modelBottom = modelWhale.maxOf { it.y }
        assertEquals(modelTop, searchWhale.minOf { it.y })
        assertEquals(modelBottom, searchWhale.maxOf { it.y })

        val globe = search.pixelsWithColor(teal)
        assertTrue(globe.any { model[it.x, it.y] == blue })
        assertTrue(globe.all { it.x >= search.width / 2 && it.y in modelTop..modelBottom })
    }

    private fun PixelMap.pixelsWithColor(color: Color): List<IntOffset> = buildList {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (this@pixelsWithColor[x, y] == color) add(IntOffset(x, y))
            }
        }
    }

    @Test
    fun assistantWebDrawerSavesImmediatelyAndTheChatButtonReopensIt() {
        val initial = AiExternalSearchSettings(deepSeek = service.copy(enabled = false))
        val tavily = AiExternalSearchService(AiExternalSearchProvider.TAVILY, apiKey = "tavily-key")
        val provider = AiDefaultProviders.all().single { it.id == "deepseek" }
        val model = AiModel("deepseek-flash", abilities = listOf(AiModelAbility.TOOL))
        val original = AiExternalSearchStore.current()
        var showDrawer by mutableStateOf(true)
        var managementOpens = 0
        try {
            AiExternalSearchStore.update { initial }
            showInTheme {
                if (showDrawer) {
                    AiAssistantWebSettingsSheet(onManageServices = { managementOpens++ })
                } else {
                    val settings by AiExternalSearchStore.changes.collectAsState()
                    AiChatWebButton(settings.webAccess(provider, model)) { showDrawer = true }
                }
            }

            val master = compose.onNodeWithText("Enable web access")
            val mode = compose.onNodeWithText("Web access")
            val backup = compose.onNodeWithText("Fallback service")
            val manage = compose.onNodeWithText("Manage services")
            val tavilyCard = compose.onNodeWithText("Tavily")
            compose.onNodeWithText("Finished").assertDoesNotExist()
            val modeBounds = mode.getUnclippedBoundsInRoot()
            val backupBounds = backup.getUnclippedBoundsInRoot()
            master.assertIsOn().touchAssistantWebSwitch().assertIsOff()
            assertEquals(modeBounds, mode.assertIsDisplayed().getUnclippedBoundsInRoot())
            assertEquals(backupBounds, backup.assertIsOff().getUnclippedBoundsInRoot())
            compose.runOnIdle {
                assertEquals(initial.copy(webEnabled = false), AiExternalSearchStore.current())
                val savedJson = JsonParser.parseString(appCtx.getPrefString(PreferKey.aiExternalSearchJson)).asJsonObject
                assertEquals(false, savedJson.get("web_enabled").asBoolean)
                showDrawer = false
            }
            compose.onNodeWithContentDescription("Web search · Off").assertIsDisplayed().performClick()
            master.assertIsOff()
            backup.assertIsOff()
            manage.assertDoesNotExist()
            backup.touchAssistantWebSwitch().assertIsOn()
            manage.performScrollTo().performClick()
            compose.onNodeWithText("DeepSeek").assertDoesNotExist()
            compose.runOnIdle {
                assertEquals(1, managementOpens)
                assertEquals(initial.copy(webEnabled = false, backupEnabled = true), AiExternalSearchStore.current())
            }

            compose.onNodeWithText("External").performScrollTo().performClick()
            backup.assertDoesNotExist()
            compose.onNodeWithText("Choose a service").performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(AiWebRoutingMode.EXTERNAL, AiExternalSearchStore.current().routing)
                assertEquals("", AiExternalSearchStore.current().selectedService)
                AiExternalSearchStore.update { it.withService(tavily) }
            }
            tavilyCard.performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(false, AiExternalSearchStore.current().webEnabled)
                assertEquals(tavily.provider.id, AiExternalSearchStore.current().selectedService)
            }
            master.performScrollTo().touchAssistantWebSwitch().assertIsOn()
            tavilyCard.assertIsSelected()
            compose.runOnIdle {
                showDrawer = false
            }
            compose.onNodeWithContentDescription("Web search · Tavily").assertIsDisplayed().performClick()
            tavilyCard.assertIsSelected()
            compose.onNodeWithText("Auto").performScrollTo().performClick()
            backup.assertIsOn()
            compose.runOnIdle { showDrawer = false }
            compose.onNodeWithContentDescription("Web search · Built-in").assertIsDisplayed().performClick()
            master.assertIsOn().performClick().assertIsOff()
            mode.assertIsDisplayed()
            backup.assertIsOn()
            tavilyCard.performScrollTo().assertIsDisplayed().assertIsSelected()
            compose.runOnIdle {
                assertEquals(
                    initial.withService(tavily).copy(
                        webEnabled = false, backupEnabled = true, selectedService = tavily.provider.id,
                    ),
                    AiExternalSearchStore.current(),
                )
            }
        } finally {
            AiExternalSearchStore.update { original }
        }
    }

    private fun SemanticsNodeInteraction.touchAssistantWebSwitch(): SemanticsNodeInteraction =
        performTouchInput {
            // The 42 dp switch is inset 14 dp from the end of the full-width row.
            val insetToCenter = with(compose.density) { 35.dp.toPx() }
            click(Offset(width - insetToCenter, center.y))
        }

    @Test
    fun searchServiceListKeepsProviderCardLayoutAndShowsDisabledServices() {
        var showProvider by mutableStateOf(true)
        var searchService by mutableStateOf(AiExternalSearchService())
        var openedService: AiExternalSearchProvider? = null
        val provider = AiProviderListItemUiModel(
            id = "deepseek", name = "DeepSeek", summary = AiExternalSearchProvider.DEEPSEEK.host,
            iconRes = R.drawable.ic_provider_deepseek, enabled = true, modelCountText = "2 models", keyConfigured = false,
        )
        showInTheme {
            if (showProvider) {
                AiProviderListScreen(AiProviderListScreenState(listState = NgListState.Content(listOf(provider))), onAction = {})
            } else {
                AiSearchServiceList(
                    AiExternalSearchSettings(deepSeek = searchService).services,
                    onOpen = { openedService = it },
                    onBack = {},
                    onReorder = {},
                )
            }
        }
        val card = compose.onNodeWithText("DeepSeek")
        val name = compose.onNodeWithText("DeepSeek", useUnmergedTree = true)
        val host = compose.onNodeWithText(AiExternalSearchProvider.DEEPSEEK.host, useUnmergedTree = true)
        val menu = compose.onNodeWithContentDescription("Menu")
        val dragHandle = compose.onNodeWithTag("management_drag_deepseek", useUnmergedTree = true)
        val referenceCard = card.getUnclippedBoundsInRoot()
        val referenceName = name.getUnclippedBoundsInRoot()
        val referenceHost = host.getUnclippedBoundsInRoot()
        val referenceHandle = dragHandle.getUnclippedBoundsInRoot()
        val referenceTagColor = textStyle(host).color
        menu.assertIsDisplayed()
        compose.runOnIdle { showProvider = false }
        assertEquals(referenceCard, card.getUnclippedBoundsInRoot())
        assertEquals(referenceName, name.getUnclippedBoundsInRoot())
        assertEquals(referenceHandle, dragHandle.getUnclippedBoundsInRoot())
        val searchTag = compose.onAllNodesWithText("Search", useUnmergedTree = true)[0]
        val fetchTag = compose.onAllNodesWithText("Fetch", useUnmergedTree = true)[0]
        val searchBounds = searchTag.getUnclippedBoundsInRoot()
        val fetchBounds = fetchTag.getUnclippedBoundsInRoot()
        val hostBounds = host.getUnclippedBoundsInRoot()
        assertEquals(referenceHost, hostBounds)
        assertEquals(searchBounds.top, fetchBounds.top)
        assertEquals(searchBounds.top, hostBounds.top)
        assertTrue(hostBounds.right < searchBounds.left)
        assertTrue(searchBounds.right < fetchBounds.left)
        assertEquals(referenceTagColor, textStyle(host).color)
        assertEquals(referenceTagColor, textStyle(searchTag).color)
        assertEquals(referenceTagColor, textStyle(fetchTag).color)
        menu.assertDoesNotExist()
        card.assertTextContains("Search").assertTextContains("Fetch")
        card.assertTextContains("Enabled").assertTextContains("No API key")
        compose.onNodeWithText("External services").assertDoesNotExist()

        val search = compose.onNode(hasSetTextAction())
        search.performTextReplacement("other")
        card.assertDoesNotExist()
        compose.onNodeWithText("No matching services").assertIsDisplayed()
        search.performTextReplacement("feedcoopapi")
        val longHost = compose.onNodeWithText("open.feedcoopapi.com", useUnmergedTree = true).assertIsDisplayed()
        assertTrue(!textLayout(longHost).isLineEllipsized(0))
        compose.onNodeWithText("豆包搜索").assertTextContains("Search").assertTextContains("Fetch")
        card.assertDoesNotExist()
        search.performTextReplacement("Tavily")
        compose.onNodeWithText("api.tavily.com", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("api.tavily.com").assertTextContains("Search").assertTextContains("Fetch")
        search.performTextReplacement("SeEk")
        card.assertIsDisplayed()
        dragHandle.assertDoesNotExist()
        compose.runOnIdle { searchService = searchService.copy(enabled = false) }
        card.assertIsDisplayed().assertTextContains("Disabled")
        search.performTextReplacement("")
        card.assertIsDisplayed()
        dragHandle.assertIsDisplayed()
        search.performTextReplacement(AiExternalSearchProvider.DEEPSEEK.host)
        card.assertIsDisplayed().assertTextContains("Disabled").performClick()
        compose.runOnIdle { assertEquals(AiExternalSearchProvider.DEEPSEEK, openedService) }
    }

    @Test
    fun draggingAServiceCardSavesItsOrderAndKeepsItAfterReturningFromDetails() {
        val deepSeek = AiExternalSearchProvider.DEEPSEEK
        val doubao = AiExternalSearchProvider.DOUBAO
        val commits = mutableListOf<List<AiExternalSearchProvider>>()
        showConfiguredServices(listOf(deepSeek, doubao), onReorder = { commits += it })

        val source = compose.onNodeWithTag("management_drag_deepseek", useUnmergedTree = true)
        val target = compose.onNodeWithTag("management_drag_doubao", useUnmergedTree = true)
        val shift = target.fetchSemanticsNode().boundsInRoot.center.y - source.fetchSemanticsNode().boundsInRoot.center.y
        assertTrue(shift > 0)
        source.performTouchInput {
            down(center)
            moveTo(center + Offset(0f, shift), delayMillis = 180)
            up()
        }

        val expected = listOf(doubao, deepSeek) + AiExternalSearchProvider.entries.drop(2)
        compose.runOnIdle { assertEquals(listOf(expected), commits) }
        assertTrue(source.getUnclippedBoundsInRoot().top > target.getUnclippedBoundsInRoot().top)
        compose.onNodeWithText(doubao.defaultName).performClick()
        compose.onNodeWithContentDescription("More").assertIsDisplayed()
        back()
        assertTrue(source.getUnclippedBoundsInRoot().top > target.getUnclippedBoundsInRoot().top)
        compose.runOnIdle { assertEquals(listOf(expected), commits) }
    }

    @Test
    fun bothNewServiceCardsRunSearchAndFetchTheSameUrlWithTheirOwnProvider() {
        val providers = listOf(AiExternalSearchProvider.DOUBAO, AiExternalSearchProvider.TAVILY)
        showConfiguredServices(providers)
        for (provider in providers) {
            compose.onNodeWithText(provider.defaultName).performScrollTo().performClick()
            val doubao = provider == AiExternalSearchProvider.DOUBAO
            val option = compose.onNodeWithContentDescription(if (doubao) "Search mode" else "Search depth")
            option.performScrollTo().assertTextContains(if (doubao) "Custom" else "Basic")
            val resultCount = compose.onNodeWithContentDescription("Result count").performScrollTo()
            val maxCount = if (doubao) 50 else 20
            val test = compose.onNodeWithContentDescription("Test").performScrollTo()
            compose.onNode(hasSetTextAction() and hasText("")).performTextReplacement("catalog")
            resultCount.assertTextContains("10").performTextReplacement((maxCount + 1).toString())
            compose.onNodeWithText("Enter 1–$maxCount").performScrollTo().assertIsDisplayed()
            test.assertIsNotEnabled()
            resultCount.performTextReplacement(maxCount.toString())
            val plusCount = compose.onNodeWithContentDescription("Plus Result count")
            plusCount.assertIsNotEnabled()
            val alternate = if (doubao) "Global" else "Advanced"
            option.performScrollTo().performClick()
            compose.onNodeWithText(alternate).performClick()
            option.assertTextContains(alternate)
            resultCount.performScrollTo().assertTextContains("20")
            plusCount.assertIsNotEnabled()
            if (doubao) {
                resultCount.performTextReplacement("21")
                compose.onNodeWithText("Enter 1–20").performScrollTo().assertIsDisplayed()
                test.assertIsNotEnabled()
                resultCount.performTextReplacement("20")
            }
            test.performScrollTo().performClick()
            waitForResult { model.searchResult?.data?.size == 1 }
            compose.onNodeWithText("Book catalog", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Fetch article").performClick()
            waitForResult { model.article?.result?.data != null }
            compose.runOnIdle {
                assertEquals("Article from ${provider.id}", model.article?.result?.data?.text)
            }
            back()
            back()
            option.performScrollTo().assertTextContains(alternate)
            if (doubao) {
                option.performClick()
                compose.onNodeWithText("Custom").performClick()
                resultCount.performScrollTo().assertTextContains("20")
                plusCount.assertIsEnabled()
            }
            back()
        }
        assertEquals(
            listOf("/search_api/global_search", "/search_api/fetch", "/search", "/extract"),
            requests.map { it.first },
        )
        assertEquals(20, requests.first().second["DocCount"].asInt)
        assertEquals(20, requests.elementAt(2).second["max_results"].asInt)
        assertEquals("advanced", requests.elementAt(2).second["search_depth"].asString)
    }

    @Test
    fun tinyFishAndExaUseTheSharedServiceCardsAndDebugPages() {
        val providers = listOf(AiExternalSearchProvider.TINYFISH, AiExternalSearchProvider.EXA)
        val inputUrl = "https://example.org/book"
        showConfiguredServices(providers)
        for (provider in providers) {
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(provider.ordinal)
            compose.onNodeWithText(provider.defaultName)
                .assertTextContains("Search").assertTextContains("Fetch").performClick()
            val test = compose.onNodeWithContentDescription("Test").performScrollTo()
            val query = compose.onNode(hasSetTextAction() and hasText(""))
            query.performTextReplacement("夜无疆 Legado")
            test.performClick()
            waitForResult { model.searchResult?.data?.size == 1 }
            compose.onNodeWithText("Book catalog", useUnmergedTree = true).assertIsDisplayed()
            compose.onNodeWithText("Fetch article").performClick()
            waitForResult { model.article?.result?.data != null }
            compose.runOnIdle {
                assertEquals("Article from ${provider.id}", model.article?.result?.data?.text)
            }
            back()
            back()
            back()
        }
        val calls = requests.toList()
        assertEquals(listOf("/", "/", "/search", "/contents"), calls.map { it.first })
        assertEquals("CN", calls[0].second["location"].asString)
        assertEquals("zh", calls[0].second["language"].asString)
        assertEquals("markdown", calls[1].second["format"].asString)
        assertEquals("auto", calls[2].second["type"].asString)
        assertTrue(calls[3].second["text"].asBoolean)
        for (index in listOf(1, 3)) {
            assertEquals(inputUrl, calls[index].second.getAsJsonArray("urls").single().asString)
        }
    }

    @Test
    fun moreMenuOpensTheMatchingHelpForEveryService() {
        val providers = AiExternalSearchProvider.entries
        val opened = mutableListOf<String>()
        showConfiguredServices(providers, onHelp = { opened += it })
        for (provider in providers) {
            compose.onNode(hasScrollToIndexAction()).performScrollToIndex(provider.ordinal)
            compose.onNodeWithText(provider.defaultName).performClick()
            compose.onNodeWithContentDescription("More").performClick()
            compose.onNodeWithText("Reset settings").assertIsDisplayed()
            compose.onNodeWithText("Help").assertIsDisplayed().performClick()
            compose.onNodeWithText("Help").assertDoesNotExist()
            val expected = "${provider.id}SearchServiceHelp"
            compose.runOnIdle { assertEquals(expected, opened.last()) }
            val article = compose.activity.assets.open("web/help/md/$expected.md")
                .bufferedReader().use { it.readText() }
            assertTrue(article.lineSequence().first().contains(provider.defaultName))
            assertTrue("费用和免费额度" in article)
            assertTrue("注册和获取 API Key" in article)
            assertTrue("怎么在 NG 中使用" in article)
            back()
        }
        assertEquals(providers.size, opened.size)
    }

    @Test
    fun resetConfirmsAndRestoresOnlyTheCurrentServiceForm() {
        val original = AiExternalSearchService(
            provider = AiExternalSearchProvider.TAVILY,
            name = "My web search",
            apiKey = "fixture-key",
            resultCount = 7,
            enabled = false,
            tavilySearchDepth = AiTavilySearchDepth.ADVANCED,
        )
        service = original
        showConfig()
        val savedCount = compose.onNodeWithContentDescription("Search result count").performScrollTo()
        savedCount.performTextReplacement("11")
        compose.onNodeWithText("Enter 1–10").performScrollTo().assertIsDisplayed()
        val testCount = compose.onNodeWithContentDescription("Result count").performScrollTo()
        testCount.performTextReplacement("17")
        val query = compose.onNode(hasSetTextAction() and hasText("")).performScrollTo()
        query.performTextReplacement("test draft")

        val more = compose.onNodeWithContentDescription("More")
        val reset = compose.onNodeWithText("Reset settings")
        val cancel = compose.activity.getString(android.R.string.cancel)
        val confirm = compose.activity.getString(android.R.string.ok)
        more.performClick()
        reset.performClick()
        reset.assertIsDisplayed()
        compose.onNodeWithText(cancel).performClick()
        compose.runOnIdle { assertEquals(original, service) }
        savedCount.performScrollTo().assertTextContains("11")

        more.performClick()
        reset.performClick()
        compose.onNodeWithText(confirm).performClick()
        reset.assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(
                AiExternalSearchService(provider = original.provider, apiKey = original.apiKey, enabled = original.enabled),
                service,
            )
        }
        savedCount.performScrollTo().assertTextContains("10")
        compose.onNodeWithText("Enter 1–10").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search depth").performScrollTo().assertTextContains("Basic")
        compose.onNodeWithContentDescription("Enable service").performScrollTo().assertIsOff()
        testCount.performScrollTo().assertTextContains("17")
        compose.onNode(hasSetTextAction() and hasText("test draft")).assertExists()
        assertTrue(requests.isEmpty())
    }

    @Test
    fun searchOverrideAndQuerySurviveResultAndArticleNavigation() {
        showConfig()
        val savedCount = compose.onNodeWithContentDescription("Search result count").performScrollTo()
        val minusCount = compose.onNodeWithContentDescription("Minus Search result count")
        val plusCount = compose.onNodeWithContentDescription("Plus Search result count")
        plusCount.assertIsNotEnabled()
        minusCount.performTouchInput { click() }
        savedCount.assertTextContains("9").performTextReplacement("1")
        minusCount.assertIsNotEnabled()
        plusCount.performTouchInput { click() }
        savedCount.assertTextContains("2").performTextReplacement("7")
        savedCount.performTextReplacement("11")
        compose.onNodeWithText("Enter 1–10").assertIsDisplayed()
        compose.runOnIdle { assertEquals(7, service.resultCount) }
        savedCount.performTextReplacement("7")
        compose.onNodeWithText("Enter 1–10").assertDoesNotExist()
        val testButton = compose.onNodeWithContentDescription("Test")
        testButton.performScrollTo()
        val enableService = compose.onNodeWithContentDescription("Enable service")
        val backButton = compose.onNodeWithContentDescription("Back")
        val switchBounds = enableService.getUnclippedBoundsInRoot()
        assertTrue(backButton.getUnclippedBoundsInRoot().bottom < switchBounds.top)
        assertTrue(savedCount.getUnclippedBoundsInRoot().bottom < switchBounds.top)
        assertTrue(switchBounds.bottom < compose.onNodeWithContentDescription("Test service").getUnclippedBoundsInRoot().top)
        enableService.assertIsDisplayed().assertIsOn().performClick().assertIsOff()
        enableService.performClick().assertIsOn()
        compose.runOnIdle { assertTrue(service.enabled) }
        compose.onNodeWithText("Enable service").assertIsDisplayed()
        compose.onNodeWithText("Result count").assertExists()
        compose.onNodeWithText("Successful requests with results are billed per request.").assertDoesNotExist()
        val searchHint = compose.onNodeWithText("Enter keywords to search", useUnmergedTree = true)
        searchHint.assertIsDisplayed()
        testButton.assertIsNotEnabled()
        val emptyInput = compose.onNode(hasSetTextAction() and hasText(""))
        emptyInput.performTouchInput { click() }.assertIsFocused()
        searchHint.assertDoesNotExist()
        val testCount = compose.onNodeWithContentDescription("Result count").performScrollTo()
        val modeRow = compose.onNodeWithContentDescription("Test service").getUnclippedBoundsInRoot()
        val dropdownArrowCenter = (modeRow.right - 20.dp).value
        val testPlus = compose.onNodeWithContentDescription("Plus Result count")
        for (arrow in listOf(plusCount, testPlus)) {
            val bounds = arrow.getUnclippedBoundsInRoot()
            val center = (bounds.left + bounds.right).value / 2f
            assertEquals(dropdownArrowCenter, center, 0.5f)
            assertEquals(44f, (bounds.right - bounds.left).value, 0.5f)
        }
        testCount.assertTextContains("7").performTextReplacement("27")
        emptyInput.performTextReplacement("fixture query")
        testButton.assertIsEnabled()
        testButton.performScrollTo()
        val formPosition = testButton.fetchSemanticsNode().boundsInRoot.top
        testButton.performClick()

        waitForResult { model.searchResult?.data?.size == 27 }
        compose.onNodeWithText("Search results").assertIsDisplayed()
        compose.onNodeWithText("DeepSeek").assertDoesNotExist()
        val list = compose.onNode(hasScrollToIndexAction())
        val summary = compose.onNodeWithText("27 results", substring = true, useUnmergedTree = true).assertIsDisplayed()
        val response = compose.onNodeWithText("Raw response", useUnmergedTree = true).assertIsDisplayed()
        val summaryStyle = textStyle(summary)
        assertEquals(FontWeight.Normal, summaryStyle.fontWeight)
        assertEquals(textStyle(response).fontSize, summaryStyle.fontSize)
        val toolbarBottom = backButton.getUnclippedBoundsInRoot().bottom + 8.dp
        val listTop = list.getUnclippedBoundsInRoot().top
        val summaryBounds = summary.getUnclippedBoundsInRoot()
        val firstTitle = compose.onNodeWithText("Result item 1", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue(response.getUnclippedBoundsInRoot().bottom < toolbarBottom)
        assertEquals(16f, (listTop - toolbarBottom).value, 1f)
        assertTrue(summaryBounds.top >= listTop && summaryBounds.bottom < firstTitle.top)
        assertEquals(firstTitle.left.value, summaryBounds.left.value, 1f)
        summary.performTouchInput { click() }
        compose.onNodeWithText("Close").assertDoesNotExist()
        compose.onNodeWithText("Test service").assertDoesNotExist()
        assertEquals("fixture query", requests.first().second.get("query").asString)
        assertEquals(27, requests.first().second.get("count").asInt)
        assertEquals(7, service.resultCount)

        list.performScrollToIndex(10)
        summary.assertIsNotDisplayed()
        compose.onNodeWithText("Raw response").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(searchResults(27), shownResponses.single().response?.body) }
        val title = compose.onNodeWithText("Result item 11", useUnmergedTree = true).assertIsDisplayed()
        val longSummary = compose.onNodeWithText(longSearchSummary, useUnmergedTree = true)
        longSummary.assertDoesNotExist()
        title.performTouchInput { click() }
        longSummary.assertIsDisplayed()
        val scrollRange = longSummary.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scrollRange.maxValue() > 0f)
        compose.onNodeWithText("Close").assertIsDisplayed().performClick()
        longSummary.assertDoesNotExist()
        title.assertIsDisplayed()
        assertEquals(listOf("/v1/tools/search"), requests.map { it.first })

        val originalUrl = "https://example.org/article/11"
        val date = compose.onNodeWithText("Published 2026-11-20", useUnmergedTree = true).assertIsDisplayed()
        val originalLink = compose.onAllNodesWithText("Original link")[0].assertIsDisplayed()
        compose.onNodeWithText(originalUrl, useUnmergedTree = true).assertDoesNotExist()
        val fetchAction = compose.onAllNodesWithText("Fetch article")[0].assertIsDisplayed()
        assertTrue(fetchAction.getUnclippedBoundsInRoot().top < title.getUnclippedBoundsInRoot().top)
        assertTrue(date.getUnclippedBoundsInRoot().top > title.getUnclippedBoundsInRoot().bottom)
        assertTrue(originalLink.getUnclippedBoundsInRoot().left > date.getUnclippedBoundsInRoot().right)
        originalLink.performTouchInput { click() }
        compose.runOnIdle {
            val launch = requireNotNull(shadowOf(compose.activity).nextStartedActivity)
            val viewIntent = if (launch.action == Intent.ACTION_CHOOSER) {
                launch.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else launch
            assertEquals(Intent.ACTION_VIEW, viewIntent?.action)
            assertEquals(originalUrl, viewIntent?.dataString)
        }
        longSummary.assertDoesNotExist()
        fetchAction.performTouchInput { click() }
        waitForResult { model.article?.result?.data != null }
        compose.onNodeWithText("Fetched article").assertIsDisplayed()
        waitForResult { tableSpans().size == 3 }
        compose.runOnIdle {
            val invalidator = TableRowSpan::class.java.getDeclaredField("invalidator").apply { isAccessible = true }
            assertTrue("Markwon must schedule relayout for every multiline table row", tableSpans().all { invalidator.get(it) != null })
        }
        compose.onNodeWithText("Raw response").performClick()
        compose.runOnIdle { assertEquals(articleBody, shownResponses.last().response?.body) }

        back()
        compose.onNodeWithText("Result item 11").assertIsDisplayed()
        compose.onAllNodesWithText("Fetch article")[0].performClick()
        compose.onNodeWithText("Fetched article").assertIsDisplayed()
        assertEquals(listOf("/v1/tools/search", "/v1/tools/fetch"), requests.map { it.first })
        back()
        back()
        assertEquals(formPosition, testButton.fetchSemanticsNode().boundsInRoot.top, 1f)
        enableService.assertIsOn()
        compose.onNode(hasSetTextAction() and hasText("fixture query")).assertExists()
        testCount.assertTextContains("27")
    }

    @Test
    fun searchWithoutHitsShowsStatusAndRawResponse() {
        showConfig()
        val testButton = compose.onNodeWithContentDescription("Test").performScrollTo()
        compose.onNode(hasSetTextAction() and hasText("")).performTextReplacement("no fixture results")
        testButton.performClick()

        waitForResult { model.searchResult?.data?.isEmpty() == true }
        compose.onNodeWithText("No results", substring = true, useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Raw response").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(searchResults(0), shownResponses.single().response?.body) }
    }

    @Test
    fun fetchModeHidesCountAndShowsLocalValidationAndSuccessfulResponse() {
        showConfig()
        compose.onNodeWithText("Test service").performScrollTo().performClick()
        compose.onNodeWithText("Web fetch").performClick()
        compose.onNodeWithText("Result count").assertDoesNotExist()
        val testButton = compose.onNodeWithContentDescription("Test")
        testButton.performScrollTo().assertIsNotEnabled()
        val fetchHint = compose.onNodeWithText("Enter a link to fetch", useUnmergedTree = true)
        fetchHint.assertIsDisplayed()
        val emptyInput = compose.onNode(hasSetTextAction() and hasText(""))
        val emptyFieldBounds = emptyInput.getUnclippedBoundsInRoot()
        val emptyActionBounds = testButton.getUnclippedBoundsInRoot()
        emptyInput.performTouchInput { click() }.assertIsFocused()
        fetchHint.assertDoesNotExist()
        emptyInput.performTextReplacement("invalid fixture link")
        testButton.performScrollTo().performClick()

        compose.onNodeWithText("Fetch results").assertIsDisplayed()
        compose.onNodeWithText("Enter a valid HTTP or HTTPS link.", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Raw response").performClick()
        compose.runOnIdle { assertEquals(AiExternalSearchFailure.INVALID_URL, shownResponses.last().error?.failure) }
        assertTrue(requests.isEmpty())

        back()
        compose.onNodeWithText("Web fetch").assertExists()
        val link = "https://example.org/test-page?query=${"abcdefghij".repeat(14)}&from=legado"
        compose.onNode(hasSetTextAction() and hasText("invalid fixture link")).performTextReplacement(link)
        val filledInput = compose.onNode(hasSetTextAction() and hasText(link)).performScrollTo()
        val fieldBounds = filledInput.getUnclippedBoundsInRoot()
        val actionBounds = testButton.getUnclippedBoundsInRoot()
        val rightInset = fieldBounds.right - actionBounds.right
        val bottomInset = fieldBounds.bottom - actionBounds.bottom
        assertTrue(fieldBounds.bottom - fieldBounds.top > emptyFieldBounds.bottom - emptyFieldBounds.top)
        assertTrue(actionBounds.left > fieldBounds.left && rightInset.value > 0f && bottomInset.value > 0f)
        assertEquals((emptyFieldBounds.right - emptyActionBounds.right).value, rightInset.value, 0.5f)
        assertEquals((emptyFieldBounds.bottom - emptyActionBounds.bottom).value, bottomInset.value, 0.5f)
        testButton.performTouchInput { click() }
        waitForResult { model.fetches.firstOrNull()?.result?.data != null }
        assertEquals(link, requests.single().second.get("url").asString)
        assertTrue(!requests.single().second.has("count"))
        compose.onNodeWithText("Fetched article").performClick()
        compose.onNodeWithText("Fetch article").assertIsDisplayed()
    }

    private fun textStyle(node: SemanticsNodeInteraction): TextStyle = textLayout(node).layoutInput.style

    private fun textLayout(node: SemanticsNodeInteraction): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        return layouts.single()
    }

    private fun showConfig() = showInTheme {
        AiSearchServiceConfigScreen(
            service = service,
            model = model,
            onUpdateService = { service = it(service) },
            onBack = { model.handleBack() },
            onShowResponse = { shownResponses += it },
            onHelp = {},
        )
    }

    private fun showConfiguredServices(
        providers: List<AiExternalSearchProvider>,
        onHelp: (String) -> Unit = {},
        onReorder: (List<AiExternalSearchProvider>) -> Unit = {},
    ) {
        var editing by mutableStateOf<AiExternalSearchProvider?>(null)
        val configured = providers.fold(AiExternalSearchSettings()) { settings, provider ->
            settings.withService(settings.service(provider).copy(apiKey = "test-key", enabled = true))
        }
        var settings by mutableStateOf(configured)
        showInTheme {
            val provider = editing
            if (provider == null) {
                AiSearchServiceList(
                    settings.services,
                    onOpen = { editing = it },
                    onBack = {},
                    onReorder = { order ->
                        settings = settings.withServiceOrder(order)
                        onReorder(order)
                    },
                )
            } else {
                AiSearchServiceConfigScreen(settings.service(provider), model,
                    onUpdateService = { transform -> settings = settings.withService(transform(settings.service(provider))) },
                    onBack = {
                        if (!model.handleBack()) {
                            model.reset()
                            editing = null
                        }
                    },
                    onShowResponse = { shownResponses += it },
                    onHelp = onHelp,
                )
            }
        }
    }

    private fun showInTheme(content: @Composable () -> Unit) {
        val snapshot = NgThemeResolver.resolve(NgLegacyThemeInput(
            primaryColor = 0xFFF0FAF6.toInt(), accentColor = 0xFF008577.toInt(),
            backgroundColor = 0xFFF2F2F2.toInt(), bottomBackground = 0xFFF2F2F2.toInt(),
            errorColor = 0xFFB3261E.toInt(), isDark = false, isEInk = false,
        ))
        compose.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false, content = content)
        }
    }

    private fun back() = compose.onNodeWithContentDescription("Back").performClick()

    private fun waitForResult(condition: () -> Boolean) = compose.waitUntil(30_000) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }

    private fun tableSpans(): List<TableRowSpan> = compose.activity.window.decorView.tableSpans()

    private fun View.tableSpans(): List<TableRowSpan> = when (this) {
        is TextView -> (text as? Spanned)?.let { it.getSpans(0, it.length, TableRowSpan::class.java).toList() }.orEmpty()
        is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).tableSpans() }
        else -> emptyList()
    }

    private fun fixtureClient(): OkHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val input = if (request.method == "GET") {
            JsonObject().apply {
                for (key in request.url.queryParameterNames) addProperty(key, request.url.queryParameter(key))
            }
        } else {
            JsonParser.parseString(Buffer().apply { request.body?.writeTo(this) }.readUtf8()).asJsonObject
        }
        val path = request.url.encodedPath
        requests.add(path to input)
        val payload = when {
            request.url.host == "api.search.tinyfish.ai" -> """{"results":[{"title":"Book catalog","url":"https://example.org/book","snippet":"Matching book"}],"page":0,"total_results":1}"""
            request.url.host == "api.fetch.tinyfish.ai" -> """{"results":[{"url":"https://example.org/book","text":"Article from tinyfish"}],"errors":[]}"""
            request.url.host == "api.exa.ai" && path == "/search" -> """{"results":[{"title":"Book catalog","url":"https://example.org/book","highlights":["Matching book"]}]}"""
            request.url.host == "api.exa.ai" && path == "/contents" -> """{"results":[{"url":"https://example.org/book","text":"Article from exa"}]}"""
            path == "/v1/tools/search" -> searchResults(
                if (input.get("query").asString == "no fixture results") 0 else input.get("count").asInt,
            )
            path == "/v1/tools/fetch" -> articleBody
            path == "/search_api/web_search" -> """{"Result":{"ResultCount":1,"WebResults":[{"Title":"Book catalog","Url":"https://example.org/book","Snippet":"Matching book"}]}}"""
            path == "/search_api/global_search" -> """{"Result":{"ErrorCode":0,"TotalDocCount":1,"Documents":[{"Title":"Book catalog","Url":"https://example.org/book","Snippet":[{"Type":"text","Text":"Matching book"}]}]}}"""
            path == "/search_api/fetch" -> """{"Result":{"Data":{"StatusCode":200,"Url":"https://example.org/book","Title":"Book catalog","ContentText":"Article from doubao"}}}"""
            path == "/search" -> """{"results":[{"title":"Book catalog","url":"https://example.org/book","content":"Matching book"}]}"""
            path == "/extract" -> """{"results":[{"url":"https://example.org/book","raw_content":"Article from tavily"}],"failed_results":[]}"""
            else -> error("Unexpected test request: $path")
        }
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
            .code(200).message("Fixture").body(payload.toResponseBody()).build()
    }.build()

    private fun searchResults(count: Int): String = JsonObject().apply {
        add("results", JsonArray().apply {
            (1..count).forEach { number ->
                add(JsonObject().apply {
                    addProperty("title", "Result item $number")
                    addProperty("url", "https://example.org/article/$number")
                    addProperty("site_name", "Example News")
                    addProperty("snippet", "Summary for result item $number")
                    if (number == 11) {
                        addProperty("summary", longSearchSummary)
                        addProperty("date", "2026-11-20T09:30:00+00:00")
                    }
                })
            }
        })
    }.toString()

    private companion object {
        val longSearchSummary = "Long summary for result item 11.\n\n" +
            "Each section gives more context from the search service. ".repeat(18) + "\n\nEnd of the long summary."

        val articleBody = JsonObject().apply {
            addProperty("title", "Fetched article")
            addProperty("content", """
                # About the Search API

                | Field | Type | Required | Default | Explanation |
                | --- | --- | --- | --- | --- |
                | query | string | yes | - | Search terms and operators passed through to the provider. |
                | ori_question | string | no | - | Supply the original question so the search provider can understand what to look for. |

                After the table, the rest of the article must remain readable.
            """.trimIndent())
        }.toString()
    }
}
