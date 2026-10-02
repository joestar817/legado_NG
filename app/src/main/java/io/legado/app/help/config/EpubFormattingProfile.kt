package io.legado.app.help.config

/**
 * Phase 0 契约：EPUB 三态用户策略 + 七项 feature rules（publisher 为主闸）。
 *
 * 契约文档：docs/reading-settings-redesign-plan.md §3.3 / §3.6
 *
 * - profile=RESPECT  → 7 项全部 RESPECT
 * - profile=OVERRIDE → 7 项全部 OVERRIDE
 * - profile=CUSTOM   → 7 项各自独立决定（顶层档位不参与属性解析）
 *
 * 迁移映射：all true → RESPECT；all false → OVERRIDE；mixed → CUSTOM。
 */

enum class EpubPublisherProfile { RESPECT, OVERRIDE, CUSTOM }

enum class EpubFeatureRule { RESPECT, OVERRIDE }

data class EpubFeatureRules(
    val font: EpubFeatureRule,
    val size: EpubFeatureRule,
    val color: EpubFeatureRule,
    val decoration: EpubFeatureRule,
    val paragraph: EpubFeatureRule,
    val title: EpubFeatureRule,
    val initial: EpubFeatureRule,
) {
    val allRespect: Boolean
        get() = listOf(font, size, color, decoration, paragraph, title, initial)
            .all { it == EpubFeatureRule.RESPECT }

    val allOverride: Boolean
        get() = listOf(font, size, color, decoration, paragraph, title, initial)
            .all { it == EpubFeatureRule.OVERRIDE }
}

data class EpubFormattingProfile(
    val profile: EpubPublisherProfile,
    val rules: EpubFeatureRules,
)

fun interface EpubFormattingProfileResolver {
    /** 由 7 项布尔值（true=respect）迁移/解析为三态 profile。 */
    fun resolve(respectFlags: List<Boolean>): EpubFormattingProfile
}
