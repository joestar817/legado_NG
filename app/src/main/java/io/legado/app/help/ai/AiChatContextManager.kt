package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlin.math.ceil

object AiChatContextManager {

    const val SUMMARY_PREFIX = "[AI_CONTEXT_COMPACTION_SUMMARY]"
    const val ATTACHMENT_PREAMBLE =
        "以下是用户在输入框中可见并随本次消息附带的 App 上下文与 Skill 说明。" +
            "这些内容用于帮助你理解场景，不要在回复中逐字复述；用户可随时移除它们。\n\n"
    const val APP_CONTEXT_HEADING = "## App 上下文\n"
    const val SKILL_HEADING = "## 已加载 Skill\n"
    const val USER_MESSAGE_HEADING = "## 用户消息\n"
    private const val MAX_RECENT_USER_TOKENS = 20_000

    fun usage(
        messages: List<JsonObject>,
        toolDefinitions: List<JsonObject> = emptyList(),
        calibration: AiChatTokenCalibration? = null
    ): AiChatContextUsage {
        val contextWindow = AiConfig.assistantContextWindowTokens
        val thresholdPercent = AiConfig.contextCompactionThresholdPercent
        val threshold = if (thresholdPercent == 0) {
            contextWindow
        } else {
            contextWindow * thresholdPercent / 100
        }
        val breakdown = breakdown(messages, toolDefinitions)
        val localEstimatedTokens = breakdown.totalTokens
        return AiChatContextUsage(
            estimatedTokens = calibration?.estimate(localEstimatedTokens)
                ?: localEstimatedTokens,
            localEstimatedTokens = localEstimatedTokens,
            contextWindowTokens = contextWindow,
            thresholdTokens = threshold,
            compactionThresholdPercent = thresholdPercent,
            breakdown = breakdown,
            calibration = calibration
        )
    }

    fun calibrationFromHistory(
        messages: List<JsonObject>,
        toolDefinitions: List<JsonObject>,
        promptTokens: Int,
        providerId: String,
        modelId: String,
        expectedToolCallCount: Int = 0
    ): AiChatTokenCalibration? {
        if (promptTokens <= 0) return null
        if (messages.any { it.isCompactionSummary() }) return null
        val actualToolCallCount = messages.sumOf { message ->
            message.getAsJsonArray("tool_calls")?.size() ?: 0
        }
        if (actualToolCallCount < expectedToolCallCount) return null
        val anchorIndex = messages.indexOfLast { message ->
            message.role() == "assistant" && !message.has("tool_calls")
        }
        if (anchorIndex <= 0) return null
        val localPromptTokens = breakdown(
            messages = messages.take(anchorIndex),
            toolDefinitions = toolDefinitions
        ).totalTokens
        return AiChatTokenCalibration.create(
            contextTokens = promptTokens,
            localContextTokens = localPromptTokens,
            providerId = providerId,
            modelId = modelId
        )
    }

    fun trimOldestCompactionHistoryUnit(messages: MutableList<JsonObject>): Boolean {
        if (messages.isEmpty()) return false
        val firstUserIndex = messages.indexOfFirst { it.role() == "user" }
        if (firstUserIndex >= 0) {
            val nextUserIndex = (firstUserIndex + 1 until messages.size)
                .firstOrNull { messages[it].role() == "user" }
            if (nextUserIndex != null) {
                repeat(nextUserIndex) { messages.removeAt(0) }
                return true
            }
            if (firstUserIndex > 0) {
                repeat(firstUserIndex) { messages.removeAt(0) }
                return true
            }
            return false
        }

        val removed = messages.removeAt(0)
        val toolCallIds = removed.toolCallIds()
        if (toolCallIds.isNotEmpty()) {
            messages.removeAll { message ->
                message.role() == "tool" && message.stringContent("tool_call_id") in toolCallIds
            }
        } else if (removed.role() == "tool") {
            val toolCallId = removed.stringContent("tool_call_id")
            val assistantIndex = messages.indexOfFirst { toolCallId in it.toolCallIds() }
            if (assistantIndex >= 0) {
                val pairedIds = messages[assistantIndex].toolCallIds()
                messages.removeAt(assistantIndex)
                messages.removeAll { message ->
                    message.role() == "tool" && message.stringContent("tool_call_id") in pairedIds
                }
            }
        }
        return true
    }

    fun shrinkLargestCompactionMessage(messages: MutableList<JsonObject>): Boolean {
        val target = messages
            .mapNotNull { message ->
                message.get("content")
                    ?.takeIf { it.isJsonPrimitive }
                    ?.asString
                    ?.let { content -> message to content }
            }
            .maxByOrNull { (_, content) -> content.length }
            ?: return false
        val (message, content) = target
        if (content.length < MIN_COMPACTION_SHRINK_CHARS) return false
        val retainedLength = content.length / 2
        val headLength = retainedLength / 2
        val tailLength = retainedLength - headLength
        message.addProperty(
            "content",
            content.take(headLength) + COMPACTION_TRUNCATION_MARKER + content.takeLast(tailLength)
        )
        return true
    }

    fun breakdown(
        messages: List<JsonObject>,
        toolDefinitions: List<JsonObject> = emptyList()
    ): AiChatContextBreakdown {
        var systemPromptTokens = 0
        var toolTokens = 0
        var skillTokens = 0
        var appContextTokens = 0
        var conversationTokens = 0
        var protocolTokens = 0

        messages.forEach { message ->
            val fullTokens = estimateTokens(listOf(message))
            val role = message.role()
            if (role == "tool" || message.has("tool_calls")) {
                toolTokens += fullTokens
                return@forEach
            }
            if (role == "system") {
                val promptTokens = estimateTextTokens(message.contentText())
                systemPromptTokens += promptTokens
                protocolTokens += (fullTokens - promptTokens).coerceAtLeast(0)
                return@forEach
            }
            if (role == "user") {
                val sections = splitUserContent(message.contentText())
                val conversation = estimateOptionalTextTokens(sections.conversation)
                val skill = estimateOptionalTextTokens(sections.skill)
                val appContext = estimateOptionalTextTokens(sections.appContext)
                conversationTokens += conversation
                skillTokens += skill
                appContextTokens += appContext
                protocolTokens += (fullTokens - conversation - skill - appContext).coerceAtLeast(0)
                return@forEach
            }
            val contentTokens = estimateOptionalTextTokens(message.contentText())
            val reasoningTokens = estimateOptionalTextTokens(message.stringContent("reasoning_content"))
            conversationTokens += contentTokens + reasoningTokens
            protocolTokens += (fullTokens - contentTokens - reasoningTokens).coerceAtLeast(0)
        }
        if (toolDefinitions.isNotEmpty()) {
            toolTokens += estimateTextTokens(JsonArray().apply {
                toolDefinitions.forEach { definition ->
                    add(definition.deepCopy())
                }
            }.toString())
        }
        return AiChatContextBreakdown(
            systemPromptTokens = systemPromptTokens,
            toolTokens = toolTokens,
            skillTokens = skillTokens,
            appContextTokens = appContextTokens,
            conversationTokens = conversationTokens,
            protocolTokens = protocolTokens
        )
    }

    fun estimateTokens(messages: List<JsonObject>): Int {
        return messages.sumOf { message ->
            estimateTextTokens(message.toString()) + MESSAGE_OVERHEAD_TOKENS
        }
    }

    fun estimateTextTokens(text: String): Int {
        var weightedUnits = 0.0
        text.forEach { char ->
            weightedUnits += when {
                char.isCjkLike() -> 1.0
                char.isWhitespace() -> 0.15
                else -> 0.25
            }
        }
        return ceil(weightedUnits).toInt().coerceAtLeast(1)
    }

    fun shouldCompact(
        messages: List<JsonObject>,
        toolDefinitions: List<JsonObject> = emptyList(),
        calibration: AiChatTokenCalibration? = null
    ): Boolean {
        val usage = usage(messages, toolDefinitions, calibration)
        return AiConfig.contextCompactionEnabled && usage.estimatedTokens >= usage.thresholdTokens
    }

    fun buildTranscript(messages: List<JsonObject>): String {
        return buildString {
            messages.forEachIndexed { index, message ->
                append("\n--- message ")
                append(index + 1)
                append(" ---\n")
                append(message.toString())
                append('\n')
            }
        }.trim()
    }

    fun historyForRegeneration(
        messages: List<JsonObject>,
        targetRole: String,
        targetContent: String
    ): List<JsonObject>? {
        val anchorIndex = when (targetRole) {
            "user" -> messages.indexOfLast { message ->
                message.role() == "user" && message.contentText() == targetContent
            }

            "assistant" -> {
                val assistantIndex = messages.indexOfLast { message ->
                    message.role() == "assistant" &&
                        !message.has("tool_calls") &&
                        message.contentText() == targetContent
                }
                if (assistantIndex < 0) {
                    -1
                } else {
                    (assistantIndex - 1 downTo 0).firstOrNull { index ->
                        messages[index].role() == "user"
                    } ?: -1
                }
            }

            else -> -1
        }
        if (anchorIndex < 0) return null
        return messages.take(anchorIndex + 1).map { it.deepCopy().asJsonObject }
    }

    fun buildCompactedHistory(
        messages: List<JsonObject>,
        summary: String,
        recentUserTokenBudget: Int = minOf(
            MAX_RECENT_USER_TOKENS,
            (AiConfig.assistantContextWindowTokens * 0.2f).toInt()
        )
    ): List<JsonObject> {
        val systems = messages
            .filter { it.role() == "system" }
            .map { it.deepCopy().asJsonObject }
        var used = 0
        val recentUsers = mutableListOf<JsonObject>()
        messages.asReversed().forEach { message ->
            if (message.role() != "user" || message.isCompactionSummary()) return@forEach
            val tokens = estimateTokens(listOf(message))
            if (tokens > recentUserTokenBudget ||
                used + tokens > recentUserTokenBudget
            ) return@forEach
            recentUsers += message.deepCopy().asJsonObject
            used += tokens
        }
        return buildList {
            addAll(systems)
            add(JsonObject().apply {
                addProperty("role", "user")
                addProperty(
                    "content",
                    "$SUMMARY_PREFIX revision=${compactionRevision(messages) + 1}\n${summary.trim()}"
                )
            })
            addAll(recentUsers.asReversed())
        }
    }

    fun compactionRevision(messages: List<JsonObject>): Int {
        return messages.asReversed().firstNotNullOfOrNull { message ->
            val content = message.get("content")
                ?.takeIf { it.isJsonPrimitive }
                ?.asString
                ?: return@firstNotNullOfOrNull null
            if (!content.startsWith(SUMMARY_PREFIX)) return@firstNotNullOfOrNull null
            REVISION_REGEX.find(content)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        } ?: 0
    }

    private fun JsonObject.role(): String {
        return get("role")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }

    private fun JsonObject.contentText(): String = stringContent("content")

    private fun JsonObject.toolCallIds(): Set<String> {
        return getAsJsonArray("tool_calls")
            ?.mapNotNull { call ->
                call.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.stringContent("id")
                    ?.takeIf { it.isNotBlank() }
            }
            ?.toSet()
            .orEmpty()
    }

    private fun JsonObject.stringContent(key: String): String {
        val value = get(key) ?: return ""
        return if (value.isJsonPrimitive) value.asString else value.toString()
    }

    private fun estimateOptionalTextTokens(text: String): Int {
        return if (text.isBlank()) 0 else estimateTextTokens(text)
    }

    private fun splitUserContent(content: String): UserContentSections {
        if (!content.startsWith(ATTACHMENT_PREAMBLE)) {
            return UserContentSections(conversation = content)
        }
        val appContextStart = content.indexOf(APP_CONTEXT_HEADING)
        val skillStart = content.indexOf(SKILL_HEADING)
        val userStart = content.indexOf(USER_MESSAGE_HEADING)

        fun section(start: Int, marker: String, vararg followingStarts: Int): String {
            if (start < 0) return ""
            val contentStart = start + marker.length
            val end = followingStarts.filter { it > contentStart }.minOrNull() ?: content.length
            return content.substring(contentStart, end).trim()
        }
        return UserContentSections(
            appContext = section(appContextStart, APP_CONTEXT_HEADING, skillStart, userStart),
            skill = section(skillStart, SKILL_HEADING, userStart),
            conversation = section(userStart, USER_MESSAGE_HEADING)
        )
    }

    private fun JsonObject.isCompactionSummary(): Boolean {
        val content = get("content")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        return content.startsWith(SUMMARY_PREFIX)
    }

    private fun Char.isCjkLike(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private const val MESSAGE_OVERHEAD_TOKENS = 12
    private const val MIN_COMPACTION_SHRINK_CHARS = 4_096
    private const val COMPACTION_TRUNCATION_MARKER =
        "\n\n[较早的超长内容已在压缩请求中省略]\n\n"
    private val REVISION_REGEX = Regex("^\\[AI_CONTEXT_COMPACTION_SUMMARY] revision=(\\d+)")

    private data class UserContentSections(
        val conversation: String,
        val skill: String = "",
        val appContext: String = ""
    )
}

data class AiChatContextBreakdown(
    val systemPromptTokens: Int,
    val toolTokens: Int,
    val skillTokens: Int,
    val appContextTokens: Int,
    val conversationTokens: Int,
    val protocolTokens: Int
) {
    val totalTokens: Int
        get() = systemPromptTokens + toolTokens + skillTokens + appContextTokens +
            conversationTokens + protocolTokens
}

data class AiChatContextUsage(
    val estimatedTokens: Int,
    val localEstimatedTokens: Int,
    val contextWindowTokens: Int,
    val thresholdTokens: Int,
    val compactionThresholdPercent: Int,
    val breakdown: AiChatContextBreakdown,
    val calibration: AiChatTokenCalibration? = null
) {
    val percent: Float
        get() = (estimatedTokens.toFloat() / contextWindowTokens.toFloat()).coerceAtLeast(0f)
}

data class AiChatTokenCalibration(
    val contextTokens: Int,
    val localContextTokens: Int,
    val providerId: String,
    val modelId: String
) {

    fun estimate(localTokens: Int): Int {
        if (localTokens <= 0) return 0
        // The provider usage is an exact anchor for the already-sent payload.
        // Only locally added content is estimated before the next request.
        return (contextTokens + (localTokens - localContextTokens).coerceAtLeast(0))
            .coerceAtLeast(1)
    }

    fun matches(providerId: String, modelId: String): Boolean {
        return this.providerId == providerId && this.modelId == modelId
    }

    companion object {
        fun create(
            contextTokens: Int,
            localContextTokens: Int,
            providerId: String,
            modelId: String
        ): AiChatTokenCalibration? {
            if (contextTokens <= 0 || localContextTokens <= 0) return null
            return AiChatTokenCalibration(
                contextTokens = contextTokens,
                localContextTokens = localContextTokens,
                providerId = providerId,
                modelId = modelId
            )
        }
    }
}

data class AiChatPromptUsageAnchor(
    val promptTokens: Int,
    val localPromptTokens: Int? = null,
    val contextTokens: Int? = null,
    val localContextTokens: Int? = null,
    val providerId: String? = null,
    val modelId: String? = null,
    val expectedToolCallCount: Int = 0
)

enum class AiChatCompactionStage {
    PRE_TURN,
    MID_TURN
}

enum class AiChatContextEventType {
    STARTED,
    COMPLETED
}

data class AiChatContextEvent(
    val type: AiChatContextEventType,
    val stage: AiChatCompactionStage,
    val beforeTokens: Int,
    val beforeUsage: AiChatContextUsage? = null,
    val afterTokens: Int? = null,
    val afterUsage: AiChatContextUsage? = null,
    val compaction: AiChatCompactionRecord? = null
)

data class AiChatCompactionRecord(
    val beforeTokens: Int,
    val afterTokens: Int,
    val revision: Int,
    val summaryPromptTokens: Int = 0,
    val summaryCompletionTokens: Int = 0
)
