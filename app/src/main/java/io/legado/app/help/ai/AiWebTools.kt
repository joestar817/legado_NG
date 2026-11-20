package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal enum class AiWebTool {
    SEARCH,
    READ_LINK,
}

/** A turn resolves to one provider for its web tools. Provider requests consume only Native. */
internal sealed interface AiWebToolPlan {
    data object Off : AiWebToolPlan

    data class Native(
        val definitions: List<JsonObject>,
        val systemInstruction: String,
    ) : AiWebToolPlan
}

internal class AiDeepSeekWebRoute(
    val endpoint: String,
    private val protocol: AiModelGenerationTestProtocol,
    private val flash: Boolean,
    val supported: Set<AiWebTool>,
    val enabled: Set<AiWebTool>,
) {
    fun plan(allowed: Boolean, userText: String, thinkingOff: Boolean): AiWebToolPlan {
        if (!allowed) return AiWebToolPlan.Off
        val readLink = AiWebTool.READ_LINK in enabled && WEB_URL.findAll(userText).any {
            it.value.trimEnd(')', ']', '}', ',', '.', '，', '。', '）', '】').toHttpUrlOrNull() != null
        }
        val tools = buildList {
            // In thinking mode, Flash search takes priority over its dedicated link reader.
            if (AiWebTool.SEARCH in enabled && (!readLink || thinkingOff)) add(AiWebTool.SEARCH)
            if (readLink) add(AiWebTool.READ_LINK)
        }
        if (tools.isEmpty()) return AiWebToolPlan.Off
        return AiWebToolPlan.Native(
            definitions = tools.map(::definition),
            systemInstruction = tools.joinToString("\n") { tool ->
                when (tool) {
                    AiWebTool.SEARCH ->
                        "本轮可以直接搜索公开网页，无需 Legado MCP。用户询问最新消息、当前状态或公开连载的更新时，" +
                            "先用内置网页搜索核实，再依据找到的来源回答。查不到可靠来源时，如实说明。"
                    AiWebTool.READ_LINK ->
                        "本轮可以读取用户发来的网页链接。用户贴链接或询问该链接的内容时，" +
                            "先读取网页，再根据页面内容回答。读取失败时，如实说明。"
                }
            },
        )
    }

    private fun definition(tool: AiWebTool) = JsonObject().apply {
        val type = when {
            tool == AiWebTool.READ_LINK -> "deepseek_read_url"
            flash -> "deepseek_web_search"
            protocol == AiModelGenerationTestProtocol.CLAUDE_MESSAGES -> "web_search_20250305"
            else -> "web_search"
        }
        addProperty("type", type)
        if (protocol == AiModelGenerationTestProtocol.CLAUDE_MESSAGES) {
            addProperty("name", if (flash) type else "web_search")
        }
    }
}

internal fun AiProviderSetting.deepSeekWebRoute(
    model: AiModel,
    generation: AiModelGenerationRoute = resolveModelGenerationRoute(model),
): AiDeepSeekWebRoute? {
    if (model.type != AiModelType.CHAT || generation.issue != null) return null
    if (builtIn && id != "deepseek") return null
    if (baseUrl.trim().toHttpUrlOrNull()?.isDeepSeekApi() != true) return null

    val protocol = generation.protocol
    val endpoint = when (protocol) {
        AiModelGenerationTestProtocol.CLAUDE_MESSAGES -> runCatching {
            buildAiApiEndpoint(baseUrl, "/messages").toHttpUrlOrNull()
        }.getOrNull()
        else -> generationUrl(protocol)
    } ?: return null
    if (!endpoint.isDeepSeekApi() || endpoint.query != null || endpoint.fragment != null) return null
    val allowedPath = when (protocol) {
        AiModelGenerationTestProtocol.CHAT_COMPLETIONS ->
            endpoint.encodedPath.trimEnd('/') in DEEPSEEK_CHAT_PATHS
        AiModelGenerationTestProtocol.RESPONSES ->
            endpoint.encodedPath.trimEnd('/') in DEEPSEEK_RESPONSES_PATHS
        AiModelGenerationTestProtocol.CLAUDE_MESSAGES ->
            endpoint.encodedPath.trimEnd('/') == "/anthropic/v1/messages"
        AiModelGenerationTestProtocol.GEMINI_GENERATE_CONTENT -> false
    }
    if (!allowedPath) return null

    val flash = model.id in DEEPSEEK_FLASH_MODELS
    val supported = when {
        flash -> setOf(AiWebTool.SEARCH, AiWebTool.READ_LINK)
        model.id == "deepseek-v4-pro" && protocol != AiModelGenerationTestProtocol.CHAT_COMPLETIONS ->
            setOf(AiWebTool.SEARCH)
        else -> return null
    }
    val settings = model.modelSettingsFor(modelConfigurationKey())
    val enabled = supported.filterTo(linkedSetOf()) { tool ->
        when (tool) {
            AiWebTool.SEARCH -> settings?.nativeWebSearch != false
            AiWebTool.READ_LINK -> settings?.nativeReadLinks != false
        }
    }
    return AiDeepSeekWebRoute(endpoint.toString(), protocol, flash, supported, enabled)
}

private val DEEPSEEK_FLASH_MODELS = setOf("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp")
private val DEEPSEEK_CHAT_PATHS = setOf("/chat/completions", "/v1/chat/completions")
private val DEEPSEEK_RESPONSES_PATHS = setOf("/responses", "/v1/responses")
private val WEB_URL = Regex("(?i)https?://[^\\s<>\"`]+")

internal fun AiNativeChatHistory.attachDeepSeekChatSteps(
    message: JsonObject,
    steps: JsonArray?,
    finalReasoning: String?,
) {
    if (steps == null || steps.isEmpty) return
    attachParts(message, JsonArray().apply {
        add(JsonObject().apply {
            addProperty("type", "deepseek_chat_steps")
            add("steps", steps)
            finalReasoning?.let { addProperty("final_reasoning", it) }
        })
    })
}

internal fun AiNativeChatHistory.deepSeekChatRecord(message: JsonObject): JsonObject? =
    parts(message)?.singleOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        ?.takeIf { it.stringOrNull("type") == "deepseek_chat_steps" }
