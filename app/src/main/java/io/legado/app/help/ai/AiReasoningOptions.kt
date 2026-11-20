package io.legado.app.help.ai

import com.google.gson.JsonObject

/** Wire formats chosen for the assistant after resolving the selected model's capability. */
enum class AiReasoningProtocol { CONFIGURED, THINKING, ENABLE_THINKING, EFFORT, OPENROUTER, NATIVE }

internal fun AiProviderSetting.reasoningOptions(modelId: String): AiModelReasoningOptions {
    val inferred = AiModelRegistry.capabilities(modelId).reasoning
    return AiModelReasoningOptions(
        thinkingParam = thinkingParam.ifBlank { inferred.thinkingParam },
        effortParam = effortParam.ifBlank { inferred.effortParam },
        disableEffortValue = disableEffortValue,
        reasoningOutputField = reasoningOutputField.ifBlank { inferred.reasoningOutputField }
    )
}

internal fun AiProviderSetting.assistantReasoningFields(modelId: String): AiModelReasoningOptions {
    val inferred = reasoningOptions(modelId)
    if (thinkingParam.isBlank() && effortParam.isBlank()) return inferred
    return inferred.copy(thinkingParam = thinkingParam, effortParam = effortParam)
}

internal fun JsonObject.addReasoningParams(
    params: AiTextParams,
    options: AiModelReasoningOptions
) {
    if (!params.enableThinking && !params.disableThinking) return
    val protocol = params.assistantReasoningProtocol
    val effort = params.reasoningEffort.takeIf { params.enableThinking }
    when (protocol) {
        null -> addConfiguredReasoningParams(params, options, effort ?: "high")
        AiReasoningProtocol.CONFIGURED -> addConfiguredReasoningParams(params, options, effort)
        AiReasoningProtocol.THINKING -> {
            add("thinking", JsonObject().apply {
                addProperty("type", if (params.enableThinking) "enabled" else "disabled")
            })
            effort?.let { addProperty("reasoning_effort", it) }
        }
        AiReasoningProtocol.ENABLE_THINKING -> {
            addProperty("enable_thinking", params.enableThinking)
            effort?.let { addProperty("reasoning_effort", it) }
            params.assistantReasoningMaxTokens?.let { addProperty("thinking_budget", it) }
        }
        AiReasoningProtocol.EFFORT -> {
            val value = effort ?: "none".takeIf { params.disableThinking }
            value?.let { addProperty("reasoning_effort", it) }
        }
        AiReasoningProtocol.OPENROUTER -> add("reasoning", openRouterReasoningRequest(params))
        AiReasoningProtocol.NATIVE -> Unit
    }
}

internal fun openRouterReasoningRequest(params: AiTextParams) = JsonObject().apply {
    val budget = params.assistantReasoningMaxTokens?.takeIf { it > 0 }
    val effort = params.reasoningEffort
    when {
        params.disableThinking -> addProperty("enabled", false)
        budget != null -> addProperty("max_tokens", budget)
        effort != null -> addProperty("effort", effort)
        else -> addProperty("enabled", true)
    }
}

private fun JsonObject.addConfiguredReasoningParams(
    params: AiTextParams,
    options: AiModelReasoningOptions,
    effort: String?,
) {
    if (params.enableThinking) {
        if (options.thinkingParam.isNotBlank()) {
            add(options.thinkingParam, JsonObject().apply {
                addProperty("type", "enabled")
            })
        }
        if (options.effortParam.isNotBlank() && effort != null) {
            addProperty(options.effortParam, effort)
        }
        return
    }

    if (options.thinkingParam.isNotBlank()) {
        add(options.thinkingParam, JsonObject().apply {
            addProperty("type", "disabled")
        })
    } else if (
        options.effortParam.isNotBlank() &&
        options.disableEffortValue.isNotBlank()
    ) {
        addProperty(options.effortParam, options.disableEffortValue)
    }
}
