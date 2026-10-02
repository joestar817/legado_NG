package io.legado.app.help.config

/**
 * Phase 0 契约：EPUB 三态用户策略 + 八项独立 precedence rules。
 *
 * 契约文档：docs/reading-settings-redesign-plan.md §3.3 / §3.6
 *
 * publisher 是**第 8 条独立规则**（控制 App 基础排版是否全面接管，渲染层有独立作用），
 * 不是凌驾于其余 7 项之上的主闸。
 *
 * - profile=RESPECT  → 8 项全部 RESPECT
 * - profile=OVERRIDE → 8 项全部 OVERRIDE
 * - profile=CUSTOM   → 8 项各自独立决定（顶层档位不参与属性解析）
 *
 * 迁移映射：8 全 true → RESPECT；8 全 false → OVERRIDE；其余 → CUSTOM。
 */

enum class EpubPublisherProfile { RESPECT, OVERRIDE, CUSTOM }

enum class EpubFeatureRule { RESPECT, OVERRIDE }

data class EpubFeatureRules(
    val publisher: EpubFeatureRule,
    val font: EpubFeatureRule,
    val size: EpubFeatureRule,
    val color: EpubFeatureRule,
    val decoration: EpubFeatureRule,
    val paragraph: EpubFeatureRule,
    val title: EpubFeatureRule,
    val initial: EpubFeatureRule,
) {
    private val all: List<EpubFeatureRule>
        get() = listOf(publisher, font, size, color, decoration, paragraph, title, initial)

    val allRespect: Boolean get() = all.all { it == EpubFeatureRule.RESPECT }

    val allOverride: Boolean get() = all.all { it == EpubFeatureRule.OVERRIDE }
}

data class EpubFormattingProfile(
    val profile: EpubPublisherProfile,
    val rules: EpubFeatureRules,
)

fun interface EpubFormattingProfileResolver {
    /** 由 8 项布尔值（true=respect，顺序 publisher/font/size/color/decoration/paragraph/title/initial）解析为三态 profile。 */
    fun resolve(respectFlags: List<Boolean>): EpubFormattingProfile
}

fun interface EpubFormattingProfileWriter {
    /**
     * 写入方向：profile → 8 项布尔值。
     * RESPECT → 8×true；OVERRIDE → 8×false；CUSTOM → 原样返回 current（恒等 = 不突变存储，
     * 「打开/展开 Custom 零渲染变化」由本契约保证，而不是靠 UI 自觉）。
     */
    fun respectFlagsFor(profile: EpubPublisherProfile, current: List<Boolean>): List<Boolean>
}

/** 生产实现：UI（EpubLayoutSheet）与契约测试共同消费。 */
object EpubFormattingProfileContract : EpubFormattingProfileResolver, EpubFormattingProfileWriter {
    override fun resolve(respectFlags: List<Boolean>): EpubFormattingProfile {
        require(respectFlags.size == 8) { "契约：8 条 precedence rules" }
        fun rule(flag: Boolean) = if (flag) EpubFeatureRule.RESPECT else EpubFeatureRule.OVERRIDE
        val rules = EpubFeatureRules(
            publisher = rule(respectFlags[0]),
            font = rule(respectFlags[1]),
            size = rule(respectFlags[2]),
            color = rule(respectFlags[3]),
            decoration = rule(respectFlags[4]),
            paragraph = rule(respectFlags[5]),
            title = rule(respectFlags[6]),
            initial = rule(respectFlags[7]),
        )
        val profile = when {
            rules.allRespect -> EpubPublisherProfile.RESPECT
            rules.allOverride -> EpubPublisherProfile.OVERRIDE
            else -> EpubPublisherProfile.CUSTOM
        }
        return EpubFormattingProfile(profile = profile, rules = rules)
    }

    override fun respectFlagsFor(
        profile: EpubPublisherProfile,
        current: List<Boolean>,
    ): List<Boolean> {
        require(current.size == 8) { "契约：8 条 precedence rules" }
        return when (profile) {
            EpubPublisherProfile.RESPECT -> List(8) { true }
            EpubPublisherProfile.OVERRIDE -> List(8) { false }
            EpubPublisherProfile.CUSTOM -> current
        }
    }
}
