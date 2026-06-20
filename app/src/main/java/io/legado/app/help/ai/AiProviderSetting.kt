package io.legado.app.help.ai

data class AiProviderSetting(
    val id: String,
    val type: AiProviderType,
    val enabled: Boolean = true,
    val builtIn: Boolean = true,
    val name: String,
    val apiKey: String = "",
    val baseUrl: String,
    val model: String = "",
    val models: List<AiModel> = emptyList(),
    val timeoutSeconds: Int = 60,
    val chatCompletionsPath: String = "/chat/completions",
    val modelsUrl: String = "",
    val supportsThinking: Boolean = false,
    val supportsEffort: Boolean = false,
    val thinkingParam: String = "",
    val effortParam: String = "",
    val reasoningOutputField: String = ""
)

enum class AiProviderType(val prefValue: String, val displayName: String) {
    OPENAI("openai", "OpenAI compatible"),
    GOOGLE("google", "Google Gemini"),
    CLAUDE("claude", "Claude");

    companion object {
        fun from(value: String?): AiProviderType {
            return entries.firstOrNull {
                it.prefValue == value || it.name.equals(value, ignoreCase = true)
            } ?: OPENAI
        }
    }
}
