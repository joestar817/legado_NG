package io.legado.app.help.ai

import android.content.Context
import com.google.gson.annotations.SerializedName
import io.legado.app.utils.GSON
import splitties.init.appCtx

internal data class AiImageDraft(
    @SerializedName("prompt") val prompt: String = "",
    @SerializedName("assets") val assets: List<String> = emptyList(),
    @SerializedName("direct") val direct: Boolean = false,
    @SerializedName("edit_assets") val editAssets: List<String>? = null,
) {
    val hasAttachments get() = assets.isNotEmpty() || direct
}

internal object AiChatImageDrafts {
    private val preferences get() = appCtx.getSharedPreferences("ai-chat-image-drafts", Context.MODE_PRIVATE)

    fun read(conversationId: String): AiImageDraft? {
        val json = preferences.getString(conversationId, null) ?: return null
        return runCatching { GSON.fromJson(json, AiImageDraft::class.java) }.getOrNull()
    }

    fun write(conversationId: String, draft: AiImageDraft?) {
        val json = draft?.takeIf { it.hasAttachments }?.let { GSON.toJson(it) }
        if (preferences.getString(conversationId, null) != json) {
            preferences.edit().putString(conversationId, json).apply()
        }
    }
}
