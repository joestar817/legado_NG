package io.legado.app.help.ai

object AiDefaultProviders {

    fun all(): List<AiProviderSetting> = listOf(
        AiProviderSetting(
            id = "openai",
            type = AiProviderType.OPENAI,
            name = "OpenAI",
            baseUrl = "https://api.openai.com/v1",
            enabled = true
        ),
        AiProviderSetting(
            id = "claude",
            type = AiProviderType.CLAUDE,
            name = "Claude",
            baseUrl = "https://api.anthropic.com/v1",
            enabled = true
        ),
        AiProviderSetting(
            id = "gemini",
            type = AiProviderType.GOOGLE,
            name = "Gemini",
            baseUrl = "https://generativelanguage.googleapis.com/v1beta",
            enabled = true
        ),
        AiProviderSetting(
            id = "deepseek",
            type = AiProviderType.OPENAI,
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com",
            model = "deepseek-v4-flash",
            modelsUrl = "https://api.deepseek.com/models",
            supportsThinking = true,
            supportsEffort = true,
            thinkingParam = "thinking",
            effortParam = "reasoning_effort",
            reasoningOutputField = "reasoning_content",
            enabled = true
        ),
        AiProviderSetting(
            id = "xiaomi_mimo",
            type = AiProviderType.OPENAI,
            name = "Xiaomi MiMo",
            baseUrl = "https://api.xiaomimimo.com/v1",
            model = "mimo-v2.5-pro",
            supportsThinking = true,
            supportsEffort = false,
            thinkingParam = "thinking",
            reasoningOutputField = "reasoning_content",
            enabled = true
        )
    )

}
