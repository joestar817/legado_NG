package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 契约测试：EPUB 三态 profile 迁移映射与不变量（§3.3 / §3.6）。
 */
class EpubFormattingProfileTest {

    private val resolver: EpubFormattingProfileResolver = ReferenceProfileResolver

    private fun rules(all: EpubFeatureRule) = EpubFeatureRules(all, all, all, all, all, all, all)

    @Test
    fun `all true migrates to respect profile`() {
        val profile = resolver.resolve(List(7) { true })
        assertEquals(EpubPublisherProfile.RESPECT, profile.profile)
        assertTrue(profile.rules.allRespect)
    }

    @Test
    fun `all false migrates to override profile`() {
        val profile = resolver.resolve(List(7) { false })
        assertEquals(EpubPublisherProfile.OVERRIDE, profile.profile)
        assertTrue(profile.rules.allOverride)
    }

    @Test
    fun `mixed migrates to custom profile`() {
        val profile = resolver.resolve(listOf(true, false, true, true, true, true, true))
        assertEquals(EpubPublisherProfile.CUSTOM, profile.profile)
    }

    @Test
    fun `respect profile invariant all seven rules respect`() {
        val profile = EpubFormattingProfile(
            profile = EpubPublisherProfile.RESPECT,
            rules = rules(EpubFeatureRule.RESPECT),
        )
        assertTrue(profile.rules.allRespect)
    }

    @Test
    fun `override profile invariant all seven rules override`() {
        val profile = EpubFormattingProfile(
            profile = EpubPublisherProfile.OVERRIDE,
            rules = rules(EpubFeatureRule.OVERRIDE),
        )
        assertTrue(profile.rules.allOverride)
    }

    @Test
    fun `custom profile rules are authoritative individually`() {
        val profile = EpubFormattingProfile(
            profile = EpubPublisherProfile.CUSTOM,
            rules = EpubFeatureRules(
                font = EpubFeatureRule.RESPECT,
                size = EpubFeatureRule.OVERRIDE,
                color = EpubFeatureRule.RESPECT,
                decoration = EpubFeatureRule.RESPECT,
                paragraph = EpubFeatureRule.RESPECT,
                title = EpubFeatureRule.RESPECT,
                initial = EpubFeatureRule.RESPECT,
            ),
        )
        assertEquals(EpubFeatureRule.RESPECT, profile.rules.font)
        assertEquals(EpubFeatureRule.OVERRIDE, profile.rules.size)
    }
}

private object ReferenceProfileResolver : EpubFormattingProfileResolver {
    override fun resolve(respectFlags: List<Boolean>): EpubFormattingProfile {
        require(respectFlags.size == 7) { "契约：7 项 feature rules" }
        fun rule(flag: Boolean) = if (flag) EpubFeatureRule.RESPECT else EpubFeatureRule.OVERRIDE
        val rules = EpubFeatureRules(
            font = rule(respectFlags[0]),
            size = rule(respectFlags[1]),
            color = rule(respectFlags[2]),
            decoration = rule(respectFlags[3]),
            paragraph = rule(respectFlags[4]),
            title = rule(respectFlags[5]),
            initial = rule(respectFlags[6]),
        )
        val profile = when {
            rules.allRespect -> EpubPublisherProfile.RESPECT
            rules.allOverride -> EpubPublisherProfile.OVERRIDE
            else -> EpubPublisherProfile.CUSTOM
        }
        return EpubFormattingProfile(profile = profile, rules = rules)
    }
}
