package io.legado.app.help.ai

internal fun AiProviderSetting.reasoningOptions(modelId: String): AiModelReasoningOptions {
    val inferred = AiModelRegistry.capabilities(modelId).reasoning
    return AiModelReasoningOptions(
        thinkingParam = thinkingParam.ifBlank { inferred.thinkingParam },
        effortParam = effortParam.ifBlank { inferred.effortParam },
        reasoningOutputField = reasoningOutputField.ifBlank { inferred.reasoningOutputField }
    )
}
