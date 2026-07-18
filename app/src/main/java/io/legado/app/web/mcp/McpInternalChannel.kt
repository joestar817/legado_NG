package io.legado.app.web.mcp

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.legado.app.constant.PreferKey
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefBoolean
import splitties.init.appCtx

object McpInternalChannel {

    fun isEnabled(): Boolean = appCtx.getPrefBoolean(PreferKey.aiInternalMcp, false)

    fun request(request: JsonObject): JsonElement? {
        check(isEnabled()) { "内置 MCP 通道未开启" }
        return McpServer.handleJsonRpc(request)
    }

    fun request(requestJson: String): JsonElement? {
        check(isEnabled()) { "内置 MCP 通道未开启" }
        return McpServer.handleJsonRpc(requestJson)
    }

    fun listTools(capabilityIds: Collection<String>): List<JsonObject> {
        check(isEnabled()) { "内置 MCP 通道未开启" }
        return McpServer.listInternalTools(capabilityIds).mapNotNull { tool ->
            GSON.toJsonTree(tool).takeIf { it.isJsonObject }?.asJsonObject
        }
    }

    fun callTool(
        name: String,
        arguments: JsonObject = JsonObject(),
        capabilityIds: Collection<String>? = null,
        executionContext: McpToolExecutionContext? = null
    ): JsonObject {
        if (capabilityIds != null) {
            check(isEnabled()) { "内置 MCP 通道未开启" }
            return GSON.toJsonTree(
                McpServer.callInternalTool(name, arguments, capabilityIds, executionContext)
            ).asJsonObject
        }
        val params = JsonObject().apply {
            addProperty("name", name)
            add("arguments", arguments)
        }
        val response = request(jsonRpc("tools/call", params))
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw IllegalStateException("MCP tools/call notification returned no response")
        response.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
            throw IllegalStateException(it.get("message")?.asString ?: "MCP tool call failed")
        }
        return response.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalStateException("MCP tool call returned invalid result")
    }

    fun readResource(uri: String): JsonObject {
        val params = JsonObject().apply {
            addProperty("uri", uri)
        }
        val response = request(jsonRpc("resources/read", params))
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: throw IllegalStateException("MCP resources/read notification returned no response")
        response.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.let {
            throw IllegalStateException(it.get("message")?.asString ?: "MCP resource read failed")
        }
        return response.get("result")?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalStateException("MCP resource read returned invalid result")
    }

    private fun jsonRpc(method: String, params: JsonObject? = null): JsonObject {
        return JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", System.currentTimeMillis())
            addProperty("method", method)
            params?.let { add("params", it) }
        }
    }
}
