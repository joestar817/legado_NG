package io.legado.app.help.ai

object AiDefaultProviders {

    fun all(): List<AiProviderSetting> = listOf(
        AiProviderSetting(
            id = "openai",
            type = AiProviderType.OPENAI,
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            modelsUrl = "/models",
            supportsStreamUsage = true,
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
            modelsUrl = "/models",
            balanceUrl = "/credits",
            balanceJsonPath = "data.total_credits - data.total_usage",
            enabled = true
        ),
        AiProviderSetting(
            id = "xiaomi_mimo",
            type = AiProviderType.OPENAI,
            name = "Xiaomi MiMo",
            baseUrl = "https://api.xiaomimimo.com/v1",
            model = "mimo-v2.5-pro",
            modelsUrl = "/models",
            enabled = true
        ),
        AiProviderSetting(
            id = "sensenova",
            type = AiProviderType.OPENAI,
            name = "商汤",
            baseUrl = "https://token.sensenova.cn/v1",
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
            modelsUrl = "/models",
            enabled = false
        ),
        AiProviderSetting(
            id = "volcengine",
            type = AiProviderType.OPENAI,
            name = "火山引擎",
            baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
            modelsUrl = "/models",
            enabled = false
        ),
        AiProviderSetting(
            id = "moonshot",
            type = AiProviderType.OPENAI,
            name = "月之暗面",
            baseUrl = "https://api.moonshot.cn/v1",
            modelsUrl = "/models",
            balanceUrl = "/users/me/balance",
            balanceJsonPath = "data.available_balance",
            enabled = false
        ),
        AiProviderSetting(
            id = "zhipu",
            type = AiProviderType.OPENAI,
            name = "智谱",
            baseUrl = "https://open.bigmodel.cn/api/paas/v4",
            modelsUrl = "/models",
            enabled = false
        )
    )

}
