package io.legado.app.help.ai

import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException

/** Converts tool exceptions in a regular function before returning to the chat coroutine. */
internal fun executeInternalToolForModel(execute: () -> JsonObject): JsonObject = try {
    execute()
} catch (error: CancellationException) {
    throw error
} catch (error: Throwable) {
    failedModelToolResult(error.localizedMessage ?: "MCP tool failed")
}

internal fun unavailableModelToolResult(functionName: String): JsonObject {
    val name = functionName.take(128)
    val message = if (name.isBlank()) {
        "Missing tool name. Use a tool from the provided list."
    } else {
        "Tool '$name' is not available in this turn. Use a tool from the provided list."
    }
    return failedModelToolResult(message)
}

private fun failedModelToolResult(message: String) = JsonObject().apply {
    addProperty("ok", false)
    addProperty("error", message)
}
