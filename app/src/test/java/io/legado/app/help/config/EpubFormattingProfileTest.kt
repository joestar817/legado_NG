package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 0 契约测试：EPUB 三态 profile 迁移映射与不变量（§3.3 / §3.6）。
 *
 * publisher 是第 8 条独立规则：8 全 true → RESPECT；8 全 false → OVERRIDE；其余 → CUSTOM。
 */
class EpubFormattingProfileTest {

    private val resolver: EpubFormattingProfileResolver = EpubFormattingProfileContract

    private fun rules(all: EpubFeatureRule) =
        EpubFeatureRules(all, all, all, all, all, all, all, all)

    @Test
    fun `all true migrates to respect profile`() {
        val profile = resolver.resolve(List(8) { true })
        assertEquals(EpubPublisherProfile.RESPECT, profile.profile)
        assertTrue(profile.rules.allRespect)
    }

    @Test
    fun `all false migrates to override profile`() {
        val profile = resolver.resolve(List(8) { false })
        assertEquals(EpubPublisherProfile.OVERRIDE, profile.profile)
        assertTrue(profile.rules.allOverride)
    }

    @Test
    fun `mixed migrates to custom profile`() {
        val profile = resolver.resolve(listOf(true, false, true, true, true, true, true, true))
        assertEquals(EpubPublisherProfile.CUSTOM, profile.profile)
    }

    @Test
    fun `publisher false with all seven true is custom not respect`() {
        // (F, all-T)：publisher 独立于 7 项，渲染层行为与 (T, all-T) 不同
        val profile = resolver.resolve(listOf(false, true, true, true, true, true, true, true))
        assertEquals(EpubPublisherProfile.CUSTOM, profile.profile)
    }

    @Test
    fun `publisher false with mixed seven is custom not override`() {
        // (F, mixed)：存量状态必须可表达、可编辑，不能错标为 OVERRIDE
        val profile = resolver.resolve(listOf(false, true, false, true, true, true, true, true))
        assertEquals(EpubPublisherProfile.CUSTOM, profile.profile)
    }

    @Test
    fun `respect profile invariant all eight rules respect`() {
        val profile = EpubFormattingProfile(
            profile = EpubPublisherProfile.RESPECT,
            rules = rules(EpubFeatureRule.RESPECT),
        )
        assertTrue(profile.rules.allRespect)
    }

    @Test
    fun `override profile invariant all eight rules override`() {
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
                publisher = EpubFeatureRule.RESPECT,
                font = EpubFeatureRule.OVERRIDE,
                size = EpubFeatureRule.RESPECT,
                color = EpubFeatureRule.RESPECT,
                decoration = EpubFeatureRule.RESPECT,
                paragraph = EpubFeatureRule.RESPECT,
                title = EpubFeatureRule.RESPECT,
                initial = EpubFeatureRule.RESPECT,
            ),
        )
        assertEquals(EpubFeatureRule.RESPECT, profile.rules.publisher)
        assertEquals(EpubFeatureRule.OVERRIDE, profile.rules.font)
    }

    @Test
    fun `exhaustive 256 states map only via all-true all-false mixed`() {
        for (mask in 0..255) {
            val flags = (0 until 8).map { mask and (1 shl it) != 0 }
            val expected = when {
                flags.all { it } -> EpubPublisherProfile.RESPECT
                flags.none { it } -> EpubPublisherProfile.OVERRIDE
                else -> EpubPublisherProfile.CUSTOM
            }
            assertEquals("flags=$flags", expected, resolver.resolve(flags).profile)
        }
    }

    @Test
    fun `round trip flags to profile to flags is identity for all 256 states`() {
        // CUSTOM 恒等返回 current：展开 Custom 不突变存储；RESPECT/OVERRIDE 重写为全 T/全 F。
        for (mask in 0..255) {
            val flags = (0 until 8).map { mask and (1 shl it) != 0 }
            val profile = EpubFormattingProfileContract.resolve(flags)
            assertEquals(
                "flags=$flags",
                flags,
                EpubFormattingProfileContract.respectFlagsFor(profile.profile, flags),
            )
        }
    }

    @Test
    fun `missing epub preference overrides the book`() {
        val flags = EpubLayoutPreferences.decode(null)
        assertEquals(8, flags.size)
        assertFalse(flags.values.any { it })
        assertEquals(
            EpubPublisherProfile.OVERRIDE,
            EpubFormattingProfileContract.resolve(flags.values.toList()).profile,
        )
    }
}
