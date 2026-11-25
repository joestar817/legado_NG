package io.legado.app.help.ai

object AiDefaultProviders {

    const val OPENCODE_ID = "opencode"
    internal const val CHATGPT_ID = "chatgpt_subscription"
    internal const val CODEX_ID = "codex_subscription"
    internal const val WORK_BUDDY_ID = "workbuddy"
    internal const val AGNES_ID = "agnes"

    fun all(): List<AiProviderSetting> = listOf(
        AiProviderSetting(
            id = "openai",
            type = AiProviderType.OPENAI,
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            responsesApiPath = "/responses",
            modelsUrl = "/models",
            supportsStreamUsage = true,
            enabled = true
        ),
        AiProviderSetting(
            id = CODEX_ID,
            type = AiProviderType.OPENAI,
            name = "Codex",
            baseUrl = CODEX_API,
            responsesApiPath = "/responses",
            useResponsesApi = true,
            streamResponseEnabled = true,
            supportsStreamUsage = true,
        ),
        AiProviderSetting(
            id = CHATGPT_ID,
            type = AiProviderType.OPENAI,
            name = "ChatGPT",
            baseUrl = CHATGPT_API,
            modelsUrl = "/models",
            responsesApiPath = "/responses",
            useResponsesApi = true,
            streamResponseEnabled = true,
            supportsStreamUsage = true,
        ),
        AiProviderSetting(
            id = WORK_BUDDY_ID,
            type = AiProviderType.OPENAI,
            name = "WorkBuddy",
            baseUrl = WORK_BUDDY_API,
            chatCompletionsPath = WORK_BUDDY_CHAT_PATH,
            modelsUrl = WORK_BUDDY_MODELS_PATH,
            streamResponseEnabled = true,
            supportsStreamUsage = true,
        ),
        AiProviderSetting(
            id = OPENCODE_ID,
            type = AiProviderType.OPENAI,
            name = "OpenCode",
            baseUrl = "https://opencode.ai/inference/go",
            chatCompletionsPath = "/openai/v1/chat/completions",
            responsesApiPath = "/openai/v1/responses",
            modelsUrl = "/v1/models",
            enabled = true
        ),
        AiProviderSetting(
            id = "claude",
            type = AiProviderType.CLAUDE,
            name = "Claude",
            baseUrl = "https://api.anthropic.com/v1",
            modelsUrl = "/models",
            enabled = true
        ),
        AiProviderSetting(
            id = "gemini",
            type = AiProviderType.GOOGLE,
            name = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            modelsUrl = "/models?pageSize=100",
            enabled = true
        ),
        AiProviderSetting(
            id = "deepseek",
            type = AiProviderType.OPENAI,
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            responsesApiPath = "https://api.deepseek.com/responses",
            model = "deepseek-v4-flash",
            modelsUrl = "/models",
            balanceUrl = "/user/balance",
            balanceJsonPath = "balance_infos[0].total_balance",
            supportsStreamUsage = true,
            enabled = true
        ),
        AiProviderSetting(
            id = "siliconflow",
            type = AiProviderType.OPENAI,
            name = "硅基流动",
            baseUrl = "https://api.siliconflow.cn/v1",
            modelsUrl = "/models",
            balanceUrl = "/user/info",
            balanceJsonPath = "data.totalBalance",
            enabled = true
        ),
        AiProviderSetting(
            id = "openrouter",
            type = AiProviderType.OPENAI,
            name = "OpenRouter",
            baseUrl = "https://openrouter.ai/api/v1",
            responsesApiPath = "/responses",
            modelsUrl = "/models",
            balanceUrl = "/credits",
            balanceJsonPath = "data.total_credits - data.total_usage",
            enabled = true
        ),
        AiProviderSetting(
            id = AGNES_ID,
            type = AiProviderType.OPENAI,
            name = "Agnes AI",
            baseUrl = "https://apihub.agnes-ai.com/v1",
            model = "agnes-3.0-flash",
            modelsUrl = "/models",
            responsesApiPath = "/responses",
            streamResponseEnabled = true,
            enabled = true
        ),
        AiProviderSetting(
            id = "xiaomi_mimo",
            type = AiProviderType.OPENAI,
            name = "Xiaomi MiMo",
            baseUrl = "https://api.xiaomimimo.com/v1",
            responsesApiPath = "/responses",
            model = "mimo-v2.6-pro",
            modelsUrl = "/models",
            enabled = true
        ),
        AiProviderSetting(
            id = "sensenova",
            type = AiProviderType.OPENAI,
            name = "商汤",
            baseUrl = "https://token.sensenova.cn/v1",
            responsesApiPath = "/responses",
            model = "sensenova-6.7-flash-lite",
            models = listOf(
                AiModel(
                    id = "sensenova-6.7-flash-lite",
                    name = "SenseNova 6.7 Flash-Lite",
                    ownedBy = "SenseNova",
                    inputModalities = listOf(AiModelModality.TEXT, AiModelModality.IMAGE),
                    outputModalities = listOf(AiModelModality.TEXT),
                    abilities = listOf(AiModelAbility.TOOL, AiModelAbility.REASONING)
                )
            ),
            modelsUrl = "/models",
            effortParam = "reasoning_effort",
            disableEffortValue = "none",
            reasoningOutputField = "reasoning",
            streamResponseEnabled = true,
            enabled = true
        ),
        AiProviderSetting(
            id = "aliyun_bailian",
            type = AiProviderType.OPENAI,
            name = "阿里云百炼",
            baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            responsesApiPath = "/responses",
            modelsUrl = "/models",
            enabled = true
        ),
        AiProviderSetting(
            id = "volcengine",
            type = AiProviderType.OPENAI,
            name = "火山引擎",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            responsesApiPath = "/responses",
            modelsUrl = "/models",
            enabled = true
        ),
        AiProviderSetting(
            id = "moonshot",
            type = AiProviderType.OPENAI,
            name = "月之暗面",
            baseUrl = "https://api.moonshot.cn/v1",
            responsesApiPath = "/responses",
            modelsUrl = "/models",
            balanceUrl = "/users/me/balance",
            balanceJsonPath = "data.available_balance",
            enabled = true
        ),
        AiProviderSetting(
            id = "zhipu",
            type = AiProviderType.OPENAI,
            name = "智谱",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            responsesApiPath = "https://open.bigmodel.cn/api/v1/responses",
            modelsUrl = "/models",
            enabled = true
        )
    )

}
