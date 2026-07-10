package io.legado.app.help.ai

import com.google.gson.annotations.SerializedName

internal const val AI_PROVIDER_DEFAULT_TIMEOUT_SECONDS = 180
internal const val AI_PROVIDER_LEGACY_DEFAULT_TIMEOUT_SECONDS = 60

data class AiProviderSetting(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String,
    @SerializedName(value = "type", alternate = ["b"])
    val type: AiProviderType,
    @SerializedName(value = "enabled", alternate = ["c"])
    val enabled: Boolean = true,
    @SerializedName(value = "builtIn", alternate = ["d"])
    val builtIn: Boolean = true,
    @SerializedName(value = "name", alternate = ["e"])
    val name: String,
    @SerializedName(value = "apiKey", alternate = ["f"])
    val apiKey: String = "",
    @SerializedName(value = "baseUrl", alternate = ["g"])
    val baseUrl: String,
    @SerializedName(value = "model", alternate = ["h"])
    val model: String = "",
    @SerializedName(value = "models", alternate = ["i"])
    val models: List<AiModel> = emptyList(),
    @SerializedName(value = "availableModelIds", alternate = ["v"])
    val availableModelIds: List<String> = emptyList(),
    @SerializedName(value = "availableModelSelectionInitialized", alternate = ["w"])
    val availableModelSelectionInitialized: Boolean = false,
    @SerializedName(value = "timeoutSeconds", alternate = ["j"])
    val timeoutSeconds: Int = AI_PROVIDER_DEFAULT_TIMEOUT_SECONDS,
    @SerializedName(value = "chatCompletionsPath", alternate = ["k"])
    val chatCompletionsPath: String = "/chat/completions",
    @SerializedName(value = "modelsUrl", alternate = ["l"])
    val modelsUrl: String = "",
    @SerializedName(value = "supportsThinking", alternate = ["m"])
    val supportsThinking: Boolean = false,
    @SerializedName(value = "supportsEffort", alternate = ["n"])
    val supportsEffort: Boolean = false,
    @SerializedName(value = "thinkingParam", alternate = ["o"])
    val thinkingParam: String = "",
    @SerializedName(value = "effortParam", alternate = ["p"])
    val effortParam: String = "",
    @SerializedName("disableEffortValue")
    val disableEffortValue: String = "",
    @SerializedName(value = "reasoningOutputField", alternate = ["q"])
    val reasoningOutputField: String = "",
    @SerializedName(value = "useCustomModelsUrl", alternate = ["r"])
    val useCustomModelsUrl: Boolean = false,
    @SerializedName(value = "balanceUrl", alternate = ["s"])
    val balanceUrl: String = "",
    @SerializedName(value = "balanceJsonPath", alternate = ["t"])
    val balanceJsonPath: String = "",
    @SerializedName(value = "useCustomBalanceUrl", alternate = ["u"])
    val useCustomBalanceUrl: Boolean = false,
    @SerializedName(value = "streamResponseEnabled", alternate = ["x"])
    val streamResponseEnabled: Boolean = false,
    @SerializedName("supportsStreamUsage")
    val supportsStreamUsage: Boolean = false
)

enum class AiProviderType(val prefValue: String, val displayName: String) {
    @SerializedName(value = "OPENAI", alternate = ["X", "openai"])
    OPENAI("openai", "OpenAI compatible"),
    @SerializedName(value = "GOOGLE", alternate = ["Y", "google"])
    GOOGLE("google", "Google Gemini"),
    @SerializedName(value = "CLAUDE", alternate = ["Z", "claude"])
    CLAUDE("claude", "Anthropic compatible");

    companion object {
        fun from(value: String?): AiProviderType {
            return entries.firstOrNull {
                it.prefValue == value || it.name.equals(value, ignoreCase = true)
            } ?: OPENAI
        }
    }
}
