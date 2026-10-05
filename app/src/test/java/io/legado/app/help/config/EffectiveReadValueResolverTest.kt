package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 0 契约测试：`effective(property, script, book) → (value, source)`。
 *
 * 用例来源：docs/reading-settings-redesign-plan.md §3.6 契约测试表。
 * 每个用例都断言 value 与 source（scope 一并断言）。
 *
 * 本文件是生产解析器 [EffectiveReadValueResolverContract]（Phase 2 落地）必须满足的可执行规格。
 */
class EffectiveReadValueResolverTest {

    private val resolver: EffectiveReadValueResolver = EffectiveReadValueResolverContract

    // region 非 EPUB：global default / book override

    @Test
    fun `1 global default wins`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                globalDefaultFont = "A",
            )
        )
        assertEquals(ResolvedReadValue("A", ReadValueSource.GLOBAL, ReadValueScope.DEFAULT), result)
    }

    @Test
    fun `2 book default override wins`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                bookDefaultFont = "B",
                globalDefaultFont = "A",
            )
        )
        assertEquals(ResolvedReadValue("B", ReadValueSource.THIS_BOOK, ReadValueScope.DEFAULT), result)
    }

    @Test
    fun `3 global script wins over global default`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                globalScriptFont = "C",
                globalDefaultFont = "A",
            )
        )
        assertEquals(ResolvedReadValue("C", ReadValueSource.GLOBAL, ReadValueScope.CJK), result)
    }

    @Test
    fun `4 book script override wins`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                bookScriptFont = "D",
                globalScriptFont = "C",
            )
        )
        assertEquals(ResolvedReadValue("D", ReadValueSource.THIS_BOOK, ReadValueScope.CJK), result)
    }

    @Test
    fun `18 book default override does not shadow script bucket`() {
        // 稀疏继承：本书 default 是 DEFAULT 基准桶，不拦 CJK 脚本桶；CJK 继续落到全局脚本档案。
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                bookDefaultFont = "X",
                globalScriptFont = "C",
            )
        )
        assertEquals(ResolvedReadValue("C", ReadValueSource.GLOBAL, ReadValueScope.CJK), result)
    }

    // endregion

    // region EPUB precedence

    @Test
    fun `5 epub respect publisher declared wins over book override`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                bookScriptFont = "D",
                globalScriptFont = "C",
                epub = ReadEpubContext(rule = ReadEpubRule.RESPECT, publisherFont = "P"),
            )
        )
        assertEquals(ResolvedReadValue("P", ReadValueSource.PUBLISHER, ReadValueScope.CJK), result)
    }

    @Test
    fun `6 epub override falls back to app value`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                bookScriptFont = "D",
                globalScriptFont = "C",
                epub = ReadEpubContext(rule = ReadEpubRule.OVERRIDE, publisherFont = "P"),
            )
        )
        assertEquals(ResolvedReadValue("D", ReadValueSource.THIS_BOOK, ReadValueScope.CJK), result)
    }

    @Test
    fun `7 epub custom respect uses publisher when declared`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                globalDefaultFont = "A",
                epub = ReadEpubContext(rule = ReadEpubRule.RESPECT, publisherFont = "P"),
            )
        )
        assertEquals(ResolvedReadValue("P", ReadValueSource.PUBLISHER, ReadValueScope.DEFAULT), result)
    }

    @Test
    fun `8 epub custom override with book cjk override resolves deterministically`() {
        // 前置：Global default=A，Global CJK=C，Book CJK=D，publisher font=P，Custom，rule[font]=override
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                bookScriptFont = "D",
                globalScriptFont = "C",
                globalDefaultFont = "A",
                epub = ReadEpubContext(rule = ReadEpubRule.OVERRIDE, publisherFont = "P"),
            )
        )
        assertEquals(ResolvedReadValue("D", ReadValueSource.THIS_BOOK, ReadValueScope.CJK), result)
    }

    @Test
    fun `8b epub custom override without book override falls to global script`() {
        // 前置：Global default=A，Global CJK=C，无 Book CJK，publisher font=P，Custom，rule[font]=override
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                globalScriptFont = "C",
                globalDefaultFont = "A",
                epub = ReadEpubContext(rule = ReadEpubRule.OVERRIDE, publisherFont = "P"),
            )
        )
        assertEquals(ResolvedReadValue("C", ReadValueSource.GLOBAL, ReadValueScope.CJK), result)
    }

    @Test
    fun `19 epub respect publisher declared wins over pinned base`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                basePreset = ReadBasePreset(
                    mode = ReadBasePresetMode.PINNED,
                    snapshot = ReadPresetSnapshot(
                        scriptFonts = mapOf(ReadValueScope.CJK to "C"),
                        defaultFont = "A",
                    ),
                ),
                epub = ReadEpubContext(rule = ReadEpubRule.RESPECT, publisherFont = "P"),
            )
        )
        assertEquals(ResolvedReadValue("P", ReadValueSource.PUBLISHER, ReadValueScope.CJK), result)
    }

    @Test
    fun `20 epub override falls back to pinned base snapshot`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                basePreset = ReadBasePreset(
                    mode = ReadBasePresetMode.PINNED,
                    snapshot = ReadPresetSnapshot(
                        scriptFonts = mapOf(ReadValueScope.CJK to "C"),
                        defaultFont = "A",
                    ),
                ),
                globalScriptFont = "G",
                epub = ReadEpubContext(rule = ReadEpubRule.OVERRIDE, publisherFont = "P"),
            )
        )
        assertEquals(ResolvedReadValue("C", ReadValueSource.PRESET, ReadValueScope.CJK), result)
    }

    // endregion

    // region 混合脚本

    @Test
    fun `9 mixed scripts resolve per scope`() {
        val latin = resolver.resolve(
            ReadValueContext(scope = ReadValueScope.LATIN, globalScriptFont = "A")
        )
        val cjk = resolver.resolve(
            ReadValueContext(scope = ReadValueScope.CJK, globalScriptFont = "C")
        )
        val other = resolver.resolve(
            ReadValueContext(scope = ReadValueScope.OTHER, globalScriptFont = "B")
        )
        assertEquals(ResolvedReadValue("A", ReadValueSource.GLOBAL, ReadValueScope.LATIN), latin)
        assertEquals(ResolvedReadValue("C", ReadValueSource.GLOBAL, ReadValueScope.CJK), cjk)
        assertEquals(ResolvedReadValue("B", ReadValueSource.GLOBAL, ReadValueScope.OTHER), other)
    }

    // endregion

    // region 脚本回退 / platform

    @Test
    fun `10 script missing falls to global default`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                globalDefaultFont = "A",
            )
        )
        assertEquals(ResolvedReadValue("A", ReadValueSource.GLOBAL, ReadValueScope.CJK), result)
    }

    @Test
    fun `10b default unavailable falls to platform`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                platformFont = "PlatformFont",
            )
        )
        assertEquals(
            ResolvedReadValue("PlatformFont", ReadValueSource.PLATFORM, ReadValueScope.CJK),
            result,
        )
    }

    @Test
    fun `10c platform fallback is total`() {
        // platformFont 非空默认值保证解析是全函数：空 context 也必须出 Platform。
        val result = resolver.resolve(ReadValueContext(scope = ReadValueScope.DEFAULT))
        assertEquals(
            ResolvedReadValue("platform", ReadValueSource.PLATFORM, ReadValueScope.DEFAULT),
            result,
        )
    }

    // endregion

    // region 稀疏覆盖语义

    @Test
    fun `11 explicit book value equal to global remains book source`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                bookDefaultFont = "A",
                globalDefaultFont = "A",
            )
        )
        assertEquals(ResolvedReadValue("A", ReadValueSource.THIS_BOOK, ReadValueScope.DEFAULT), result)
    }

    @Test
    fun `12 missing book override inherits global`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                globalDefaultFont = "A",
            )
        )
        assertEquals(ResolvedReadValue("A", ReadValueSource.GLOBAL, ReadValueScope.DEFAULT), result)
    }

    // endregion

    // region basePreset

    @Test
    fun `13 legacy book style acts as pinned snapshot base`() {
        // Legacy 整份拷贝建模为 pinned 快照基准（source=PRESET），与 Phase 5 归一化终态一致。
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                basePreset = ReadBasePreset(
                    mode = ReadBasePresetMode.PINNED,
                    snapshot = ReadPresetSnapshot(defaultFont = "LegacyFont"),
                ),
                globalDefaultFont = "A",
            )
        )
        assertEquals(
            ResolvedReadValue("LegacyFont", ReadValueSource.PRESET, ReadValueScope.DEFAULT),
            result,
        )
    }

    @Test
    fun `14 pinned base snapshot is isolated from global preset changes`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                basePreset = ReadBasePreset(
                    mode = ReadBasePresetMode.PINNED,
                    snapshot = ReadPresetSnapshot(defaultFont = "A-Snapshot"),
                ),
                globalDefaultFont = "B",
            )
        )
        assertEquals(
            ResolvedReadValue("A-Snapshot", ReadValueSource.PRESET, ReadValueScope.DEFAULT),
            result,
        )
    }

    @Test
    fun `15 follow global base falls through to global default`() {
        // 契约 #15：follow_global 时值同全局当前预设，来源标 Preset。
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                basePreset = ReadBasePreset(mode = ReadBasePresetMode.FOLLOW_GLOBAL),
                globalDefaultFont = "B",
            )
        )
        assertEquals(ResolvedReadValue("B", ReadValueSource.PRESET, ReadValueScope.DEFAULT), result)
    }

    @Test
    fun `follow global still labels global script archive as global`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                basePreset = ReadBasePreset(mode = ReadBasePresetMode.FOLLOW_GLOBAL),
                globalScriptFont = "C",
                globalDefaultFont = "B",
            )
        )
        assertEquals(ResolvedReadValue("C", ReadValueSource.GLOBAL, ReadValueScope.CJK), result)
    }

    @Test
    fun `17 pinned preset own script font resolves before its default`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.CJK,
                basePreset = ReadBasePreset(
                    mode = ReadBasePresetMode.PINNED,
                    snapshot = ReadPresetSnapshot(
                        scriptFonts = mapOf(ReadValueScope.CJK to "C"),
                        defaultFont = "A",
                    ),
                ),
                globalScriptFont = "G",
                globalDefaultFont = "A",
            )
        )
        assertEquals(ResolvedReadValue("C", ReadValueSource.PRESET, ReadValueScope.CJK), result)
    }

    // endregion

    // region EPUB 级联 caveat

    @Test
    fun `16 epub respect but publisher did not declare falls to app value`() {
        val result = resolver.resolve(
            ReadValueContext(
                scope = ReadValueScope.DEFAULT,
                globalDefaultFont = "A",
                epub = ReadEpubContext(rule = ReadEpubRule.RESPECT, publisherFont = null),
            )
        )
        assertEquals(ResolvedReadValue("A", ReadValueSource.GLOBAL, ReadValueScope.DEFAULT), result)
    }

    // endregion
}
