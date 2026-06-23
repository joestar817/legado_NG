package io.legado.app.help.ai

import io.legado.app.constant.PreferKey
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
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
