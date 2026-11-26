package io.legado.app.help.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import okhttp3.Request

class OpenAiCompatibleProvider : AiProvider {

    override suspend fun listModels(setting: AiProviderSetting): List<AiModel> {
        val client = aiHttpClient(setting.timeoutSeconds)
        val deepSeekEndpoint = setting.officialDeepSeekCatalogEndpoint()
        val modelKey = setting.modelConfigurationKey()
        val apiKey = setting.apiKeyForRequest()
        var lastError: Throwable? = null
        AiModelEndpointResolver.candidates(setting).forEach { url ->
            try {
                val request = Request.Builder()
                    .url(buildAiApiEndpoint(setting.baseUrl, url))
                    .apply {
                        if (apiKey.isNotBlank()) {
                            addHeader("Authorization", "Bearer $apiKey")
                        }
                    }
                    .addOpenCodeProviderHeaders(setting)
                    .get()
                    .build()
                val (response, body) = client.executeJsonOrThrow(request)
                if (response.code == 404 || response.code == 405) {
                    return@forEach
                }
                if (!response.isSuccessful) {
                    error("HTTP ${response.code}: ${body.take(500)}")
                }
                val json = JsonParser.parseString(body).asJsonObject
                val resolved = response.request.url
                val infoEndpoint = modelKey.takeIf {
                    resolved.sameOriginAs(request.url) && (deepSeekEndpoint == null ||
                            request.url.deepSeekCatalogEndpoint() == deepSeekEndpoint && resolved.deepSeekCatalogEndpoint() != null)
                }
                return parseModels(json, infoEndpoint, setting.openAiCatalogFormat(resolved), resolved.catalogSourceId())
            } catch (e: CancellationException) {
                throw e
            } catch (e: AiEndpointException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No model endpoint candidates")
    }

    internal fun parseModels(
        json: JsonObject,
        configurationKey: String?,
        format: AiModelCatalogFormat = AiModelCatalogFormat.COMPATIBLE,
        catalogSourceId: String? = null,
    ): List<AiModel> {
        check(json.get("has_more")?.let { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean } != true) {
            "Model catalog response is incomplete"
        }
        return json.modelCatalogEntries("data", "id").map { (obj, id) ->
            val clearCapabilities = AiModelRegistry.shouldClearDeclaredCapabilities(id)
            AiModel(
                id = id,
                name = obj.stringOrNull("name") ?: id,
                ownedBy = obj.stringOrNull("owned_by").orEmpty(),
                inputModalities = if (clearCapabilities) {
                    emptyList()
                } else {
                    obj.stringArrayOrEmpty("input_modalities").toModelModalities()
                },
                outputModalities = if (clearCapabilities) {
                    emptyList()
                } else {
                    obj.stringArrayOrEmpty("output_modalities").toModelModalities()
                },
                abilities = if (clearCapabilities) {
                    emptyList()
                } else {
                    obj.stringArrayOrEmpty("supported_features").toModelAbilities()
                },
                modelInfo = configurationKey?.let {
                    readModelInfo(obj, it, format).copy(catalogSourceId = catalogSourceId)
                },
            )
        }
    }

    private fun JsonObject.stringArrayOrEmpty(key: String): List<String> {
        return arrayOrNull(key)
            ?.mapNotNull { item ->
                item.takeIf { it.isJsonPrimitive }?.asString?.trim()
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun List<String>.toModelAbilities(): List<AiModelAbility> {
        val values = map { it.lowercase() }.toSet()
        return buildList {
            if ("tools" in values || "tool" in values || "function_calling" in values) {
                add(AiModelAbility.TOOL)
            }
            if ("reasoning" in values) {
                add(AiModelAbility.REASONING)
            }
        }
    }

    private fun List<String>.toModelModalities(): List<AiModelModality> {
        val values = map { it.lowercase() }.toSet()
        return buildList {
            if ("text" in values) add(AiModelModality.TEXT)
            if ("image" in values) add(AiModelModality.IMAGE)
            if ("audio" in values) add(AiModelModality.AUDIO)
            if ("video" in values) add(AiModelModality.VIDEO)
            if ("file" in values) add(AiModelModality.FILE)
        }
    }
}
