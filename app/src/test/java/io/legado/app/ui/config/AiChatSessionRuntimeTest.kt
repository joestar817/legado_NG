package io.legado.app.ui.config

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AgentModeIdentity
import io.legado.app.help.ai.AgentModeRegistry
import io.legado.app.help.ai.AiBookSearchCandidate
import io.legado.app.help.ai.AiBookSearchMedia
import io.legado.app.help.ai.AiBookSearchReply
import io.legado.app.help.ai.AiBookSourceStatus
import io.legado.app.help.ai.AiChatClient
import io.legado.app.help.ai.AiChatHistoryStore
import io.legado.app.help.ai.AiChatSessionSnapshot
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiExternalSearchClient
import io.legado.app.help.ai.AiExternalSearchProvider
import io.legado.app.help.ai.AiExternalSearchService
import io.legado.app.help.ai.AiExternalSearchSettings
import io.legado.app.help.ai.AiExternalSearchStore
import io.legado.app.help.ai.AiImageDraft
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiModelRegistry
import io.legado.app.help.ai.AiModelSelection
import io.legado.app.help.ai.AiOperationPermissionMode
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.AiProviderStore
import io.legado.app.help.ai.AiProviderType
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.ai.AiWebRoutingMode
import io.legado.app.help.ai.withFetchedModels
import io.legado.pet.api.PetActivities
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AiChatSessionRuntimeTest {
    private val generalEntry = AiChatSessionEntry("entry")

    @Test
    fun imageDraftAndModeRestoreOnlyIntoTheirOwnConversation() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val drafts = mutableMapOf<String, AiImageDraft>()
        val snapshots = mutableMapOf<String, AiChatSessionSnapshot>()
        val coordinator = AiChatSessions(
            scope,
            readImageDraft = { drafts[it] },
            writeImageDraft = { id, draft -> drafts[id] = draft },
            writeSnapshot = { snapshots[it.id] = it },
        )
        try {
            val first = AiChatSessionRuntime(coordinator, AgentModeRegistry.general, null, emptyList(), id = "first-image")
            first.input = "change the sky"
            first.replaceImageDraftAssets(listOf("reference"))
            first.setExplicitImageDraft(true)
            first.persist()?.await()
            val reopened = AiChatSessionRuntime(coordinator, AgentModeRegistry.general, null, emptyList(),
                id = first.id, saved = requireNotNull(snapshots[first.id]))
            val another = AiChatSessionRuntime(coordinator, AgentModeRegistry.general, null, emptyList(), id = "other-image")

            assertEquals("change the sky", reopened.input)
            assertEquals(listOf("reference"), reopened.imageDraftAssets.toList())
            assertTrue(reopened.directImageDraft)
            assertTrue(reopened.explicitImageDraft)
            assertTrue(reopened.hasDraft)
            assertTrue(another.imageDraftAssets.isEmpty())
            assertFalse(another.directImageDraft)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun continueEditingUsesItsReferenceWithoutSelectingTheLabel() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val gson = Gson()
        val drafts = mutableMapOf<String, String>()
        val snapshots = mutableMapOf<String, AiChatSessionSnapshot>()
        val coordinator = AiChatSessions(
            scope,
            readImageDraft = { id -> drafts[id]?.let { gson.fromJson(it, AiImageDraft::class.java) } },
            writeImageDraft = { id, draft -> drafts[id] = gson.toJson(draft) },
            writeSnapshot = { snapshots[it.id] = it },
        )
        try {
            val session = AiChatSessionRuntime(coordinator, AgentModeRegistry.general, null, emptyList())
            session.setExplicitImageDraft(true)
            session.replaceImageDraftAssets(listOf("photo", "work-one"), editAsset = "work-one")
            session.replaceImageDraftAssets(listOf("photo", "work-one", "work-two"), editAsset = "work-two")
            assertTrue(session.directImageDraft)
            assertFalse(session.explicitImageDraft)
            session.persist()?.await()

            val restored = AiChatSessionRuntime(coordinator, AgentModeRegistry.general, null, emptyList(),
                id = session.id, saved = requireNotNull(snapshots[session.id]))
            assertTrue(restored.directImageDraft)
            assertFalse(restored.explicitImageDraft)

            restored.replaceImageDraftAssets(restored.imageDraftAssets - "work-one")
            assertTrue(restored.directImageDraft)
            restored.replaceImageDraftAssets(restored.imageDraftAssets - "work-two")
            assertFalse(restored.directImageDraft)
            restored.replaceImageDraftAssets(restored.imageDraftAssets + "work-two")
            assertFalse(restored.directImageDraft)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun browsingHistoryDoesNotReplaceTheDedicatedSearchSession() = runBlocking {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = AiChatSessions(scope, writeSnapshot = {})
        val mode = AgentModeRegistry.bookSearch
        val context = AgentModeEntryContext(contextId = mode.id)
        val entry = chatSessionEntry(AiChatActivity.ENTRY_BOOK_SEARCH, mode, context, "first book", emptyList(), emptyList())
        val otherEntry = chatSessionEntry(AiChatActivity.ENTRY_BOOK_SEARCH, mode, context, "other book", emptyList(), emptyList())
        val first = AiChatSessionRuntime(coordinator, mode, context, emptyList(), id = "search-first")
        val other = AiChatSessionRuntime(coordinator, mode, context, emptyList(), id = "search-other")
        val ordinary = AiChatSessionRuntime(coordinator, AgentModeRegistry.general, null, emptyList(), id = "ordinary")
        try {
            coordinator.select(entry, first, createdForEntry = true)
            coordinator.select(otherEntry, other, createdForEntry = true)
            coordinator.select(entry, other)
            coordinator.select(entry, ordinary)
            coordinator.select(entry, ordinary, createdForEntry = true)
            assertEquals(first.id, coordinator.selectedFor(entry))
            assertEquals(other.id, coordinator.selectedFor(otherEntry))
            assertSame(ordinary, coordinator.get(ordinary.id))
            assertFalse(entry.matches(ordinary.mode.identity, ordinary.activeModeEntryContext))

            val newSearch = AiChatSessionRuntime(coordinator, mode, context, emptyList(), id = "new-search")
            coordinator.select(entry, newSearch, createdForEntry = true)
            coordinator.select(entry, first)
            assertEquals(newSearch.id, coordinator.selectedFor(entry))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun bookScanReusesTheSameWorkAfterChapterAndSourceChanges() = runBlocking {
        fun context(work: String, chapter: Int, bookUrl: String) = AgentModeEntryContext(
            contextId = "book_detail",
            title = "Same title",
            payload = JsonObject().apply {
                addProperty("work_key", work)
                addProperty("current_chapter_index", chapter)
                addProperty("latest_chapter_title", "Chapter $chapter")
                addProperty("book_url", bookUrl)
            },
        )
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = AiChatSessions(scope, writeSnapshot = {})
        val mode = AgentModeRegistry.bookScan
        val original = context("first-work", 10, "https://example.test/1")
        val updated = context("first-work", 12, "https://example.test/2")
        val anotherWork = context("different-work", 12, "https://example.test/2")
        fun entry(context: AgentModeEntryContext) = chatSessionEntry(
            AiChatActivity.ENTRY_BOOK_SCAN, mode, context, "", emptyList(), emptyList(),
        )
        val session = AiChatSessionRuntime(coordinator, mode, original, emptyList(), id = "scan-first")
        val anotherSession = AiChatSessionRuntime(coordinator, mode, anotherWork, emptyList(), id = "scan-other")
        try {
            coordinator.select(entry(original), session, createdForEntry = true)
            coordinator.select(entry(anotherWork), anotherSession, createdForEntry = true)
            coordinator.select(entry(original), anotherSession, createdForEntry = true)
            assertEquals(session.id, coordinator.selectedFor(entry(updated)))
            assertEquals(anotherSession.id, coordinator.selectedFor(entry(anotherWork)))
            assertTrue(entry(updated).matches(mode.identity, original))
            assertFalse(entry(updated).matches(mode.identity, anotherWork))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun waitingTasksReturnToIdleAndOnlyRunningTasksSelectWorkActivity() {
        val scan = AiChatTask("scan", "AI 扫书", AiChatTaskState.WAITING)
        val repair = AiChatTask("repair", "修复书源", AiChatTaskState.CONFIRM_TOOL,
            workActivity = PetActivities.BOOK_SOURCE_REPAIR)
        val search = AiChatTask("search", "AI 搜书", AiChatTaskState.WEB_SEARCH)
        assertNull(listOf(scan, repair).petWorkActivity())
        assertEquals(PetActivities.WORK, listOf(scan, repair, search).petWorkActivity())
        assertEquals(PetActivities.BOOK_SOURCE_REPAIR,
            listOf(search, repair.copy(state = AiChatTaskState.WORKING)).petWorkActivity())
    }

    @Test
    fun twoRequestsFinishInTheirOwnHistoriesAfterSwitchingAndClosingWindows() = runBlocking {
        val (selection, otherSelection) = configureModels("gpt-4o", "gpt-4o-mini")
        AiConfig.internalMcpEnabled = false
        val aStarted = CompletableDeferred<Unit>()
        val bStarted = CompletableDeferred<Unit>()
        val releaseA = CountDownLatch(1)
        val releaseB = CountDownLatch(1)
        val requestHistory = ConcurrentHashMap<String, List<String>>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8()).asJsonObject
            val history = json.getAsJsonArray("messages").filter { it.asJsonObject["role"].asString == "user" }
                .map { it.asJsonObject["content"].asString }
            val content = history.last()
            requestHistory[content] = history
            when (content) {
                "question A" -> {
                    aStarted.complete(Unit)
                    check(releaseA.await(30, TimeUnit.SECONDS))
                }
                "question B" -> {
                    bStarted.complete(Unit)
                    check(releaseB.await(30, TimeUnit.SECONDS))
                }
            }
            chain.jsonReply("""{"choices":[{"message":{"role":"assistant","content":"answer to $content"},"finish_reason":"stop"}]}""")
        }.build()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val persisted = ConcurrentHashMap<String, AiChatSessionSnapshot>()
        val coordinator = AiChatSessions(scope, AiChatClient { http }) { snapshot -> persisted[snapshot.id] = snapshot }
        fun begin(id: String, question: String, modelSelection: AiModelSelection): AiChatSessionRuntime {
            return coordinator.openSession(id).also { it.ask(coordinator.client, question, modelSelection) }
        }

        suspend fun awaitRequest(session: AiChatSessionRuntime, started: CompletableDeferred<Unit>) {
            withTimeoutOrNull(20_000) {
                while (session.sending && !started.isCompleted) delay(10)
            }
            assertTrue(
                "${session.id} did not reach its model. Messages: ${session.messages.map { it.content }}",
                started.isCompleted,
            )
        }

        suspend fun awaitReply(session: AiChatSessionRuntime, reply: String) {
            withTimeoutOrNull(20_000) {
                while (session.sending || persisted[session.id]?.messages?.lastOrNull()?.content != reply) delay(10)
            }
            assertEquals("Unexpected reply in ${session.id}", reply, persisted[session.id]?.messages?.lastOrNull()?.content)
        }

        try {
            coordinator.attachWindow()
            val first = begin("first", "question A", selection)
            awaitRequest(first, aStarted)
            first.messages += ChatUiMessage(role = ChatRole.USER, content = "queued A", deliveryState = ChatDeliveryState.QUEUED)
            first.persist()?.await()
            coordinator.unbind(first)
            coordinator.detachWindow()
            assertTrue(first.sending)
            assertSame(first, coordinator.get(first.id))

            coordinator.attachWindow()
            assertTrue(AiConfig.saveAssistantModel(otherSelection.providerId, otherSelection.modelId))
            val second = begin("second", "question B", otherSelection)
            awaitRequest(second, bStarted)
            assertEquals(listOf(AiChatTaskState.THINKING, AiChatTaskState.THINKING), coordinator.tasks.value.map { it.state })
            releaseA.countDown()
            awaitReply(first, "answer to queued A")
            val finished = coordinator.tasks.value.single { it.sessionId == first.id }
            assertEquals(AiChatTaskState.COMPLETED, finished.state)
            assertEquals("answer to queued A", finished.replyPreview)
            assertEquals(PetActivities.WORK, coordinator.tasks.value.petWorkActivity())
            assertTrue(coordinator.takePetCompletion())
            assertFalse(coordinator.takePetCompletion())
            assertEquals(
                listOf("question A", "answer to question A", "queued A", "answer to queued A"),
                persisted.getValue(first.id).messages.map { it.content },
            )
            assertEquals(selection.modelId, persisted.getValue(first.id).messages.last().usageModelId)
            assertEquals(listOf("question A", "queued A"), requestHistory["queued A"])
            assertEquals(listOf("question B"), requestHistory["question B"])
            assertEquals(listOf("question B"), second.messages.filterNot { it.loading }.map { it.content })
            assertEquals(second.id, coordinator.selectedFor(generalEntry))

            coordinator.unbind(second)
            coordinator.detachWindow()
            releaseB.countDown()
            awaitReply(second, "answer to question B")
            coordinator.attachWindow()
            coordinator.bind(second)
            assertSame(second, coordinator.get(coordinator.selectedFor(generalEntry)))
            assertEquals(AiChatTaskState.COMPLETED, coordinator.tasks.value.single { it.sessionId == second.id }.state)
            assertNull(coordinator.tasks.value.petWorkActivity())
            assertTrue(coordinator.takePetCompletion())
            assertFalse(coordinator.takePetCompletion())
            coordinator.beginViewing(second.id)
            assertEquals(listOf(first.id), coordinator.tasks.value.map { it.sessionId })
            coordinator.select(null, first)
            assertEquals(second.id, coordinator.selectedFor(generalEntry))
            assertFalse(second.sending)
            assertEquals(listOf("question B", "answer to question B"), second.messages.map { it.content })
            assertEquals(otherSelection.modelId, persisted.getValue(second.id).messages.last().usageModelId)
        } finally {
            releaseA.countDown()
            releaseB.countDown()
            scope.cancel()
        }
    }

    @Test
    fun backgroundModelFailureStaysInTheTaskListUntilItsSessionIsViewed() = runBlocking {
        val selection = configureModels("gpt-4o").single()
        AiConfig.internalMcpEnabled = false
        val requestStarted = CompletableDeferred<Unit>()
        val finishRequest = CountDownLatch(1)
        val http = OkHttpClient.Builder().addInterceptor {
            requestStarted.complete(Unit)
            check(finishRequest.await(30, TimeUnit.SECONDS))
            throw IOException("Fixture connection lost")
        }.build()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val saved = ConcurrentHashMap<String, AiChatSessionSnapshot>()
        val coordinator = AiChatSessions(scope, AiChatClient { http }) { saved[it.id] = it }
        try {
            coordinator.attachWindow()
            val session = coordinator.openSession("failed-chat")
            session.ask(coordinator.client, "Ask while online", selection)
            withTimeoutOrNull(20_000) { requestStarted.await() }
            assertTrue(requestStarted.isCompleted)
            coordinator.unbind(session)
            coordinator.detachWindow()
            finishRequest.countDown()

            withTimeoutOrNull(20_000) {
                while (session.sending || saved[session.id]?.messages?.lastOrNull()?.meta != "error") delay(10)
            }
            val failedTask = coordinator.tasks.value.single()
            assertEquals(session.id, failedTask.sessionId)
            assertEquals(AiChatTaskState.FAILED, failedTask.state)
            assertTrue(failedTask.state.showsBadge)
            assertNull(failedTask.replyPreview)
            assertNull(coordinator.tasks.value.petWorkActivity())
            assertFalse(coordinator.takePetCompletion())
            assertTrue(saved.getValue(session.id).messages.last().content.contains("Fixture connection lost"))

            coordinator.beginViewing("another-session")
            assertEquals(listOf(failedTask), coordinator.tasks.value)
            coordinator.beginViewing(session.id)
            assertTrue(coordinator.tasks.value.isEmpty())
        } finally {
            finishRequest.countDown()
            scope.cancel()
        }
    }

    @Test
    fun disabledBookSourcesLeaveAFailedTaskWithTheSavedLookupReason() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        AiConfig.internalMcpEnabled = false
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val saved = ConcurrentHashMap<String, AiChatSessionSnapshot>()
        val coordinator = AiChatSessions(scope, writeSnapshot = { saved[it.id] = it })
        val mode = AgentModeRegistry.bookSearch
        val session = AiChatSessionRuntime(coordinator, mode, AgentModeEntryContext(contextId = mode.id), emptyList())
        val candidate = AiBookSearchCandidate("Example book", "Example author", AiBookSearchMedia.NOVEL)
        val message = ChatUiMessage(
            role = ChatRole.ASSISTANT,
            content = candidate.title,
            bookSearch = AiBookSearchReply(candidates = listOf(candidate)),
        )
        try {
            coordinator.attachWindow()
            coordinator.bind(session)
            session.messages += message
            session.launchBookSourceLookup(message.id, candidate)
            coordinator.unbind(session)
            coordinator.detachWindow()
            withTimeoutOrNull(20_000) {
                while (saved[session.id]?.messages?.lastOrNull()?.bookSearch?.lookup?.status != AiBookSourceStatus.DISABLED) {
                    delay(10)
                }
            }
            assertEquals(AiBookSourceStatus.DISABLED, saved.getValue(session.id).messages.last().bookSearch?.lookup?.status)
            assertEquals(AiChatTaskState.FAILED, coordinator.tasks.value.single().state)
            assertFalse(coordinator.takePetCompletion())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun writeApprovalWaitsInItsOwnSessionAcrossNavigation() = runBlocking {
        val selection = configureModels("gpt-4o").single()
        AiConfig.internalMcpEnabled = true
        AiConfig.operationPermissionMode = AiOperationPermissionMode.CONFIRM_WRITE
        val toolResult = CompletableDeferred<String>()
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val messages = JsonParser.parseString(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
                .asJsonObject.getAsJsonArray("messages")
            val tool = messages.firstOrNull { it.asJsonObject["role"].asString == "tool" }
            val reply = if (tool == null) {
                """{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                    {"id":"write_1","type":"function","function":{"name":"mcp__legado__bookshelf_group_upsert","arguments":"{\"name\":\"Approval fixture\"}"}}
                ]},"finish_reason":"tool_calls"}]}"""
            } else {
                toolResult.complete(tool.asJsonObject["content"].asString)
                """{"choices":[{"message":{"role":"assistant","content":"Write declined"},"finish_reason":"stop"}]}"""
            }
            chain.jsonReply(reply)
        }.build()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = AiChatSessions(scope, AiChatClient { http })
        try {
            coordinator.attachWindow()
            val first = coordinator.openSession("approval")
            first.enabledMcpCapabilityIds += "bookshelf.manage_groups"
            first.ask(coordinator.client, "Create a test group", selection)
            withTimeoutOrNull(20_000) {
                while (first.sending && first.pendingApproval == null) delay(10)
            }
            assertTrue("No approval: ${first.messages.map { it.content }}", first.pendingApproval != null)
            val approval = requireNotNull(first.pendingApproval)
            assertEquals("bookshelf_group_upsert", approval.calls.single().toolName)
            assertEquals(AiChatTaskState.CONFIRM_TOOL, coordinator.tasks.value.single().state)
            coordinator.unbind(first)
            coordinator.detachWindow()

            coordinator.attachWindow()
            val second = coordinator.openSession("unrelated")
            assertNull(second.pendingApproval)
            assertFalse(approval.response.isCompleted)
            assertFalse(toolResult.isCompleted)

            coordinator.unbind(second)
            coordinator.bind(first)
            coordinator.select(generalEntry, first)
            coordinator.beginViewing(first.id)
            assertSame(approval, first.pendingApproval)
            approval.response.complete(false)
            withTimeoutOrNull(20_000) {
                while (first.sending) delay(10)
            }
            assertFalse(first.sending)
            assertNull(first.pendingApproval)
            assertTrue(coordinator.tasks.value.isEmpty())
            assertFalse(coordinator.takePetCompletion())
            assertTrue(toolResult.isCompleted)
            assertTrue(JsonParser.parseString(toolResult.await()).asJsonObject["canceled"].asBoolean)
            val saved = coordinator.readHistory { AiChatHistoryStore.loadSession(first.id) }
            assertEquals(listOf("Create a test group", "Write declined"), saved?.messages?.map { it.content })
            assertTrue(second.messages.isEmpty())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun backgroundTaskShowsTheSearchWhileTheSearchRequestRuns() = runBlocking {
        val selection = configureModels("gpt-4o").single()
        AiConfig.internalMcpEnabled = false
        val previous = AiExternalSearchStore.current()
        val searchEntered = CompletableDeferred<Unit>()
        val continueSearch = CountDownLatch(1)
        val nextReplyEntered = CompletableDeferred<Unit>()
        val continueReply = CountDownLatch(1)
        val searchHttp = OkHttpClient.Builder().addInterceptor { chain ->
            searchEntered.complete(Unit)
            check(continueSearch.await(30, TimeUnit.SECONDS))
            chain.jsonReply("""{"results":[{"url":"https://example.org/book","title":"Publisher","summary":"Book update"}]}""")
        }.build()
        val chatHttp = OkHttpClient.Builder().addInterceptor { chain ->
            val messages = JsonParser.parseString(Buffer().also { chain.request().body!!.writeTo(it) }.readUtf8())
                .asJsonObject.getAsJsonArray("messages")
            val reply = if (messages.any { it.asJsonObject["role"].asString == "tool" }) {
                nextReplyEntered.complete(Unit)
                check(continueReply.await(30, TimeUnit.SECONDS))
                """{"choices":[{"message":{"role":"assistant","content":"**Book update found**"},"finish_reason":"stop"}]}"""
            } else {
                """{"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
                    {"id":"search_1","type":"function","function":{"name":"search_web","arguments":"{\"query\":\"book update\"}"}}
                ]},"finish_reason":"tool_calls"}]}"""
            }
            chain.jsonReply(reply)
        }.build()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = AiChatSessions(
            scope,
            AiChatClient(externalSearchClient = { AiExternalSearchClient(searchHttp) }) { chatHttp },
            writeSnapshot = {},
        )
        suspend fun expectStatus(status: AiChatTaskState) {
            withTimeoutOrNull(20_000) {
                while (coordinator.tasks.value.singleOrNull()?.state != status) delay(10)
            }
            assertEquals(status, coordinator.tasks.value.singleOrNull()?.state)
        }
        try {
            AiExternalSearchStore.update {
                AiExternalSearchSettings(
                    routing = AiWebRoutingMode.EXTERNAL,
                    selectedService = AiExternalSearchProvider.DEEPSEEK.id,
                    deepSeek = AiExternalSearchService(apiKey = "search-fixture", enabled = true),
                )
            }
            coordinator.attachWindow()
            val session = coordinator.openSession("search-progress")
            session.ask(coordinator.client, "Search the web for the latest book update", selection)
            coordinator.unbind(session)
            coordinator.detachWindow()
            withTimeoutOrNull(20_000) { searchEntered.await() }
            assertTrue(searchEntered.isCompleted)
            expectStatus(AiChatTaskState.WEB_SEARCH)
            continueSearch.countDown()
            withTimeoutOrNull(20_000) { nextReplyEntered.await() }
            assertTrue(nextReplyEntered.isCompleted)
            expectStatus(AiChatTaskState.THINKING)
            continueReply.countDown()
            expectStatus(AiChatTaskState.COMPLETED)
            assertEquals("Book update found", coordinator.tasks.value.single().replyPreview)
        } finally {
            continueSearch.countDown()
            continueReply.countDown()
            scope.cancel()
            AiExternalSearchStore.update { previous }
        }
    }

    @Test
    fun chapterChoicesRemainAvailableForTheSameBookAfterClosingTheWindow() = runBlocking {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coordinator = AiChatSessions(scope, writeSnapshot = {})
        val skill = AiChatInputAttachment(
            id = "skill.${AiSkillRegistry.SKILL_CHARACTER_CARD_GENERATE}",
            type = AiChatInputAttachmentType.SKILL,
            title = "Character cards",
            subtitle = "",
            prompt = "Character card test",
        )
        val rangeChoice = """
            Choose a scan range.
            ```legado-interaction
            {"version":1,"id":"scan_range","type":"single_choice","title":"Choose scan range",
             "options":[{"label":"First 100 chapters","value":"1-100"}],
             "submit":{"label":"Continue","prompt_template":"Scan {{value}}"}}
            ```
        """.trimIndent()

        suspend fun createWaitingSession(id: String, workKey: String): AiChatSessionRuntime {
            val entry = AgentModeEntryContext(
                contextId = CHARACTER_CARD_ENTRY_CONTEXT,
                payload = JsonObject().apply {
                    addProperty("work_key", workKey)
                    addProperty("book_name", "Same title")
                },
            )
            val session = coordinator.openSession(id, entry, listOf(skill))
            session.messages += ChatUiMessage(role = ChatRole.ASSISTANT, content = rangeChoice)
            session.persist()?.await()
            coordinator.unbind(session)
            return session
        }

        fun pendingTasksFor(workKey: String) = coordinator.tasks.value.filter {
            it.workKey == workKey && !it.state.isTerminal
        }

        try {
            coordinator.attachWindow()
            val first = createWaitingSession("first-range", "book-a")
            val second = createWaitingSession("second-range", "book-a")
            val other = createWaitingSession("other-range", "book-b")
            coordinator.detachWindow()

            val matches = pendingTasksFor("book-a")
            assertEquals(listOf(second.id, first.id), matches.map { it.sessionId })
            assertEquals(listOf(other.id), pendingTasksFor("book-b").map { it.sessionId })
            assertTrue(matches.all { it.actionTitle == "Choose scan range" && it.state.needsAction })
            assertNull(coordinator.tasks.value.petWorkActivity())
            assertFalse(coordinator.takePetCompletion())
            first.markInteractionResolved(first.messages.single().id, "scan_range", "First 100 chapters")
            assertEquals(listOf(second.id), pendingTasksFor("book-a").map { it.sessionId })
        } finally {
            scope.cancel()
        }
    }

    private fun configureModels(vararg modelIds: String): List<AiModelSelection> {
        ApplicationProvider.getApplicationContext<Application>().injectAsAppCtx()
        val models = modelIds.map { AiModelRegistry.enrich(AiModel(it)) }
        val fixture = AiProviderSetting(
            id = "session-test", type = AiProviderType.OPENAI, builtIn = false,
            name = "Session test", baseUrl = "http://127.0.0.1:1/v1", apiKey = "fixture-only",
        ).withFetchedModels(models)
        AiProviderStore.saveProviders(listOf(fixture))
        return models.map { AiModelSelection(fixture.id, it.id) }.also { selections ->
            val first = selections.first()
            assertTrue(AiConfig.saveAssistantModel(first.providerId, first.modelId))
        }
    }

    private fun AiChatSessions.openSession(
        id: String,
        entry: AgentModeEntryContext? = null,
        attachments: List<AiChatInputAttachment> = emptyList(),
    ): AiChatSessionRuntime {
        val identity = AgentModeIdentity(AgentModeRegistry.GENERAL_ID, AgentModeRegistry.GENERAL_REVISION)
        val mode = requireNotNull(AgentModeRegistry.resolve(identity))
        return AiChatSessionRuntime(this, mode, entry, attachments, id = id).also { session ->
            bind(session)
            select(generalEntry, session)
        }
    }

    private fun AiChatSessionRuntime.ask(client: AiChatClient, question: String, selection: AiModelSelection) {
        val user = ChatUiMessage(role = ChatRole.USER, content = question, deliveryState = ChatDeliveryState.IN_FLIGHT)
        messages += user
        uploadMessages += client.newUserMessage(question)
        sendCurrentMessages(listOf(user.id), selection)
    }

    private fun Interceptor.Chain.jsonReply(reply: String): Response {
        return Response.Builder().request(request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(reply.toResponseBody("application/json".toMediaType())).build()
    }
}
