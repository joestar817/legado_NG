package io.legado.app.help.ai

import com.google.gson.JsonObject
import io.legado.app.data.entities.BookCharacter
import io.legado.app.help.ai.runtime.ToolExecutionStatus
import io.legado.app.ui.book.character.BookCharacterFormValue
import io.legado.app.ui.book.character.parseCharacterAliases
import io.legado.app.ui.book.info.CharacterCardSelectionEntry
import io.legado.app.ui.book.read.CharacterCardSelection
import io.legado.app.ui.config.activeSkillSnapshot
import io.legado.app.ui.config.buildIntentContextInputAttachments
import io.legado.app.ui.config.buildModeSkillInputAttachments
import io.legado.app.ui.config.buildUserUploadContent
import io.legado.app.ui.config.characterCardPreviewRow
import io.legado.app.ui.config.displayContent
import io.legado.app.ui.config.resolveModeManagedCapabilities
import io.legado.app.web.mcp.McpInternalToolCatalog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal const val CHARACTER_CARD_INTRO_MAX_CHARS = 90
private const val CHARACTER_CARD_INTRO_REWRITE_TIMEOUT_MS = 20_000L
private const val CHARACTER_CARD_INTRO_RULES =
    "人物介绍写成一段人物小传，用两三句短句，通常50–80字，最多$CHARACTER_CARD_INTRO_MAX_CHARS 个字符。" +
        "先交代最能说明他是谁的身份，再写有依据的关键性格与动机。只在有必要时留一个有辨识度的关系或能力。" +
        "把具体事件当作判断依据。省略年份、履历、逐章经过、采样说明和立绘描述。信息少时可以更短。"

internal data class AiCharacterCardModel(val provider: AiProviderSetting, val model: AiModel)

internal fun AiProviderSetting.characterCardModel(modelId: String): AiModel? =
    availableCatalogModel(modelId)?.let(::effectiveModelCapabilities)
        ?.takeIf { it.isTextChatModel() && AiModelAbility.TOOL in it.safeAbilities() }

internal data class AiCharacterCardTool(
    val name: String,
    val status: ToolExecutionStatus?,
    val chapters: Set<Int>,
)

internal data class AiCharacterCardProgress(
    val tools: List<AiCharacterCardTool> = emptyList(),
    val usage: AiChatUsage? = null,
    val missingPromptTokens: Boolean = false,
    val missingCompletionTokens: Boolean = false,
) {
    val runningTool: String? get() = tools.lastOrNull { it.status == null }?.name

    fun interruptRunningTools(): AiCharacterCardProgress = if (runningTool == null) this else copy(
        tools = tools.map { tool ->
            if (tool.status == null) tool.copy(status = ToolExecutionStatus.CANCELED) else tool
        },
    )

    fun followedBy(next: AiCharacterCardProgress): AiCharacterCardProgress {
        val accumulator = AiChatUsageAccumulator().apply {
            usage?.let(::add)
            next.usage?.let(::add)
        }
        return AiCharacterCardProgress(
            tools = tools + next.tools,
            usage = accumulator.result(),
            missingPromptTokens = missingPromptTokens || next.missingPromptTokens,
            missingCompletionTokens = missingCompletionTokens || next.missingCompletionTokens,
        )
    }
}

internal class AiCharacterCardProgressTracker {
    private val calls = linkedMapOf<String, AiCharacterCardTool>()
    private val usage = AiChatUsageAccumulator()

    fun record(event: AiToolExecutionUpdate): AiCharacterCardProgress? {
        if (event.receipt == null && event.callId in calls) return null
        val tool = AiCharacterCardTool(
            name = event.toolName,
            status = event.receipt?.status,
            chapters = readCharacterCardChapters(event.toolName, event.result),
        )
        if (calls.put(event.callId, tool) == tool) return null
        return snapshot()
    }

    fun recordUsage(requestUsage: AiChatUsage?): AiCharacterCardProgress {
        usage.add(requestUsage)
        return snapshot()
    }

    private fun snapshot() = AiCharacterCardProgress(
        tools = calls.values.toList(),
        usage = usage.result(),
        missingPromptTokens = usage.hasMissingPromptTokens,
        missingCompletionTokens = usage.hasMissingCompletionTokens,
    )
}

internal data class AiCharacterCardPreview(
    val form: BookCharacterFormValue?,
    val explanation: String?,
    val introNeedsReview: Boolean,
)

/** Runs the regular Skill for the reader's single selection, up to its preview. */
internal class AiCharacterCardSession(
    private val entry: CharacterCardSelectionEntry,
    private val choice: AiCharacterCardModel,
    private val reasoning: AiReasoningLevel,
) {
    suspend fun generate(onProgress: (AiCharacterCardProgress) -> Unit): AiCharacterCardPreview {
        val tracker = AiCharacterCardProgressTracker()
        val client = AiChatClient(
            onModelRequestUsage = { onProgress(tracker.recordUsage(it)) },
            clientForTimeout = ::aiHttpClient,
        )
        val mode = AgentModeRegistry.general
        val skillId = AiSkillRegistry.SKILL_CHARACTER_CARD_GENERATE
        val attachments = buildModeSkillInputAttachments(mode, listOf(skillId))
        val skill = requireNotNull(attachments.singleOrNull()) { "未找到原角色卡 Skill" }
        val selection = buildIntentContextInputAttachments(listOf(entry.attachment))
        val messages = mutableListOf(client.newSystemMessage())
        AiChatContextManager.syncModeEntryContext(messages, entry.context)
        AiChatContextManager.syncActiveSkill(messages, skill.activeSkillSnapshot())
        messages += JsonObject().apply {
            addProperty("role", "system")
            addProperty(
                "content",
                "阅读器单选角色时，人物介绍的写法和篇幅以本条为准，其余按角色卡技能执行。" +
                    CHARACTER_CARD_INTRO_RULES,
            )
        }
        messages += client.newUserMessage(buildUserUploadContent(entry.prompt, selection))
        val level = reasoning.takeIf { AiModelAbility.REASONING in choice.model.safeAbilities() }
            ?: AiReasoningLevel.OFF
        val result = client.send(
            messages = messages,
            conversationId = UUID.randomUUID().toString(),
            turnId = UUID.randomUUID().toString(),
            skillId = skillId,
            skillRevision = skill.skillRuntimeRevision,
            skillContentHash = skill.skillContentHash,
            availableSkillIds = skill.availableSkillIds,
            memoryPolicy = mode.memoryPolicy,
            runtimeHooks = mode.runtimeHooks + skill.skillRuntime.runtimeHooks,
            enabledMcpCapabilityIds = resolveModeManagedCapabilities(mode, attachments),
            selection = AiModelSelection(choice.provider.id, choice.model.id),
            paramsOverride = { params ->
                params.copy(
                    enableThinking = level != AiReasoningLevel.OFF && level != AiReasoningLevel.AUTO,
                    disableThinking = level == AiReasoningLevel.OFF,
                    reasoningEffort = level.reasoningEffort,
                    networkLogSource = "AI角色卡",
                )
            },
            toolAllowed = { tool, arguments ->
                tool != "bookshelf_character_upsert" &&
                    tool != CODEX_IMAGE_TOOL &&
                    !McpInternalToolCatalog.requiresUserConfirmation(
                        tool,
                        argumentsDeclareWrite = arguments?.flag("write") == true ||
                            arguments?.flag("overwrite") == true,
                    )
            },
            toolResultForModel = AiCharacterCardToolResults::compact,
            onToolExecution = { event -> tracker.record(event)?.let(onProgress) },
        )
        val content = AiChatInteractionParser.parse(result.content).displayContent(result.content)
        val form = characterCardPreviewRow(content)?.let { row ->
            characterCardForm(row, entry.context.payload.stringOrNull("character_name").orEmpty())
        }
        val intro = form?.intro
        val conciseIntro = if (intro != null && intro.length > CHARACTER_CARD_INTRO_MAX_CHARS) {
            shortenIntro(client, intro)
        } else {
            intro
        }
        return AiCharacterCardPreview(
            form = form?.copy(intro = conciseIntro),
            explanation = content.trim().take(360).takeIf {
                form == null && it.isNotBlank() && !content.contains('|') && !content.contains("```")
            },
            introNeedsReview = intro != null && conciseIntro == null,
        )
    }

    private suspend fun shortenIntro(client: AiChatClient, intro: String): String? {
        return try {
            withTimeoutOrNull(CHARACTER_CARD_INTRO_REWRITE_TIMEOUT_MS) {
                val draft = JsonObject().apply { addProperty("draft", intro) }
                val result = client.generateText(
                    setting = choice.provider,
                    model = choice.model,
                    messages = listOf(
                        AiMessage(
                            AiMessage.Role.SYSTEM,
                            "把用户消息里的 draft 字段改为角色卡简介，只使用其中的信息。" +
                                CHARACTER_CARD_INTRO_RULES + "只输出简介正文。",
                        ),
                        AiMessage(AiMessage.Role.USER, draft.toString()),
                    ),
                    params = AiTextParams(maxTokens = 384, disableThinking = true, networkLogSource = "AI角色卡"),
                )
                validatedShortCharacterIntro(result)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.flag(name: String): Boolean =
        get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
}

internal fun characterCardForm(row: List<String>, selectedName: String): BookCharacterFormValue? {
    if (row.size != 6) return null
    val name = row[0].cardField()?.let(CharacterCardSelection::normalizeSelectedName) ?: return null
    val aliases = parseCharacterAliases(row[3].cardField().orEmpty()).mapNotNull { alias ->
        alias.cardField()?.let(CharacterCardSelection::normalizeSelectedName)
    }
    val selectedKey = CharacterCardSelection.unwrappedName(selectedName)
    if ((listOf(name) + aliases).none { it.equals(selectedKey, ignoreCase = true) }) return null
    val gender = when (row[1].cardField()?.lowercase()) {
        "男", BookCharacter.Gender.MALE -> BookCharacter.Gender.MALE
        "女", BookCharacter.Gender.FEMALE -> BookCharacter.Gender.FEMALE
        else -> BookCharacter.Gender.UNKNOWN
    }
    val tag = when (row[2].cardField()?.lowercase()) {
        "男主", BookCharacter.RoleTag.MALE_LEAD -> BookCharacter.RoleTag.MALE_LEAD
        "女主", BookCharacter.RoleTag.FEMALE_LEAD -> BookCharacter.RoleTag.FEMALE_LEAD
        "男配", BookCharacter.RoleTag.MALE_SUPPORT -> BookCharacter.RoleTag.MALE_SUPPORT
        "女配", BookCharacter.RoleTag.FEMALE_SUPPORT -> BookCharacter.RoleTag.FEMALE_SUPPORT
        "重要配角", "其他", BookCharacter.RoleTag.OTHER -> BookCharacter.RoleTag.OTHER
        "路人", BookCharacter.RoleTag.PASSERBY -> return null
        else -> BookCharacter.RoleTag.UNKNOWN
    }
    return BookCharacterFormValue(
        name = name,
        aliases = aliases.filterNot { it.equals(name, ignoreCase = true) },
        gender = gender,
        roleTag = tag,
        intro = row[4].cardField(),
        imagePrompt = row[5].cardField(),
    )
}

private val emptyCardFields = setOf("无", "未提及", "未明确", "不详", "暂无", "未知", "none", "n/a", "-", "—", "–")

private fun String.cardField(): String? =
    trim(' ', '*', '\u0060').takeIf { it.isNotBlank() && it.lowercase() !in emptyCardFields }

internal fun validatedShortCharacterIntro(result: AiTextResult): String? {
    if (result.finishReason?.lowercase() in setOf("length", "max_tokens", "content_filter", "refusal", "incomplete", "error")) {
        return null
    }
    return result.content.trim().cardField()?.takeIf { intro ->
        intro.length <= CHARACTER_CARD_INTRO_MAX_CHARS &&
            intro.none { it == '|' || it == '\n' || it == '\r' } &&
            !intro.startsWith("人物介绍") && !intro.startsWith("抱歉")
    }
}

internal fun readCharacterCardChapters(tool: String, result: JsonObject?): Set<Int> {
    val data = (result?.objectOrNull("structuredContent") ?: result)?.objectOrNull("normalized_data")
        ?: return emptySet()
    return when (tool) {
        "bookshelf_chapter_content_get" -> {
            val number = data.objectOrNull("chapter")?.intOrNull("chapter_number")
            if (number != null && number > 0 && !data.stringOrNull("content").isNullOrBlank()) {
                setOf(number)
            } else {
                emptySet()
            }
        }
        "bookshelf_text_window_get", "bookshelf_chapter_snippets_get" -> {
            data.arrayOrNull("chapters")?.mapNotNull { element ->
                val chapter = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                chapter.intOrNull("chapter_number")?.takeIf {
                    it > 0 && (chapter.intOrNull("included_chars") ?: 0) > 0
                }
            }?.toSet().orEmpty()
        }
        else -> emptySet()
    }
}
