package io.legado.app.help.ai

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

private val bailianPublicHosts = setOf(
    "dashscope.aliyuncs.com",
    "dashscope-intl.aliyuncs.com",
    "dashscope-us.aliyuncs.com",
    "cn-hongkong.dashscope.aliyuncs.com",
)

private val bailianWorkspaceHost = Regex(
    """llm-[a-z0-9](?:[a-z0-9-]{0,57}[a-z0-9])?\.(cn-beijing|ap-southeast-1|us-east-1|eu-central-1|ap-northeast-1|cn-hongkong)\.maas\.aliyuncs\.com""",
)

private val bailianGlmModelsRequiringToolStream = setOf("glm-5", "glm-5.1", "glm-5.2")

/** Checks the request endpoint only. Catalog metadata and credentials stay scoped to the real address. */
internal fun AiProviderSetting.isOfficialBailianGenerationEndpoint(): Boolean {
    if (type != AiProviderType.OPENAI) return false
    val endpoint = baseUrl.trim().toHttpUrlOrNull() ?: return false
    return endpoint.isHttps && endpoint.port == 443 && !endpoint.hasUserInfo() &&
        endpoint.query == null && endpoint.fragment == null &&
        endpoint.encodedPath.removeSuffix("/") == "/compatible-mode/v1" &&
        (endpoint.host in bailianPublicHosts || bailianWorkspaceHost.matches(endpoint.host))
}

// https://help.aliyun.com/zh/model-studio/qwen-function-calling
internal fun AiProviderSetting.requiresBailianToolStream(modelId: String): Boolean =
    modelId in bailianGlmModelsRequiringToolStream && isOfficialBailianGenerationEndpoint() &&
        generationUrl(AiModelGenerationTestProtocol.CHAT_COMPLETIONS)?.encodedPath?.trimEnd('/') ==
        "/compatible-mode/v1/chat/completions"
