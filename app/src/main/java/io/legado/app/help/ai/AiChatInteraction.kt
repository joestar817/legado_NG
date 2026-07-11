package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser

data class AiChatInteractionRender(
    val content: String,
    val interactions: List<AiChatInteraction>
)

data class AiChatInteraction(
    val id: String,
    val type: AiChatInteractionType,
    val title: String,
    val description: String,
    val options: List<AiChatInteractionOption>,
    val submit: AiChatInteractionSubmit?,
    val cancel: AiChatInteractionSubmit?
)

data class AiChatInteractionOption(
    val label: String,
    val value: String,
    val description: String
)

data class AiChatInteractionSubmit(
    val label: String,
    val promptTemplate: String
)

enum class AiChatInteractionType {
    ACTIONS,
    SINGLE_CHOICE,
    MULTI_CHOICE,
    CONFIRM
}

object AiChatInteractionParser {

    private val blockRegex = Regex(
        pattern = """(?s)```[ \t]*legado-interaction[^\r\n]*\r?\n(.*?)\r?\n```"""
    )
    private val hiddenBlockRegex = Regex(
        pattern = """(?s)```[ \t]*(?:legado-character-scan|character_scan_meta|legado-book-scan|book_scan_delta)[^\r\n]*\r?\n.*?\r?\n```"""
    )
    private val partialHiddenBlockRegex = Regex(
        pattern = """(?s)```[ \t]*(?:legado-character-scan|character_scan_meta|legado-book-scan|book_scan_delta)[^\r\n]*(?:\r?\n.*)?$"""
    )
    private val partialBlockRegex = Regex(
        pattern = """(?s)```[ \t]*legado-interaction[^\r\n]*(?:\r?\n.*)?$"""
    )

    fun parse(content: String): AiChatInteractionRender {
        val completedInteractions = blockRegex.findAll(content)
            .mapNotNull { match -> parseInteraction(match.groupValues[1]) }
            .toList()
        val contentWithoutCompletedBlocks = blockRegex.replace(content, "")
        val partialInteraction = partialBlockRegex.find(contentWithoutCompletedBlocks)
            ?.let { match -> parsePartialInteraction(match.value) }
        val interactions = completedInteractions + listOfNotNull(partialInteraction)
        val visibleContentBase = if (partialInteraction != null) {
            partialBlockRegex.replace(contentWithoutCompletedBlocks, "")
        } else {
            contentWithoutCompletedBlocks
        }
        val visibleContent = partialHiddenBlockRegex.replace(
            hiddenBlockRegex.replace(visibleContentBase, ""),
            ""
        )
            .trim()
        return AiChatInteractionRender(
            content = visibleContent,
            interactions = interactions
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
        val root = runCatching {
            JsonParser.parseString(text.trim()).takeIf { it.isJsonObject }?.asJsonObject
        }.getOrNull() ?: return null
        val type = when (root.stringValue("type").lowercase()) {
            "actions" -> AiChatInteractionType.ACTIONS
            "single_choice" -> AiChatInteractionType.SINGLE_CHOICE
            "multi_choice" -> AiChatInteractionType.MULTI_CHOICE
            "confirm" -> AiChatInteractionType.CONFIRM
            else -> return null
        }
        val id = root.stringValue("id").ifBlank { type.name.lowercase() }
        val options = root.getAsJsonArray("options")
            ?.mapNotNull { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val label = obj.stringValue("label")
                if (label.isBlank()) return@mapNotNull null
                AiChatInteractionOption(
                    label = label,
                    value = obj.stringValue("value").ifBlank { label },
                    description = obj.stringValue("description")
                )
            }
            .orEmpty()
        return AiChatInteraction(
            id = id,
            type = type,
            title = root.stringValue("title"),
            description = root.stringValue("description"),
            options = options,
            submit = root.objectOrNull("submit")?.toSubmit(defaultLabel = "确认"),
            cancel = root.objectOrNull("cancel")?.toSubmit(defaultLabel = "取消")
        )
    }

    private fun parsePartialInteraction(text: String): AiChatInteraction? {
        val body = text.substringAfter('\n', missingDelimiterValue = "").trim()
        if (body.isBlank()) return null
        return parseInteraction(body)
    }

    private fun JsonObject.toSubmit(defaultLabel: String): AiChatInteractionSubmit {
        return AiChatInteractionSubmit(
            label = stringValue("label").ifBlank { defaultLabel },
            promptTemplate = stringValue("prompt_template")
        )
    }

    private fun JsonObject.stringValue(name: String): String {
        return get(name)?.takeIf { !it.isJsonNull }?.asString.orEmpty().trim()
    }

    private fun JsonObject.objectOrNull(name: String): JsonObject? {
        return get(name)?.takeIf { it.isJsonObject }?.asJsonObject
    }
}
