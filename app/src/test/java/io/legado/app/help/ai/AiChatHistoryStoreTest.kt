package io.legado.app.help.ai

import com.google.gson.JsonObject
import io.legado.app.data.dao.AiChatConversationMetadata
import io.legado.app.utils.GSON
import io.legado.app.help.ai.runtime.ToolApprovalStatus
import io.legado.app.help.ai.runtime.ToolExecutionReceipt
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatHistoryStoreTest {

    @Test
    fun bookSearchPrefillDoesNotBecomeAHistoryDraftUntilEdited() {
        assertNull(bookSearchDraftForHistory("搜索栏预填", hasMessages = false, userEdited = false))
        assertEquals("我记得男主的名字", bookSearchDraftForHistory(
            "我记得男主的名字", hasMessages = false, userEdited = true,
        ))
        assertNull(bookSearchDraftForHistory("  \n", hasMessages = false, userEdited = true))
        assertEquals("补充线索", bookSearchDraftForHistory("补充线索", hasMessages = true, userEdited = false))
    }

    @Test
    fun historySkipsEmptyEntriesBeforeApplyingItsLimit() {
        fun bookSearchDraft(value: String?) = AgentModeEntryContext(
            contextId = AgentModeRegistry.BOOK_SEARCH_ID,
            title = "AI 搜书",
            payload = JsonObject().apply {
                addProperty(AI_BOOK_SEARCH_SCOPE, "测试书源")
                if (value != null) addProperty(AI_BOOK_SEARCH_DRAFT, value)
            },
        ).toJson()

        val empty = AiChatConversationMetadata(
            id = "welcome-only",
            assistantId = AgentModeRegistry.BOOK_SEARCH_ID,
            agentModeRevision = "",
            modeEntryContext = bookSearchDraft(null),
            modeEntryStarted = false,
            title = "AI 搜书",
            createAt = 0,
            updateAt = 0,
            isPinned = false,
            customSystemPrompt = "",
            loadedSkillIds = "[]",
            enabledMcpCapabilityIds = "[]",
            webEnabled = true,
            hasMessageNodes = false,
        )
        val index = listOf(
            empty,
            empty.copy(id = "cleared-draft", modeEntryContext = bookSearchDraft("  ")),
            empty.copy(id = "sent", hasMessageNodes = true),
            empty.copy(id = "draft", modeEntryContext = bookSearchDraft("还有一句原文")),
            empty.copy(id = "started-workflow", modeEntryStarted = true),
        )

        val listed = selectChatHistoryMetadata(limit = 3) { limit, offset ->
            index.drop(offset).take(limit)
        }

        assertEquals(listOf("sent", "draft", "started-workflow"), listed.map { it.id })
    }

    @Test
    fun durableTurnCreatesRecoverableAuthoritativeAssistantNode() {
        val base = AiChatSessionSnapshot(
            id = "conversation-1",
            title = "测试",
            createdAt = 1,
            updatedAt = 1,
            messages = listOf(
                AiChatMessageSnapshot("user-1", "user", "执行任务")
            )
        )
        val context = listOf(JsonObject().apply {
            addProperty("role", "assistant")
            addProperty("content", "provider context")
        })
        val receipt = ToolExecutionReceipt(
            receiptId = "receipt-1",
            conversationId = "conversation-1",
            turnId = "user-1",
            stepId = "step-1",
            toolCallId = "call-1",
            toolName = "reader.read",
            argumentsHash = "args",
            resultHash = "result",
            status = ToolExecutionStatus.SUCCEEDED,
            approvalStatus = ToolApprovalStatus.NOT_REQUIRED
        )

        val merged = AiChatHistoryStore.mergeDurableTurn(
            baseSession = base,
            assistantMessageId = "assistant-draft",
            update = AiChatDurableContextUpdate(
                contextMessages = context,
                visibleContent = "## 完整报告\n\n正文",
                reasoning = "reasoning",
                toolReceipts = listOf(receipt),
                pendingReceiptIds = listOf("receipt-1")
            ),
            now = 2,
            modelSelection = AiModelSelection("deepseek", "deepseek-flash"),
        )

        val draft = merged.messages.last()
        assertEquals("assistant-draft", draft.id)
        assertEquals("## 完整报告\n\n正文", draft.content)
        assertEquals(AiChatMessageSnapshot.DELIVERY_IN_FLIGHT, draft.deliveryState)
        assertEquals("deepseek", draft.usageProviderId)
        assertEquals("deepseek-flash", draft.usageModelId)
        assertNull(draft.promptTokens)
        assertEquals(listOf(receipt), draft.toolReceipts)
        assertTrue(draft.meta.orEmpty().contains("未提交检查点"))
        assertEquals("provider context", merged.uploadMessages.single().get("content").asString)
    }

    @Test
    fun durableReplyStaysBeforeMessagesQueuedDuringTheTurn() {
        val base = AiChatSessionSnapshot(
            id = "conversation", title = "测试", createdAt = 1, updatedAt = 1,
            messages = listOf(
                AiChatMessageSnapshot("first", "user", "已发送"),
                AiChatMessageSnapshot("queued", "user", "排队中", deliveryState = AiChatMessageSnapshot.DELIVERY_QUEUED),
            ),
        )
        val merged = AiChatHistoryStore.mergeDurableTurn(
            base, "reply", AiChatDurableContextUpdate(emptyList(), "阶段结果", null),
            now = 2, assistantMessageIndex = 1,
        )

        assertEquals(listOf("first", "reply", "queued"), merged.messages.map { it.id })
        assertEquals(AiChatMessageSnapshot.DELIVERY_QUEUED, merged.messages.last().deliveryState)
    }

    @Test
    fun laterDurableUpdateReplacesDraftInsteadOfDuplicatingIt() {
        val existingDraft = AiChatMessageSnapshot("assistant-draft", "assistant", "旧正文")
        val base = AiChatSessionSnapshot(
            id = "conversation-1",
            title = "测试",
            createdAt = 1,
            updatedAt = 1,
            messages = listOf(existingDraft)
        )

        val merged = AiChatHistoryStore.mergeDurableTurn(
            baseSession = base,
            assistantMessageId = "assistant-draft",
            update = AiChatDurableContextUpdate(
                contextMessages = emptyList(),
                visibleContent = "新正文",
                reasoning = null
            ),
            now = 2
        )

        assertEquals(1, merged.messages.size)
        assertEquals("新正文", merged.messages.single().content)
    }

    @Test
    fun legacyRecoveryWorksForEarlierTurnWhenConversationHasLaterFailedUser() {
        val interactionOnly = """
            ```legado-interaction
            {"id":"next","type":"actions","title":"下一步","options":[]}
            ```
        """.trimIndent()
        val report = ("## 快速定位\n\n旧记录里的完整报告不能被吞。\n").repeat(8)
        val uiMessages = listOf(
            AiChatMessageSnapshot("user-1", "user", "扫描"),
            AiChatMessageSnapshot("assistant-1", "assistant", interactionOnly),
            AiChatMessageSnapshot("user-2", "user", "继续")
        )
        val uploads = listOf(
            jsonMessage("user", "扫描"),
            jsonMessage("assistant", report),
            jsonMessage("assistant", interactionOnly),
            jsonMessage("user", "继续")
        )

        val recovered = AiChatHistoryStore.recoverLegacyVisibleMessages(uiMessages, uploads)

        assertTrue(recovered[1].content.contains("旧记录里的完整报告不能被吞"))
        assertEquals("继续", recovered[2].content)
    }

    @Test
    fun legacyMessageWithoutToolReceiptsDoesNotCrashDuringRecovery() {
        val legacyMessage = GSON.fromJson(
            """{"id":"assistant-old","role":"assistant","content":"旧报告"}""",
            AiChatMessageSnapshot::class.java
        )

        val recovered = AiChatHistoryStore.recoverLegacyVisibleMessages(
            messages = listOf(legacyMessage),
            uploadMessages = emptyList()
        )

        assertEquals(emptyList<ToolExecutionReceipt>(), recovered.single().toolReceipts)
        assertEquals(emptyList<String>(), recovered.single().toolTrace)
        assertEquals(AiChatMessageSnapshot.DELIVERY_SENT, recovered.single().deliveryState)
    }

    @Test
    fun durableTurnPreservesModeEntryContextAndStartMarker() {
        val entryContext = AgentModeEntryContext(
            contextId = "book_detail",
            title = "AI 扫书：天之下",
            payload = JsonObject().apply { addProperty("work_key", "天之下\n空") }
        )
        val base = AiChatSessionSnapshot(
            id = "conversation-entry",
            title = entryContext.title,
            modeId = AgentModeRegistry.BOOK_SCAN_ID,
            modeRevision = AgentModeRegistry.BOOK_SCAN_REVISION,
            modeEntryContext = entryContext,
            modeEntryStarted = true,
            createdAt = 1,
            updatedAt = 1,
            messages = emptyList()
        )

        val merged = AiChatHistoryStore.mergeDurableTurn(
            baseSession = base,
            assistantMessageId = "assistant-entry",
            update = AiChatDurableContextUpdate(
                contextMessages = emptyList(),
                visibleContent = "快扫结果",
                reasoning = null
            ),
            now = 2
        )

        assertEquals(entryContext, merged.modeEntryContext)
        assertTrue(merged.modeEntryStarted)
    }

    @Test
    fun snapshotJsonRoundTripPreservesModeEntryState() {
        val entryContext = AgentModeEntryContext(
            contextId = "book_detail",
            title = "AI 扫书：天之下",
            payload = JsonObject().apply { addProperty("work_key", "天之下\n空") }
        )
        val expected = AiChatSessionSnapshot(
            id = "conversation-json",
            title = entryContext.title,
            modeId = AgentModeRegistry.BOOK_SCAN_ID,
            modeRevision = AgentModeRegistry.BOOK_SCAN_REVISION,
            modeEntryContext = entryContext,
            modeEntryStarted = true,
            createdAt = 1,
            updatedAt = 2,
            messages = emptyList()
        )

        val restored = GSON.fromJson(
            GSON.toJson(expected),
            AiChatSessionSnapshot::class.java
        )

        assertEquals(entryContext, restored.modeEntryContext?.validatedCopyOrNull())
        assertTrue(restored.modeEntryStarted)
    }

    @Test
    fun snapshotJsonRoundTripPreservesResolvedInteractionSelections() {
        val expected = AiChatMessageSnapshot(
            id = "assistant-preference",
            role = AiChatMessageSnapshot.ROLE_ASSISTANT,
            content = "报告",
            resolvedInteractionIds = listOf("reader_preference_adaptive_tags"),
            interactionResultLabels = mapOf(
                "reader_preference_adaptive_tags" to "已保存 2 项阅读偏好"
            ),
            interactionResultSelections = mapOf(
                "reader_preference_adaptive_tags" to mapOf(
                    "tone.dark_cruel" to "avoid",
                    "setting.system_present" to "like"
                )
            )
        )

        val restored = GSON.fromJson(
            GSON.toJson(expected),
            AiChatMessageSnapshot::class.java
        )

        assertEquals(expected.interactionResultSelections, restored.interactionResultSelections)
    }

    private fun jsonMessage(role: String, content: String) = JsonObject().apply {
        addProperty("role", role)
        addProperty("content", content)
    }
}
