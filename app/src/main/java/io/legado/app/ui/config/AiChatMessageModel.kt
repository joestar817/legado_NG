package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import io.legado.app.R
import io.legado.app.help.ai.AiModel
import io.legado.app.help.ai.AiProviderSetting

internal data class AiChatMessageModel(
    val label: String?,
    @DrawableRes val iconRes: Int,
)

internal fun resolveChatMessageModel(
    providerId: String?,
    modelId: String?,
    providers: Map<String, AiProviderSetting>,
): AiChatMessageModel {
    val id = modelId?.takeIf { it.isNotBlank() }
        ?: return AiChatMessageModel(null, R.drawable.ic_ai_setting)
    val provider = providerId?.let(providers::get)
    // Old replies can refer to disabled or removed catalog entries.
    val catalogModel = provider?.models?.firstOrNull { it.id == id }
    val icon = (catalogModel ?: AiModel(id)).iconRes(provider?.iconRes() ?: R.drawable.ic_ai_setting)
    return AiChatMessageModel(catalogModel?.displayName() ?: id, icon)
}
