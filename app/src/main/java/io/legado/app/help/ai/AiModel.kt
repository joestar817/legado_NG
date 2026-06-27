package io.legado.app.help.ai

import com.google.gson.annotations.SerializedName

data class AiModel(
    @SerializedName(value = "id", alternate = ["a"])
    val id: String,
    @SerializedName(value = "name", alternate = ["b"])
    val name: String = id,
    @SerializedName(value = "displayName")
    val displayName: String = "",
    @SerializedName(value = "ownedBy", alternate = ["owned_by", "c"])
    val ownedBy: String = "",
    @SerializedName(value = "type")
    val type: AiModelType = AiModelType.CHAT,
    @SerializedName(value = "inputModalities", alternate = ["d"])
    val inputModalities: List<AiModelModality> = emptyList(),
    @SerializedName(value = "outputModalities", alternate = ["e"])
    val outputModalities: List<AiModelModality> = emptyList(),
    @SerializedName(value = "abilities", alternate = ["f"])
    val abilities: List<AiModelAbility> = emptyList()
)

enum class AiModelType {
    @SerializedName("chat")
    CHAT,

    @SerializedName("image")
    IMAGE,

    @SerializedName("embedding")
    EMBEDDING,

    @SerializedName("video")
    VIDEO,

    @SerializedName("asr")
    ASR,

    @SerializedName("tts")
    TTS
}

enum class AiModelModality {
    @SerializedName("text")
    TEXT,

    @SerializedName("image")
    IMAGE,

    @SerializedName("audio")
    AUDIO,

    @SerializedName("video")
    VIDEO
}

enum class AiModelAbility {
    @SerializedName("asr")
    ASR,

    @SerializedName("tts")
    TTS,

    @SerializedName("tool")
    TOOL,

    @SerializedName("reasoning")
    REASONING
}
