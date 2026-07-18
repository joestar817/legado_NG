package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName

data class AiChatInteractionRender(
    @SerializedName("content")
    val content: String,
    @SerializedName("interactions")
    val interactions: List<AiChatInteraction>,
    @SerializedName("has_hidden_artifacts")
    val hasHiddenArtifacts: Boolean
)

data class AiChatInteraction(
    @SerializedName("version")
    val version: Int,
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: AiChatInteractionType,
    @SerializedName("title")
    val title: String,
    @SerializedName("description")
    val description: String,
    @SerializedName("options")
    val options: List<AiChatInteractionOption>,
    @SerializedName("items")
    val items: List<AiChatInteractionItem> = emptyList(),
    @SerializedName("submit")
    val submit: AiChatInteractionSubmit?,
    @SerializedName("cancel")
    val cancel: AiChatInteractionSubmit?,
    @SerializedName("skip")
    val skip: AiChatInteractionSubmit? = null,
    @SerializedName("memory_patch")
    val memoryPatch: AiChatInteractionMemoryPatch? = null
)

data class AiChatInteractionItem(
    @SerializedName("id")
    val id: String,
    @SerializedName("label")
    val label: String,
    @SerializedName("description")
    val description: String
)

data class AiChatInteractionOption(
    @SerializedName("label")
    val label: String,
    @SerializedName("value")
    val value: String,
    @SerializedName("description")
    val description: String
)

data class AiChatInteractionSubmit(
    @SerializedName("label")
    val label: String,
    @SerializedName("prompt_template")
    val promptTemplate: String
)

data class AiChatInteractionMemoryPatch(
    @SerializedName("operation")
    val operation: AiChatInteractionMemoryPatchOperation,
    @SerializedName("scope_type")
    val scopeType: String,
    @SerializedName("scope_key")
    val scopeKey: String,
    @SerializedName("domain")
    val domain: String,
    @SerializedName("memory_type")
    val memoryType: String,
    @SerializedName("title")
    val title: String,
    @SerializedName("content")
    val content: String,
    @SerializedName("map_field")
    val mapField: String,
    @SerializedName("value_field")
    val valueField: String,
    @SerializedName("identity_fields")
    val identityFields: List<String>,
    @SerializedName("base_data")
    val baseData: JsonObject,
    @SerializedName("on_base_mismatch")
    val onBaseMismatch: AiChatInteractionMemoryBaseMismatch
)

enum class AiChatInteractionMemoryPatchOperation {
    @SerializedName("json_map_merge")
    JSON_MAP_MERGE
}

enum class AiChatInteractionMemoryBaseMismatch {
    @SerializedName("reject")
    REJECT,
    @SerializedName("replace")
    REPLACE
}

enum class AiChatInteractionType {
    @SerializedName("policy_ref")
    POLICY_REF,

    @SerializedName("actions")
    ACTIONS,
    @SerializedName("single_choice")
    SINGLE_CHOICE,
    @SerializedName("multi_choice")
    MULTI_CHOICE,
    @SerializedName("multi_tag_stance")
    MULTI_TAG_STANCE,
    @SerializedName("confirm")
    CONFIRM
}

object AiChatInteractionParser {

    private val blockRegex = Regex(
        pattern = """(?s)```[ \t]*legado-interaction[^\r\n]*\r?\n(.*?)\r?\n[ \t]*```"""
    )
    private val xmlBlockRegex = Regex(
        pattern = """(?s)(?:```[ \t]*text[^\r\n]*\r?\n[ \t]*)?<legado-interaction>\s*(.*?)\s*</legado-interaction>[ \t]*(?:\r?\n```)?"""
    )
    private val hiddenBlockRegex = AiLegacyArtifactRegistry.completedBlockRegex
    private val partialHiddenBlockRegex = AiLegacyArtifactRegistry.partialBlockRegex
    private val partialBlockRegex = Regex(
        pattern = """(?s)```[ \t]*legado-interaction[^\r\n]*(?:\r?\n.*)?$"""
    )

    fun parse(content: String): AiChatInteractionRender {
        val fencedBlocks = findParsedBlocks(content, blockRegex)
        val fencedInteractions = fencedBlocks.map { it.interaction }
        val contentWithoutFencedBlocks = removeRanges(content, fencedBlocks.map { it.range })
        val xmlBlocks = findParsedBlocks(contentWithoutFencedBlocks, xmlBlockRegex)
        val xmlInteractions = xmlBlocks.map { it.interaction }
        val contentWithoutCompletedBlocks = removeRanges(
            contentWithoutFencedBlocks,
            xmlBlocks.map { it.range }
        )
        val bareBlocks = findBareInteractionBlocks(contentWithoutCompletedBlocks)
        val bareInteractions = bareBlocks.map { it.interaction }
        val contentWithoutBareBlocks = removeRanges(
            contentWithoutCompletedBlocks,
            bareBlocks.map { it.range }
        )
        val partialInteraction = partialBlockRegex.find(contentWithoutCompletedBlocks)
            ?.let { match -> parsePartialInteraction(match.value) }
        val interactions = fencedInteractions + xmlInteractions + bareInteractions +
                listOfNotNull(partialInteraction)
        val visibleContentBase = if (partialInteraction != null) {
            partialBlockRegex.replace(contentWithoutBareBlocks, "")
        } else {
            contentWithoutBareBlocks
        }
        val hasHiddenArtifacts = hiddenBlockRegex.containsMatchIn(visibleContentBase) ||
            partialHiddenBlockRegex.containsMatchIn(visibleContentBase)
        val visibleContent = partialHiddenBlockRegex.replace(
            hiddenBlockRegex.replace(visibleContentBase, ""),
            ""
        )
            .trim()
        return AiChatInteractionRender(
            content = visibleContent,
            interactions = interactions,
            hasHiddenArtifacts = hasHiddenArtifacts
        )
    }

    fun buildPrompt(
        interaction: AiChatInteraction,
        option: AiChatInteractionOption? = null,
        options: List<AiChatInteractionOption> = emptyList(),
        submit: AiChatInteractionSubmit? = interaction.submit
    ): String {
        val selectedOptions = if (options.isNotEmpty()) options else listOfNotNull(option)
        val labels = selectedOptions.joinToString("、") { it.label }
        val values = selectedOptions.joinToString("、") { it.value }
        val template = submit?.promptTemplate
            ?.takeIf { it.isNotBlank() }
            ?: when {
                option != null -> "我选择：{{label}}"
                selectedOptions.isNotEmpty() -> "我选择：{{labels}}"
                else -> submit?.label?.takeIf { it.isNotBlank() } ?: "确认"
            }
        return template
            .replace("{{interaction_id}}", interaction.id)
            .replace("{{label}}", option?.label ?: labels)
            .replace("{{value}}", option?.value ?: values)
            .replace("{{labels}}", labels)
            .replace("{{values}}", values)
            .trim()
            .ifBlank { labels.ifBlank { submit?.label.orEmpty() }.ifBlank { "确认" } }
    }

    private fun parseInteraction(text: String): AiChatInteraction? {
        val trimmed = text.trim()
        if (trimmed.startsWith("<")) {
            return parseXmlPolicyReference(trimmed)
        }
        val root = runCatching {
            JsonParser.parseString(trimmed).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull() ?: return null
        if (root.stringValue("type").isBlank()) {
            return parsePolicyReference(root)
        }
        val version = root.intValue("version") ?: 1
        val type = when (root.stringValue("type").lowercase()) {
            "actions" -> AiChatInteractionType.ACTIONS
            "single_choice" -> AiChatInteractionType.SINGLE_CHOICE
            "multi_choice" -> AiChatInteractionType.MULTI_CHOICE
            "multi_tag_stance" -> AiChatInteractionType.MULTI_TAG_STANCE
            "confirm" -> AiChatInteractionType.CONFIRM
            else -> return null
        }
        if (type == AiChatInteractionType.MULTI_TAG_STANCE && version != 2) return null
        val id = root.stringValue("id").ifBlank { type.name.lowercase() }
        val rawOptions = root.arrayOrNull("options")
        val hasConflictingOptionAliases = rawOptions?.any { item ->
            val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@any false
            val value = obj.stringValue("value")
            val legacyId = obj.stringValue("id")
            value.isNotBlank() && legacyId.isNotBlank() && value != legacyId
        } == true
        if (hasConflictingOptionAliases) return null
        val parsedOptions = rawOptions
            ?.mapNotNull { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val label = obj.stringValue("label")
                if (label.isBlank()) return@mapNotNull null
                AiChatInteractionOption(
                    label = label,
                    value = obj.stringValue("value")
                        .ifBlank { obj.stringValue("id") }
                        .ifBlank { label },
                    description = obj.stringValue("description")
                )
            }
            .orEmpty()
        val options = if (type == AiChatInteractionType.MULTI_TAG_STANCE) {
            parsedOptions.map { option ->
                option.copy(label = STANCE_LABELS[option.value] ?: option.label)
            }
        } else {
            parsedOptions
        }
        val rawItems = root.arrayOrNull("items")
        val items = rawItems
            ?.mapNotNull { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val itemId = obj.stringValue("id")
                val label = obj.stringValue("label")
                val description = obj.stringValue("description")
                if (!itemId.matches(ITEM_ID_REGEX) || label.isBlank() || description.isBlank()) {
                    return@mapNotNull null
                }
                AiChatInteractionItem(
                    id = itemId,
                    label = label,
                    description = description
                )
            }
            .orEmpty()
        val submit = root.objectOrNull("submit")?.toSubmit(defaultLabel = "确认")
        val skip = root.objectOrNull("skip")?.toSubmit(defaultLabel = "暂不设置")
        val memoryPatch = root.objectOrNull("memory_patch")?.toMemoryPatch()
        if (type == AiChatInteractionType.MULTI_TAG_STANCE) {
            val optionValues = options.map { it.value }
            if (
                !id.matches(ITEM_ID_REGEX) ||
                items.size !in 1..MAX_MULTI_TAG_ITEMS ||
                rawItems?.size() != items.size ||
                items.map { it.id }.distinct().size != items.size ||
                options.size != STANCE_VALUES.size ||
                rawOptions?.size() != options.size ||
                optionValues.toSet() != STANCE_VALUES ||
                optionValues.distinct().size != optionValues.size ||
                submit == null ||
                skip == null ||
                memoryPatch == null
            ) {
                return null
            }
        }
        return AiChatInteraction(
            version = version,
            id = id,
            type = type,
            title = root.stringValue("title"),
            description = root.stringValue("description"),
            options = options,
            items = items,
            submit = submit,
            cancel = root.objectOrNull("cancel")?.toSubmit(defaultLabel = "取消"),
            skip = skip,
            memoryPatch = memoryPatch
        )
    }

    /**
     * Minimal host-owned interaction protocol. The model only chooses a declared interaction id
     * and, for dynamic catalog interactions, item ids. All other fields are hydrated by policy.
     */
    private fun parsePolicyReference(root: JsonObject): AiChatInteraction? {
        val id = root.stringValue("id")
        if (!id.matches(ITEM_ID_REGEX)) return null
        val rawItemIds = root.arrayOrNull("item_ids")
        val itemIds = rawItemIds?.mapNotNull { element ->
            element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
                ?.trim()
                ?.takeIf(ITEM_ID_REGEX::matches)
        }.orEmpty()
        if (
            rawItemIds != null && rawItemIds.size() != itemIds.size ||
            itemIds.size > MAX_MULTI_TAG_ITEMS ||
            itemIds.distinct().size != itemIds.size
        ) {
            return null
        }
        return AiChatInteraction(
            version = 1,
            id = id,
            type = AiChatInteractionType.POLICY_REF,
            title = "",
            description = "",
            options = emptyList(),
            items = itemIds.map { itemId ->
                AiChatInteractionItem(
                    id = itemId,
                    label = "",
                    description = ""
                )
            },
            submit = null,
            cancel = null,
            skip = null,
            memoryPatch = null
        )
    }

    private fun parseXmlPolicyReference(text: String): AiChatInteraction? {
        val tag = xmlPolicyReferenceRegex.find(text)?.value ?: return null
        val id = tag.xmlInteractionAttribute("id")
        if (!id.matches(ITEM_ID_REGEX)) return null
        val itemIds = tag.xmlInteractionAttribute("item-ids")
            .ifBlank { tag.xmlInteractionAttribute("item_ids") }
            .split(',', '，', ' ', '\n', '\t')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (
            itemIds.size > MAX_MULTI_TAG_ITEMS ||
            itemIds.distinct().size != itemIds.size ||
            itemIds.any { !it.matches(ITEM_ID_REGEX) }
        ) {
            return null
        }
        return AiChatInteraction(
            version = 1,
            id = id,
            type = AiChatInteractionType.POLICY_REF,
            title = "",
            description = "",
            options = emptyList(),
            items = itemIds.map { itemId ->
                AiChatInteractionItem(
                    id = itemId,
                    label = "",
                    description = ""
                )
            },
            submit = null,
            cancel = null,
            skip = null,
            memoryPatch = null
        )
    }

    private fun findParsedBlocks(content: String, regex: Regex): List<ParsedInteractionBlock> {
        return regex.findAll(content).mapNotNull { match ->
            parseInteraction(match.groupValues[1])?.let { interaction ->
                ParsedInteractionBlock(range = match.range, interaction = interaction)
            }
        }.toList()
    }

    private fun parsePartialInteraction(text: String): AiChatInteraction? {
        val body = text.substringAfter('\n', missingDelimiterValue = "").trim()
        if (body.isBlank()) return null
        return parseInteraction(body)
    }

    private fun findBareInteractionBlocks(content: String): List<BareInteractionBlock> {
        val result = mutableListOf<BareInteractionBlock>()
        var objectStart = -1
        var depth = 0
        var inString = false
        var escaped = false
        content.forEachIndexed { index, char ->
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                return@forEachIndexed
            }
            when (char) {
                '"' -> if (depth > 0) inString = true
                '{' -> {
                    if (depth == 0) objectStart = index
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        val range = objectStart..index
                        parseInteraction(content.substring(range))?.let { interaction ->
                            result += BareInteractionBlock(range, interaction)
                        }
                        objectStart = -1
                    }
                }
            }
        }
        return result
    }

    private fun removeRanges(content: String, ranges: List<IntRange>): String {
        if (ranges.isEmpty()) return content
        val result = StringBuilder(content)
        ranges.asReversed().forEach { range ->
            result.delete(range.first, range.last + 1)
        }
        return result.toString()
    }

    private data class BareInteractionBlock(
        val range: IntRange,
        val interaction: AiChatInteraction
    )

    private data class ParsedInteractionBlock(
        val range: IntRange,
        val interaction: AiChatInteraction
    )

    private fun JsonObject.toSubmit(defaultLabel: String): AiChatInteractionSubmit {
        return AiChatInteractionSubmit(
            label = stringValue("label").ifBlank { defaultLabel },
            promptTemplate = stringValue("prompt_template")
        )
    }

    private fun JsonObject.toMemoryPatch(): AiChatInteractionMemoryPatch? {
        val operation = when (stringValue("operation").lowercase()) {
            "json_map_merge" -> AiChatInteractionMemoryPatchOperation.JSON_MAP_MERGE
            else -> return null
        }
        val scopeType = stringValue("scope_type")
        val scopeKey = stringValue("scope_key")
        val domain = stringValue("domain")
        val memoryType = stringValue("memory_type")
        val title = stringValue("title")
        val content = stringValue("content")
        val mapField = stringValue("map_field")
        val valueField = stringValue("value_field").ifBlank { "value" }
        val rawIdentityFields = arrayOrNull("identity_fields")
        val identityFields = rawIdentityFields
            ?.mapNotNull { value ->
                value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?.trim()
                    ?.takeIf(FIELD_NAME_REGEX::matches)
            }
            .orEmpty()
        val baseData = get("base_data")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.deepCopy()
            ?: return null
        val onBaseMismatch = when (stringValue("on_base_mismatch").lowercase()) {
            "replace" -> AiChatInteractionMemoryBaseMismatch.REPLACE
            "", "reject" -> AiChatInteractionMemoryBaseMismatch.REJECT
            else -> return null
        }
        if (
            scopeType.isBlank() ||
            scopeKey.isBlank() ||
            domain.isBlank() ||
            memoryType.isBlank() ||
            title.isBlank() ||
            content.isBlank() ||
            !mapField.matches(FIELD_NAME_REGEX) ||
            !valueField.matches(FIELD_NAME_REGEX) ||
            identityFields.isEmpty() ||
            rawIdentityFields?.size() != identityFields.size ||
            identityFields.distinct().size != identityFields.size ||
            identityFields.any { !baseData.has(it) } ||
            baseData.get(mapField)?.takeIf { it.isJsonObject } == null
        ) {
            return null
        }
        return AiChatInteractionMemoryPatch(
            operation = operation,
            scopeType = scopeType,
            scopeKey = scopeKey,
            domain = domain,
            memoryType = memoryType,
            title = title,
            content = content,
            mapField = mapField,
            valueField = valueField,
            identityFields = identityFields,
            baseData = baseData,
            onBaseMismatch = onBaseMismatch
        )
    }

    private fun JsonObject.stringValue(name: String): String {
        return get(name)
            ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            .orEmpty()
            .trim()
    }

    private fun JsonObject.intValue(name: String): Int? {
        return runCatching {
            get(name)?.takeIf { it.isJsonPrimitive }?.asInt
        }.getOrNull()
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? {
        return get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun JsonObject.arrayOrNull(name: String) =
        get(name)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun String.xmlInteractionAttribute(name: String): String {
        return Regex("""\b${Regex.escape(name)}\s*=\s*["']([^"']*)["']""")
            .find(this)
            ?.groupValues
            ?.get(1)
            ?.trim()
            .orEmpty()
    }

    private const val MAX_MULTI_TAG_ITEMS = 5
    private val STANCE_VALUES = setOf("like", "neutral", "avoid")
    private val STANCE_LABELS = mapOf(
        "like" to "更偏好这类作品",
        "neutral" to "可以接受",
        "avoid" to "会因此劝退"
    )
    private val ITEM_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$")
    private val FIELD_NAME_REGEX = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
    private val xmlPolicyReferenceRegex = Regex("""(?is)<interaction\b[^>]*>""")
}

/**
 * 将同一 Agent turn 内各次 assistant 输出按顺序组成一个可见结果。
 * 每一步都是权威 part；后续短收尾不能覆盖先前已经生成的正文。
 */
internal object AiChatTurnContent {

    fun append(current: String, next: String): String {
        val existing = current.trim()
        val incoming = next.trim()
        if (incoming.isBlank()) return existing
        if (existing.isBlank()) return incoming
        if (incoming == existing) return existing
        if (incoming.contains(existing)) return incoming
        if (existing.contains(incoming)) return existing
        return "$existing\n\n$incoming"
    }
}

/**
 * Keeps provider transport text and tool-step narration out of the authoritative user reply.
 * The rule is intentionally independent of any Skill or MCP tool name.
 */
internal object AiChatToolStepContent {

    private val providerProtocolMarkers = listOf("<｜DSML｜", "<|DSML|")
    private const val MIN_RECOVERABLE_CONTENT_LENGTH = 80

    fun containsProviderProtocol(content: String): Boolean {
        return providerProtocolMarkers.any(content::contains)
    }

    fun providerProjection(content: String): String {
        val markerIndex = providerProtocolMarkers
            .map(content::indexOf)
            .filter { index -> index >= 0 }
            .minOrNull()
            ?: return content
        return content.substring(0, markerIndex).trimEnd()
    }

    fun recoveryCandidate(content: String): String? {
        if (containsProviderProtocol(content)) return null
        val visible = AiChatInteractionParser.parse(content).content
        return content.trim().takeIf { visible.length >= MIN_RECOVERABLE_CONTENT_LENGTH }
    }

    fun bestRecoveryCandidate(candidates: Collection<String>): String? {
        return candidates.maxByOrNull { content ->
            AiChatInteractionParser.parse(content).content.length
        }
    }
}

/**
 * Tool-capable models sometimes emit a complete visible answer before their last tool call,
 * then finish with only a transport artifact or a short lead-in plus that artifact. Preserve
 * the visible answer in that generic case instead of letting the final carrier replace it.
 */
object AiChatVisibleContentRecovery {

    private const val MIN_RECOVERABLE_CONTENT_LENGTH = 80
    private const val MAX_ARTIFACT_LEAD_IN_LENGTH = 80
    private const val MIN_CONTENT_TO_LEAD_IN_RATIO = 8
    private const val MAX_INTERNAL_RETRY_LEAD_IN_LENGTH = 240
    private const val MIN_INTERNAL_RETRY_CONTENT_RATIO = 2

    fun recover(finalContent: String, earlierContent: String): String? {
        val finalRender = AiChatInteractionParser.parse(finalContent)
        val earlierRender = AiChatInteractionParser.parse(earlierContent)
        if (earlierRender.content.length < MIN_RECOVERABLE_CONTENT_LENGTH) return null
        if (finalContent.isBlank()) return earlierContent.trim()
        val hasTerminalArtifact = finalRender.interactions.isNotEmpty() || finalRender.hasHiddenArtifacts
        if (!hasTerminalArtifact) return null
        val finalIsOnlyArtifact = finalRender.content.isBlank()
        val finalIsShortArtifactLeadIn = finalRender.content.length <= MAX_ARTIFACT_LEAD_IN_LENGTH &&
            earlierRender.content.length >= finalRender.content.length * MIN_CONTENT_TO_LEAD_IN_RATIO
        if (!finalIsOnlyArtifact && !finalIsShortArtifactLeadIn) return null
        return buildString {
            append(earlierRender.content.trim())
            append("\n\n")
            append(finalContent.trim())
        }
    }

    /**
     * A finalize hook retry is an internal state-flush attempt, not a new user request. If the
     * retry only returns a short completion claim, keep the substantial answer that triggered
     * the retry instead of replacing it with the claim.
     */
    fun recoverAfterInternalRetry(finalContent: String, earlierContent: String): String? {
        recover(finalContent, earlierContent)?.let { return it }
        val finalVisibleLength = AiChatInteractionParser.parse(finalContent).content.length
        val earlierVisibleLength = AiChatInteractionParser.parse(earlierContent).content.length
        val earlierIsSubstantial = earlierVisibleLength >= MIN_RECOVERABLE_CONTENT_LENGTH
        val finalIsShort = finalVisibleLength <= MAX_INTERNAL_RETRY_LEAD_IN_LENGTH
        val earlierIsMateriallyLonger = earlierVisibleLength >=
            finalVisibleLength.coerceAtLeast(1) * MIN_INTERNAL_RETRY_CONTENT_RATIO
        return earlierContent.trim().takeIf {
            earlierIsSubstantial && finalIsShort && earlierIsMateriallyLonger
        }
    }

    fun recoverFromUploadHistory(
        finalContent: String,
        uploadMessages: List<JsonObject>
    ): String? {
        val lastUserIndex = uploadMessages.indexOfLast { message ->
            message.get("role")?.asString == "user"
        }
        val earlierContent = uploadMessages.asSequence()
            .drop(lastUserIndex + 1)
            .filter { message -> message.get("role")?.asString == "assistant" }
            .mapNotNull { message -> message.get("content")?.takeIf { !it.isJsonNull }?.asString }
            .filter { content -> content != finalContent }
            .maxByOrNull { content -> AiChatInteractionParser.parse(content).content.length }
            ?: return null
        return recover(finalContent, earlierContent)
    }
}
