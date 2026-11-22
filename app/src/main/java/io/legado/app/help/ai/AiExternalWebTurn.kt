package io.legado.app.help.ai

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.data.AgentToolResultStore
import splitties.init.appCtx

/** Holds the selected service and web results for one assistant turn. */
internal class AiExternalWebTurn(
    private val service: AiExternalSearchService,
    private val client: AiExternalSearchClient,
    private val onUpdate: (AiWebActivity) -> Unit,
) {
    val definitions = AiExternalSearchTool.entries.map { it.definition(service.provider) }
    private val keyDigest = AgentToolResultStore.payloadHash(service.apiKey.trim())
    private val dispatchedResults = mutableMapOf<Pair<AiExternalSearchTool, String>, JsonObject>()
    private var activity = AiWebActivity(providerLabel = appCtx.getString(R.string.ai_search_source_external, service.displayName))
    val settled: AiWebActivity? get() = activity.settledOrNull()

    fun tool(functionName: String): AiExternalSearchTool? =
        AiExternalSearchTool.entries.firstOrNull { it.functionName == functionName }

    fun receiptArguments(tool: AiExternalSearchTool, arguments: JsonObject): JsonArray = JsonArray().apply {
        add(service.provider.id)
        add(keyDigest)
        add(tool.functionName)
        if (tool == AiExternalSearchTool.SEARCH) {
            add(service.chatResultCount)
            // Keep existing receipt keys for searches using the default options.
            when (service.provider) {
                AiExternalSearchProvider.DEEPSEEK, AiExternalSearchProvider.TINYFISH, AiExternalSearchProvider.EXA -> Unit
                AiExternalSearchProvider.DOUBAO -> if (service.doubaoMode != AiDoubaoSearchMode.CUSTOM) {
                    add(service.doubaoMode.id)
                }
                AiExternalSearchProvider.TAVILY -> if (service.tavilySearchDepth != AiTavilySearchDepth.BASIC) {
                    add(service.tavilySearchDepth.apiValue)
                }
            }
        }
        add(normalizedInput(tool, arguments))
    }

    suspend fun execute(tool: AiExternalSearchTool, arguments: JsonObject): JsonObject {
        val input = normalizedInput(tool, arguments)
        val identity = tool to input
        dispatchedResults[identity]?.let { return it.deepCopy() }
        publish(activity.copy(activeAction = tool.activity))
        try {
            val result = try {
                val pages = client.execute(service, tool, input)
                JsonObject().apply {
                    addProperty("ok", true)
                    add("results", JsonArray().apply {
                        pages.forEach { page -> add(JsonObject().apply {
                            addProperty("title", page.source.title)
                            addProperty("url", page.source.url)
                            addProperty(if (tool == AiExternalSearchTool.SEARCH) "summary" else "content", page.text)
                            if (page.truncated) addProperty("truncated", true)
                        }) }
                    })
                }
            } catch (error: AiExternalSearchException) {
                JsonObject().apply {
                    addProperty("ok", false)
                    addProperty("error", error.displayMessage(appCtx))
                }
            }
            dispatchedResults[identity] = result.deepCopy()
            return result
        } finally {
            publish(activity.copy(activeAction = null))
        }
    }

    fun acceptResult(result: JsonObject) {
        val payload = result.objectOrNull("structuredContent") ?: result
        val sources = payload.arrayOrNull("results")?.filterIsInstance<JsonObject>().orEmpty()
            .mapNotNull { entry ->
                val url = entry.stringOrNull("url").webUrl() ?: return@mapNotNull null
                AiWebSource(url, entry.stringOrNull("title")?.takeIf(String::isNotBlank) ?: url)
            }
        publish(activity.copy(
            sources = (activity.sources + sources).distinctBy(AiWebSource::url).take(30),
            externalError = payload.stringOrNull("error") ?: activity.externalError,
        ))
    }

    private fun normalizedInput(tool: AiExternalSearchTool, arguments: JsonObject): String {
        val input = tool.input(arguments).trim()
        return if (tool == AiExternalSearchTool.READ_LINK) input.webUrl() ?: input else input
    }

    private fun publish(next: AiWebActivity) {
        if (next == activity) return
        activity = next
        onUpdate(next)
    }
}
