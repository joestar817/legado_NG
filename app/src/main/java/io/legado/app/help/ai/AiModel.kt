package io.legado.app.help.ai

data class AiModel(
    val id: String,
    val name: String = id,
    val ownedBy: String = ""
)
