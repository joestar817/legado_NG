package io.legado.app.help.ai

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import splitties.init.appCtx

object AiConfig {

    const val DEFAULT_PURIFY_PARAGRAPH_LIMIT = 4000
    const val MIN_PURIFY_PARAGRAPH_LIMIT = 500
    const val MAX_PURIFY_PARAGRAPH_LIMIT = 20000
    const val DEFAULT_PURIFY_CHAPTER_SEGMENT_LIMIT = 10000
    const val MIN_PURIFY_CHAPTER_SEGMENT_LIMIT = 1000
    const val MAX_PURIFY_CHAPTER_SEGMENT_LIMIT = 50000
    const val DEFAULT_PURIFY_CHAPTER_SAMPLE_LIMIT = 10
    const val MIN_PURIFY_CHAPTER_SAMPLE_LIMIT = 1
    const val MAX_PURIFY_CHAPTER_SAMPLE_LIMIT = 200
    const val DEFAULT_PURIFY_CHAPTER_CONCURRENCY_LIMIT = 10
    const val MIN_PURIFY_CHAPTER_CONCURRENCY_LIMIT = 1
    const val MAX_PURIFY_CHAPTER_CONCURRENCY_LIMIT = 64
    const val DEFAULT_PURIFY_CHAPTER_RETRY_COUNT = 3
    const val MIN_PURIFY_CHAPTER_RETRY_COUNT = 0
    const val MAX_PURIFY_CHAPTER_RETRY_COUNT = 10
    private const val PARAGRAPH_OUTPUT_BUDGET_LIMIT = 6144
    private const val CHAPTER_OUTPUT_BUDGET_LIMIT = 12288

    var internalMcpEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiInternalMcp, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiInternalMcp, value)
        }

    var chatFabEnabled: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiChatFab, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiChatFab, value)
        }

    var purifyProviderId: String
        get() = appCtx.getPrefString(PreferKey.aiPurifyProviderId).orEmpty()
        set(value) {
            appCtx.putPrefString(PreferKey.aiPurifyProviderId, value.trim())
        }

    var purifyModelId: String
        get() = appCtx.getPrefString(PreferKey.aiPurifyModelId).orEmpty()
        set(value) {
            appCtx.putPrefString(PreferKey.aiPurifyModelId, value.trim())
        }

    var purifyReasoningLevel: AiReasoningLevel
        get() = AiReasoningLevel.from(appCtx.getPrefString(PreferKey.aiPurifyReasoningLevel))
        set(value) {
            appCtx.putPrefString(PreferKey.aiPurifyReasoningLevel, value.prefValue)
        }

    var assistantProviderId: String
        get() = appCtx.getPrefString(PreferKey.aiAssistantProviderId).orEmpty()
        set(value) {
            appCtx.putPrefString(PreferKey.aiAssistantProviderId, value.trim())
        }

    var assistantModelId: String
        get() = appCtx.getPrefString(PreferKey.aiAssistantModelId).orEmpty()
        set(value) {
            appCtx.putPrefString(PreferKey.aiAssistantModelId, value.trim())
        }

    var assistantReasoningLevel: AiReasoningLevel
        get() = AiReasoningLevel.from(appCtx.getPrefString(PreferKey.aiAssistantReasoningLevel))
        set(value) {
            appCtx.putPrefString(PreferKey.aiAssistantReasoningLevel, value.prefValue)
        }

    val temperature: Float?
        get() = appCtx.getPrefString(PreferKey.aiTemperature, "0.2")
            ?.toFloatOrNull()
            ?.coerceIn(0f, 2f)

    var purifyAutoApply: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyAutoApply, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyAutoApply, value)
        }

    var purifyExceptionIntercept: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyExceptionIntercept, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyExceptionIntercept, value)
        }

    var purifyParagraphLimit: Int
        get() = appCtx.getPrefInt(
            PreferKey.aiPurifyParagraphLimit,
            DEFAULT_PURIFY_PARAGRAPH_LIMIT
        ).coerceIn(MIN_PURIFY_PARAGRAPH_LIMIT, MAX_PURIFY_PARAGRAPH_LIMIT)
        set(value) {
            appCtx.putPrefInt(
                PreferKey.aiPurifyParagraphLimit,
                value.coerceIn(MIN_PURIFY_PARAGRAPH_LIMIT, MAX_PURIFY_PARAGRAPH_LIMIT)
            )
        }

    var purifyChapterAutoApply: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyChapterAutoApply, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyChapterAutoApply, value)
        }

    var purifyChapterExceptionIntercept: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyChapterExceptionIntercept, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyChapterExceptionIntercept, value)
        }

    var purifyChapterSegmentLimit: Int
        get() = appCtx.getPrefInt(
            PreferKey.aiPurifyChapterSegmentLimit,
            DEFAULT_PURIFY_CHAPTER_SEGMENT_LIMIT
        ).coerceIn(MIN_PURIFY_CHAPTER_SEGMENT_LIMIT, MAX_PURIFY_CHAPTER_SEGMENT_LIMIT)
        set(value) {
            appCtx.putPrefInt(
                PreferKey.aiPurifyChapterSegmentLimit,
                value.coerceIn(MIN_PURIFY_CHAPTER_SEGMENT_LIMIT, MAX_PURIFY_CHAPTER_SEGMENT_LIMIT)
            )
        }

    var purifyChapterSampleLimit: Int
        get() = appCtx.getPrefInt(
            PreferKey.aiPurifyChapterSampleLimit,
            DEFAULT_PURIFY_CHAPTER_SAMPLE_LIMIT
        ).coerceIn(MIN_PURIFY_CHAPTER_SAMPLE_LIMIT, MAX_PURIFY_CHAPTER_SAMPLE_LIMIT)
        set(value) {
            appCtx.putPrefInt(
                PreferKey.aiPurifyChapterSampleLimit,
                value.coerceIn(MIN_PURIFY_CHAPTER_SAMPLE_LIMIT, MAX_PURIFY_CHAPTER_SAMPLE_LIMIT)
            )
        }

    var purifyChapterConcurrencyLimit: Int
        get() = appCtx.getPrefInt(
            PreferKey.aiPurifyChapterConcurrencyLimit,
            DEFAULT_PURIFY_CHAPTER_CONCURRENCY_LIMIT
        ).coerceIn(MIN_PURIFY_CHAPTER_CONCURRENCY_LIMIT, MAX_PURIFY_CHAPTER_CONCURRENCY_LIMIT)
        set(value) {
            appCtx.putPrefInt(
                PreferKey.aiPurifyChapterConcurrencyLimit,
                value.coerceIn(
                    MIN_PURIFY_CHAPTER_CONCURRENCY_LIMIT,
                    MAX_PURIFY_CHAPTER_CONCURRENCY_LIMIT
                )
            )
        }

    var purifyChapterRetryCount: Int
        get() = appCtx.getPrefInt(
            PreferKey.aiPurifyChapterRetryCount,
            DEFAULT_PURIFY_CHAPTER_RETRY_COUNT
        ).coerceIn(MIN_PURIFY_CHAPTER_RETRY_COUNT, MAX_PURIFY_CHAPTER_RETRY_COUNT)
        set(value) {
            appCtx.putPrefInt(
                PreferKey.aiPurifyChapterRetryCount,
                value.coerceIn(MIN_PURIFY_CHAPTER_RETRY_COUNT, MAX_PURIFY_CHAPTER_RETRY_COUNT)
            )
        }

    var purifyChapterRuleTypo: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyChapterRuleTypo, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyChapterRuleTypo, value)
        }

    var purifyChapterRuleNoise: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyChapterRuleNoise, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyChapterRuleNoise, value)
        }

    var purifyChapterRuleAd: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.aiPurifyChapterRuleAd, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.aiPurifyChapterRuleAd, value)
        }

    fun isPurifyChapterRuleTypeEnabled(type: String): Boolean {
        return when (type.trim().lowercase()) {
            "typo" -> purifyChapterRuleTypo
            "noise" -> purifyChapterRuleNoise
            "ad" -> purifyChapterRuleAd
            else -> false
        }
    }

    fun requirePurifyModel(): AiModelSelection {
        val providerId = purifyProviderId
        val modelId = purifyModelId
        check(providerId.isNotBlank() && modelId.isNotBlank()) {
            "请先在 AI 设置 > 模型设置 > 净化模型 中选择模型"
        }
        return AiModelSelection(providerId, modelId)
    }

    fun savePurifyModel(providerId: String, modelId: String) {
        purifyProviderId = providerId
        purifyModelId = modelId
    }

    fun requireAssistantModel(): AiModelSelection {
        val providerId = assistantProviderId
        val modelId = assistantModelId
        check(providerId.isNotBlank() && modelId.isNotBlank()) {
            "请先在 AI 设置 > 模型设置 > AI助理 中选择聊天模型"
        }
        return AiModelSelection(providerId, modelId)
    }

    fun saveAssistantModel(providerId: String, modelId: String) {
        assistantProviderId = providerId
        assistantModelId = modelId
    }

    fun assistantChatParams(supportsReasoning: Boolean): AiTextParams {
        val level = assistantReasoningLevel.takeIf { supportsReasoning } ?: AiReasoningLevel.OFF
        val maxTokens = (4096 + level.reasoningReserve(4096)).coerceIn(4096, 8192)
        return AiTextParams(
            temperature = temperature,
            maxTokens = maxTokens,
            enableThinking = level != AiReasoningLevel.OFF && level != AiReasoningLevel.AUTO,
            disableThinking = level == AiReasoningLevel.OFF,
            reasoningEffort = level.reasoningEffort
        )
    }

    fun purifyParagraphParams(
        inputLength: Int,
        supportsReasoning: Boolean
    ): AiTextParams {
        val baseBudget = (inputLength * 2 + 256).coerceIn(512, 4096)
        return purifyTextParams(
            baseOutputBudget = baseBudget,
            outputBudgetLimit = PARAGRAPH_OUTPUT_BUDGET_LIMIT,
            supportsReasoning = supportsReasoning
        )
    }

    fun purifyChapterRuleParams(
        sourceLength: Int,
        paragraphCount: Int,
        supportsReasoning: Boolean
    ): AiTextParams {
        val baseBudget = (sourceLength + paragraphCount * 64 + 512).coerceIn(1024, 8192)
        return purifyTextParams(
            baseOutputBudget = baseBudget,
            outputBudgetLimit = CHAPTER_OUTPUT_BUDGET_LIMIT,
            supportsReasoning = supportsReasoning
        )
    }

    private fun purifyTextParams(
        baseOutputBudget: Int,
        outputBudgetLimit: Int,
        supportsReasoning: Boolean
    ): AiTextParams {
        val level = purifyReasoningLevel.takeIf { supportsReasoning } ?: AiReasoningLevel.OFF
        val outputBudget = (baseOutputBudget + level.reasoningReserve(baseOutputBudget))
            .coerceIn(baseOutputBudget, outputBudgetLimit)
        return AiTextParams(
            temperature = 0f,
            maxTokens = outputBudget,
            enableThinking = level != AiReasoningLevel.OFF && level != AiReasoningLevel.AUTO,
            disableThinking = level == AiReasoningLevel.OFF,
            reasoningEffort = level.reasoningEffort
        )
    }

    fun currentSetting(): AiProviderSetting {
        return AiProviderStore.activeProvider()
    }

    fun savedModels(): List<AiModel> {
        val setting = currentSetting()
        val availableIds = if (setting.availableModelSelectionInitialized) {
            setting.availableModelIds.toSet()
        } else {
            setting.models.map { it.id }.toSet()
        }
        return setting.models.filter { it.id in availableIds }
    }

    fun saveModels(models: List<AiModel>) {
        val setting = currentSetting()
        AiProviderStore.saveProvider(
            setting.copy(
                models = models,
                availableModelIds = models.map { it.id },
                availableModelSelectionInitialized = true
            )
        )
    }

    fun clearModels() {
        val setting = currentSetting()
        AiProviderStore.saveProvider(
            setting.copy(
                models = emptyList(),
                availableModelIds = emptyList(),
                availableModelSelectionInitialized = true
            )
        )
    }
}

data class AiModelSelection(
    val providerId: String,
    val modelId: String
)

enum class AiReasoningLevel(
    val prefValue: String,
    val reasoningEffort: String?
) {
    OFF("off", null),
    AUTO("auto", null),
    LOW("low", "low"),
    MEDIUM("medium", "medium"),
    HIGH("high", "high"),
    ULTRA("ultra", "high");

    fun reasoningReserve(baseOutputBudget: Int): Int {
        return when (this) {
            OFF -> 0
            AUTO -> baseOutputBudget / 2 + 256
            LOW -> baseOutputBudget * 3 / 4 + 256
            MEDIUM -> baseOutputBudget + 256
            HIGH -> baseOutputBudget * 3 / 2 + 512
            ULTRA -> baseOutputBudget * 2 + 512
        }
    }

    companion object {
        fun from(value: String?): AiReasoningLevel {
            return entries.firstOrNull { it.prefValue == value } ?: OFF
        }
    }
}
