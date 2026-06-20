package io.legado.app.help.ai

data class AiMessage(
    val role: Role,
    val content: String
) {
    enum class Role(val apiValue: String) {
        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant")
    }
}
