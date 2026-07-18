package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.data.appDb
import io.legado.app.data.entities.AgentMemory
import io.legado.app.web.mcp.McpToolExecutionContext
import java.util.UUID

data class AiChatInteractionMemoryWriteResult(
    val memoryId: String?,
    val created: Boolean,
    val displayText: String,
    val continuationPrompt: String
)

/**
 * Applies a user-confirmed interaction patch without knowing any business tag id.
 * Missing item ids remain unknown; existing unselected map entries remain untouched.
 */
internal object AiChatInteractionMemoryMerge {

    fun merge(
        interaction: AiChatInteraction,
        existingDataJson: String?,
        selectedValues: Map<String, String>
    ): JsonObject {
        require(interaction.type == AiChatInteractionType.MULTI_TAG_STANCE) {
            "memory patch is only supported for multi_tag_stance interactions"
        }
        require(selectedValues.isNotEmpty()) { "at least one explicit selection is required" }
        val patch = requireNotNull(interaction.memoryPatch) {
            "multi_tag_stance interaction is missing memory_patch"
        }
        require(patch.operation == AiChatInteractionMemoryPatchOperation.JSON_MAP_MERGE) {
            "unsupported memory patch operation: ${patch.operation}"
        }

        val itemsById = interaction.items.associateBy { it.id }
        val optionsByValue = interaction.options.associateBy { it.value }
        require(selectedValues.keys.all(itemsById::containsKey)) {
            "selection contains an item that is not declared by the interaction"
        }
        require(selectedValues.values.all(optionsByValue::containsKey)) {
            "selection contains a value that is not declared by the interaction"
        }

        val existingRoot = existingDataJson?.let(::parseExistingRoot)
        val root = when {
            existingRoot == null -> patch.baseData.deepCopy()
            patch.identityFields.all { field ->
                existingRoot.get(field) == patch.baseData.get(field)
            } -> existingRoot.deepCopy()
            patch.onBaseMismatch == AiChatInteractionMemoryBaseMismatch.REPLACE -> {
                patch.baseData.deepCopy()
            }
            else -> throw IllegalArgumentException(
                "existing memory base does not match the interaction contract"
            )
        }

        val currentMap = root.get(patch.mapField)
        require(currentMap != null && currentMap.isJsonObject) {
            "memory map field ${patch.mapField} must be a JSON object"
        }
        val targetMap = currentMap.asJsonObject
        selectedValues.forEach { (itemId, selectedValue) ->
            val item = requireNotNull(itemsById[itemId])
            val option = requireNotNull(optionsByValue[selectedValue])
            targetMap.add(itemId, JsonObject().apply {
                addProperty(patch.valueField, selectedValue)
                addProperty("source_kind", "ui_choice")
                addProperty("source_text", "${item.label}：${option.label}")
                addProperty("confirmed_by_user", true)
            })
        }
        return root
    }

    private fun parseExistingRoot(dataJson: String): JsonObject {
        require(dataJson.isNotBlank()) { "existing memory data_json must not be blank" }
        return runCatching {
            JsonParser.parseString(dataJson).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull()?.deepCopy()
            ?: throw IllegalArgumentException("existing memory data_json must be a JSON object")
    }
}

/**
 * Trusted host-side writer for structured interaction results.
 *
 * Authorization is intentionally fail-closed: only the exact pinned, read-only System Workflow
 * of the active Agent Mode can use this path, and the target must be inside that Mode's memory
 * policy. User-editable Skills and General Mode cannot obtain a direct memory write through it.
 */
object AiChatInteractionMemoryWriter {

    fun write(
        interaction: AiChatInteraction,
        selectedValues: Map<String, String>,
        agentMode: AgentModeDefinition,
        activeSkill: AiActiveSkillSnapshot,
        conversationId: String,
        turnId: String
    ): AiChatInteractionMemoryWriteResult {
        return write(
            interaction = interaction,
            selectedValues = selectedValues,
            agentMode = agentMode,
            activeSkill = activeSkill,
            conversationId = conversationId,
            turnId = turnId,
            dependencies = productionDependencies
        )
    }

    fun skipped(interaction: AiChatInteraction): AiChatInteractionMemoryWriteResult {
        require(interaction.type == AiChatInteractionType.MULTI_TAG_STANCE) {
            "skip result is only supported for multi_tag_stance interactions"
        }
        return AiChatInteractionMemoryWriteResult(
            memoryId = null,
            created = false,
            displayText = interaction.skip?.label?.takeIf { it.isNotBlank() } ?: "暂不设置",
            continuationPrompt = buildContinuationPrompt(
                interactionId = interaction.id,
                status = "skipped",
                selectedValues = emptyMap()
            )
        )
    }

    internal fun write(
        interaction: AiChatInteraction,
        selectedValues: Map<String, String>,
        agentMode: AgentModeDefinition,
        activeSkill: AiActiveSkillSnapshot,
        conversationId: String,
        turnId: String,
        dependencies: AiChatInteractionMemoryWriterDependencies
    ): AiChatInteractionMemoryWriteResult {
        require(dependencies.memoryEnabled()) { "AI assistant memory is disabled" }
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(turnId.isNotBlank()) { "turnId must not be blank" }
        val patch = requireNotNull(interaction.memoryPatch) {
            "multi_tag_stance interaction is missing memory_patch"
        }
        val executionContext = trustedExecutionContext(
            agentMode = agentMode,
            activeSkill = activeSkill,
            conversationId = conversationId,
            turnId = turnId,
            resolveSystemSkill = dependencies.resolveSystemSkill
        )
        executionContext.requireMemoryAccess(
            scopeType = patch.scopeType,
            scopeKey = patch.scopeKey,
            domain = patch.domain
        )
        dependencies.validateInteraction(agentMode, interaction)

        return dependencies.store.writeTransaction {
            val existing = dependencies.store.findLatest(patch)
            val mergedData = AiChatInteractionMemoryMerge.merge(
                interaction = interaction,
                existingDataJson = existing?.dataJson,
                selectedValues = selectedValues
            )
            val now = dependencies.now()
            val id = existing?.id ?: dependencies.newId()
            dependencies.store.upsert(
                AgentMemory(
                    id = id,
                    scopeType = patch.scopeType,
                    scopeKey = patch.scopeKey,
                    subject = existing?.subject.orEmpty(),
                    domain = patch.domain,
                    memoryType = patch.memoryType,
                    title = patch.title,
                    content = patch.content,
                    dataJson = mergedData.toString(),
                    tags = existing?.tags.orEmpty(),
                    confidence = 1f,
                    source = "user_confirmed",
                    status = "active",
                    createdAt = existing?.createdAt?.takeIf { it > 0 } ?: now,
                    updatedAt = now
                )
            )
            AiChatInteractionMemoryWriteResult(
                memoryId = id,
                created = existing == null,
                displayText = "已保存 ${selectedValues.size} 项阅读偏好",
                continuationPrompt = buildContinuationPrompt(
                    interactionId = interaction.id,
                    status = "saved",
                    selectedValues = selectedValues
                )
            )
        }
    }

    private fun trustedExecutionContext(
        agentMode: AgentModeDefinition,
        activeSkill: AiActiveSkillSnapshot,
        conversationId: String,
        turnId: String,
        resolveSystemSkill: (AgentModeDefinition, String) -> AiSkillDefinition?
    ): McpToolExecutionContext {
        require(agentMode.kind == AgentModeKind.SYSTEM) {
            "host interaction memory writes require System Mode"
        }
        require(!agentMode.allowsUserSkills && agentMode.systemWorkflowId == activeSkill.skillId) {
            "active Skill is not the System Workflow pinned by the Agent Mode"
        }
        val memoryPolicy = requireNotNull(agentMode.memoryPolicy) {
            "System Mode does not declare a memory policy"
        }
        val trustedSkill = requireNotNull(resolveSystemSkill(agentMode, activeSkill.skillId)) {
            "pinned System Workflow cannot be resolved from read-only resources"
        }
        require(trustedSkill.builtIn && !trustedSkill.userModified) {
            "host interaction memory writes require an unmodified built-in System Workflow"
        }
        require(activeSkill.revision.isNotBlank() && activeSkill.revision == trustedSkill.revision) {
            "pinned System Workflow revision does not match the read-only resource"
        }
        require(
            activeSkill.contentHash.isNotBlank() &&
                activeSkill.contentHash == trustedSkill.contentHash
        ) {
            "pinned System Workflow content hash does not match the read-only resource"
        }
        require(activeSkill.runtime == trustedSkill.runtime) {
            "pinned System Workflow runtime declaration does not match the read-only resource"
        }
        val expectedRuntimeRevision = buildRuntimeRevisionToken(
            skillId = trustedSkill.id,
            revision = trustedSkill.revision,
            contentHash = trustedSkill.contentHash
        )
        require(
            activeSkill.runtimeRevision.isNotBlank() &&
                activeSkill.runtimeRevision == expectedRuntimeRevision
        ) {
            "pinned System Workflow runtime revision does not match the read-only resource"
        }
        return McpToolExecutionContext(
            conversationId = conversationId,
            turnId = turnId,
            skillRevision = trustedSkill.revision,
            skillContentHash = trustedSkill.contentHash,
            allowedMemoryRanges = memoryPolicy.allowedRanges
        )
    }

    private fun buildContinuationPrompt(
        interactionId: String,
        status: String,
        selectedValues: Map<String, String>
    ): String {
        return JsonObject().apply {
            addProperty("type", "interaction_result")
            addProperty("interaction_id", interactionId)
            addProperty("status", status)
            add("selected", JsonArray().apply {
                selectedValues.forEach { (itemId, value) ->
                    add(JsonObject().apply {
                        addProperty("id", itemId)
                        addProperty("value", value)
                    })
                }
            })
        }.toString()
    }

    private fun buildRuntimeRevisionToken(
        skillId: String,
        revision: String,
        contentHash: String
    ): String {
        val id = skillId.trim().ifBlank { "skill" }
        val version = revision.trim().ifBlank { "unversioned" }
        return contentHash.trim().takeIf { it.isNotBlank() }
            ?.let { hash -> "$id@$version@$hash" }
            ?: "$id@$version"
    }

    private val productionDependencies: AiChatInteractionMemoryWriterDependencies
        get() = AiChatInteractionMemoryWriterDependencies(
            memoryEnabled = { AiConfig.memoryEnabled },
            store = AppDbInteractionMemoryStore,
            resolveSystemSkill = { mode, skillId ->
                val packageSetHash = AiSkillPackageRegistry
                    .systemPackageSet(mode.availableSystemSkillIds)
                    ?.contentHash
                if (packageSetHash == null) {
                    null
                } else {
                    AiSkillRegistry.systemWorkflow(skillId)?.copy(contentHash = packageSetHash)
                }
            },
            now = System::currentTimeMillis,
            newId = { UUID.randomUUID().toString() },
            validateInteraction = { mode, interaction ->
                AiChatInteractionPolicyRegistry.requireAllowed(
                    contract = AiChatInteractionPolicyRegistry.resolve(mode),
                    interaction = interaction
                )
            }
        )
}

internal data class AiChatInteractionMemoryWriterDependencies(
    val memoryEnabled: () -> Boolean,
    val store: AiChatInteractionMemoryStore,
    val resolveSystemSkill: (AgentModeDefinition, String) -> AiSkillDefinition?,
    val now: () -> Long,
    val newId: () -> String,
    val validateInteraction: (AgentModeDefinition, AiChatInteraction) -> Unit = { _, _ -> }
)

internal interface AiChatInteractionMemoryStore {
    fun writeTransaction(block: () -> AiChatInteractionMemoryWriteResult): AiChatInteractionMemoryWriteResult
    fun findLatest(patch: AiChatInteractionMemoryPatch): AgentMemory?
    fun upsert(memory: AgentMemory)
}

private object AppDbInteractionMemoryStore : AiChatInteractionMemoryStore {
    override fun writeTransaction(
        block: () -> AiChatInteractionMemoryWriteResult
    ): AiChatInteractionMemoryWriteResult {
        return appDb.runInTransaction<AiChatInteractionMemoryWriteResult> { block() }
    }

    override fun findLatest(patch: AiChatInteractionMemoryPatch): AgentMemory? {
        return appDb.agentMemoryDao.search(
            scopeType = patch.scopeType,
            scopeKey = patch.scopeKey,
            subject = "",
            domain = patch.domain,
            memoryType = patch.memoryType,
            keyword = "",
            status = "active",
            offset = 0,
            limit = 1
        ).firstOrNull()
    }

    override fun upsert(memory: AgentMemory) {
        appDb.agentMemoryDao.upsert(memory)
    }
}
