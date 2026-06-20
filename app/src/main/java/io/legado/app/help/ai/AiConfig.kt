package io.legado.app.help.ai

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import splitties.init.appCtx

object AiConfig {

    val temperature: Float?
        get() = appCtx.getPrefString(PreferKey.aiTemperature, "0.2")
            ?.toFloatOrNull()
            ?.coerceIn(0f, 2f)

    var purifyAutoApply: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyAutoApply, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyAutoApply, value)
        }

    fun currentSetting(): AiProviderSetting {
        return AiProviderStore.activeProvider()
    }

    fun savedModels(): List<AiModel> {
        return currentSetting().models
    }

    fun saveModels(models: List<AiModel>) {
        val setting = currentSetting()
        AiProviderStore.saveProvider(setting.copy(models = models))
    }

    fun clearModels() {
        val setting = currentSetting()
        AiProviderStore.saveProvider(setting.copy(models = emptyList()))
    }
}
