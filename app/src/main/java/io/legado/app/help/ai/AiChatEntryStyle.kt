package io.legado.app.help.ai

import androidx.annotation.StringRes
import io.legado.app.R

/** Stable entry IDs are persisted independently of their labels and visual assets. */
enum class AiChatEntryStyle(
    val id: String,
    @param:StringRes val titleRes: Int,
    val petAssetPath: String? = null,
) {
    NONE("none", R.string.ai_chat_entry_none),
    BUTTON("button", R.string.ai_chat_entry_button),
    GUGUGAGA("gugugaga", R.string.ai_chat_entry_gugugaga, "ai_pets/gugugaga"),
    BLUE_FISH("blue_fish", R.string.ai_chat_entry_blue_fish, "ai_pets/blue_fish");

    companion object {
        fun fromId(id: String?): AiChatEntryStyle = entries.firstOrNull { it.id == id } ?: BUTTON
    }
}
