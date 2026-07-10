package io.legado.app.help.ai

import com.google.gson.JsonObject

internal fun AiProviderSetting.reasoningOptions(modelId: String): AiModelReasoningOptions {
    val inferred = AiModelRegistry.capabilities(modelId).reasoning
    return AiModelReasoningOptions(
        thinkingParam = thinkingParam.ifBlank { inferred.thinkingParam },
        effortParam = effortParam.ifBlank { inferred.effortParam },
        disableEffortValue = disableEffortValue,
        reasoningOutputField = reasoningOutputField.ifBlank { inferred.reasoningOutputField }
    )
}

internal fun JsonObject.addReasoningParams(
    params: AiTextParams,
    options: AiModelReasoningOptions
) {
    if (params.enableThinking) {
        if (options.thinkingParam.isNotBlank()) {
            add(options.thinkingParam, JsonObject().apply {
                addProperty("type", "enabled")
            })
        }
        if (options.effortParam.isNotBlank()) {
            addProperty(options.effortParam, params.reasoningEffort ?: "high")
        }
        return
    }

    if (!params.disableThinking) return
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
