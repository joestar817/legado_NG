package io.legado.app.ui.config

import io.legado.app.help.ai.AiDefaultProviders
import io.legado.app.help.ai.AiProviderSetting
import io.legado.app.help.ai.officialModelProviderId
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal object AiProviderHelpDocuments {
    private val documents = mapOf(
        "openai" to "openAiProviderHelp",
        AiDefaultProviders.CHATGPT_ID to "chatGptProviderHelp",
        AiDefaultProviders.CODEX_ID to "codexProviderHelp",
        AiDefaultProviders.WORK_BUDDY_ID to "workBuddyProviderHelp",
        AiDefaultProviders.OPENCODE_ID to "openCodeProviderHelp",
        "claude" to "claudeProviderHelp",
        "gemini" to "geminiProviderHelp",
        "deepseek" to "deepSeekProviderHelp",
        "siliconflow" to "siliconFlowProviderHelp",
        "openrouter" to "openRouterProviderHelp",
        AiDefaultProviders.AGNES_ID to "agnesProviderHelp",
        "xiaomi_mimo" to "xiaomiMiMoProviderHelp",
        "sensenova" to "senseNovaProviderHelp",
        "aliyun_bailian" to "aliyunBailianProviderHelp",
        "volcengine" to "volcengineProviderHelp",
        "moonshot" to "moonshotProviderHelp",
        "zhipu" to "zhipuProviderHelp",
    )
    private val presetEndpoints = AiDefaultProviders.all().associate { provider ->
        provider.id to provider.baseUrl.toHttpUrlOrNull()
    }
    private val modelPages = mapOf(
        "openai" to "https://developers.openai.com/api/docs/models",
        AiDefaultProviders.OPENCODE_ID to "https://opencode.ai/docs/console/go/",
        "claude" to "https://platform.claude.com/docs/en/models/overview",
        "gemini" to "https://ai.google.dev/gemini-api/docs/models",
        "deepseek" to "https://api-docs.deepseek.com/zh-cn/quick_start/pricing/",
        "siliconflow" to "https://www.siliconflow.cn/models",
        "openrouter" to "https://openrouter.ai/models",
        AiDefaultProviders.AGNES_ID to "https://wiki.agnes-ai.com/zh-Hans/docs/pricing",
        "xiaomi_mimo" to "https://mimo.mi.com/docs/zh-CN/quick-start/summary/model",
        "sensenova" to "https://www.sensenova.cn/token-plan",
        "aliyun_bailian" to "https://help.aliyun.com/zh/model-studio/models",
        "volcengine" to "https://docs.volcengine.com/docs/ark/model-pricing?lang=zh",
        "moonshot" to "https://platform.kimi.com/docs/pricing/chat",
        "zhipu" to "https://docs.bigmodel.cn/cn/guide/start/model-overview",
    )

    fun modelPage(provider: AiProviderSetting): String? = provider.officialModelProviderId()?.let(modelPages::get)

    fun forProvider(providerId: String, builtIn: Boolean, baseUrl: String): String? {
        if (!builtIn) return null
        val document = documents[providerId] ?: return null
        val preset = presetEndpoints[providerId] ?: return null
        val endpoint = baseUrl.toHttpUrlOrNull() ?: return null
        if (!endpoint.isHttps || endpoint.host != preset.host) return null

        // Go and the pay-as-you-go API use the same host with different paths.
        if (providerId == AiDefaultProviders.OPENCODE_ID &&
            endpoint.encodedPath.trimEnd('/') != preset.encodedPath
        ) return null

        return document
    }
}
