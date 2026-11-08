package io.legado.app.help.ai

import io.legado.app.help.pet.PetIdentity

/** Persist the user's selection independently of the installed plugin list. */
@JvmInline
value class AiChatEntryStyle private constructor(val id: String) {
    val isPet: Boolean get() = this != NONE && this != BUTTON

    companion object {
        val NONE = AiChatEntryStyle("none")
        val BUTTON = AiChatEntryStyle("button")

        fun fromId(id: String?): AiChatEntryStyle = when {
            id == NONE.id -> NONE
            id != null && PetIdentity.valid(id) -> AiChatEntryStyle(id)
            else -> BUTTON
        }
    }
}
