package io.legado.app.help.ai

import com.google.gson.JsonObject
import io.legado.app.R
import okhttp3.Request

internal suspend fun AiProviderSetting.fetchAccountModels(): List<AiModel> {
    if (isCodexSubscription()) {
        return parseChatGptModels(
            getCodexAccountJson("$CODEX_API/models?client_version=0.171.0"), modelConfigurationKey(), codex = true,
        )
    }
    val token = AiChatGptSessions.accessToken(this)
    val request = Request.Builder().url("$CHATGPT_API/models")
        .header("Authorization", "Bearer $token").get().build()
    return chatGptHttpClient(timeoutSeconds).newCall(request).readCancellableStream { response ->
        response.use {
            val json = it.chatGptJson()
            if (!it.isSuccessful) {
                val error = chatGptRequestError(it.code, json)
                throw AiChatGptSessions.resolveRequestError(chatGptAccountId, token, error)
            }
            if (json == null) throw AiChatGptException(R.string.ai_chatgpt_invalid_catalog)
            parseChatGptModels(json, modelConfigurationKey())
        }
    }
}

internal fun parseChatGptModels(json: JsonObject, modelSource: String, codex: Boolean = false): List<AiModel> {
    val entries = json.arrayOrNull("models") ?: throw AiChatGptException(R.string.ai_chatgpt_invalid_catalog)
    return entries.filterIsInstance<JsonObject>().mapNotNull { item ->
        if (item.modelCatalogText("visibility") != "list") return@mapNotNull null
        val slug = item.modelCatalogText("slug") ?: return@mapNotNull null
        val name = item.modelCatalogText("display_name") ?: slug
        val effortLevels = item.arrayOrNull("supported_reasoning_levels")?.filterIsInstance<JsonObject>()
            ?.mapNotNull { it.modelCatalogText("effort") }?.distinct()
        val inputs = item.catalogStrings("input_modalities") ?: listOf("text")
        val contextWindow = item.positiveInt("max_context_window") ?: item.positiveInt("context_window")
        val info = AiModelInfo(
            endpoint = modelSource,
            name = AiModelValue(name),
            contextWindow = contextWindow?.let { AiModelValue(it) },
            inputModalities = AiModelValue(inputs),
            outputModalities = AiModelValue(listOf("text")),
            toolCalling = if (codex) AiModelValue(true) else null,
            effortLevels = effortLevels?.let { AiModelValue(it) },
            reasoning = effortLevels?.let { AiModelValue(it.isNotEmpty()) },
            canDisableThinking = effortLevels?.let { AiModelValue("none" in it) },
            catalogSourceId = modelSource,
        )
        AiModelRegistry.enrich(AiModel(id = slug, name = name, ownedBy = "OpenAI", modelInfo = info))
            .withCatalogCapabilities(info)
    }.distinctBy { it.id }
}

private fun JsonObject.modelCatalogText(name: String): String? = get(name)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.trim()?.takeIf(String::isNotEmpty)
