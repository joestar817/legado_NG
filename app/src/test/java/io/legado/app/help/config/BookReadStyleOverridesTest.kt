package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2b 契约测试：本书稀疏覆盖 + LegacyBookStyle 兼容层。
 */
class BookReadStyleOverridesTest {

    private val resolver = EffectiveReadValueResolverContract

    @Test
    fun `new overrides json round trips`() {
        val overrides = BookReadStyleOverrides(
            basePreset = BookBasePreset(mode = BookBasePreset.MODE_FOLLOW_GLOBAL),
            font = SparseFontOverrides(default = "A", cjk = "C"),
        )
        val json = BookReadStyleCompatibility.toJson(overrides)
        val decoded = BookReadStyleCompatibility.overridesFromJson(json)
        assertNotNull(decoded)
        assertEquals("A", decoded?.font?.default)
        assertEquals("C", decoded?.font?.cjk)
    }

    @Test
    fun `cjk override resolves as this book source`() {
        val overrides = BookReadStyleOverrides(font = SparseFontOverrides(cjk = "D"))
        val context = BookReadStyleCompatibility.contextFor(
            scope = ReadValueScope.CJK,
            overrides = overrides,
            legacyConfig = null,
            globalScriptFont = "C",
            globalDefaultFont = "A",
        )
        val result = resolver.resolve(context)
        assertEquals("D", result.value)
        assertEquals(ReadValueSource.THIS_BOOK, result.source)
    }

    @Test
    fun `book default override wins over global script`() {
        val overrides = BookReadStyleOverrides(font = SparseFontOverrides(default = "X"))
        val context = BookReadStyleCompatibility.contextFor(
            scope = ReadValueScope.CJK,
            overrides = overrides,
            legacyConfig = null,
            globalScriptFont = "C",
            globalDefaultFont = "A",
        )
        val result = resolver.resolve(context)
        assertEquals("X", result.value)
        assertEquals(ReadValueSource.THIS_BOOK, result.source)
    }

    @Test
    fun `legacy full copy acts as pinned snapshot base with preset source`() {
        val legacy = ReadBookConfig.Config(textFont = "LegacyFont")
        val context = BookReadStyleCompatibility.contextFor(
            scope = ReadValueScope.DEFAULT,
            overrides = null,
            legacyConfig = legacy,
            globalScriptFont = null,
            globalDefaultFont = "A",
        )
        val result = resolver.resolve(context)
        assertEquals("LegacyFont", result.value)
        assertEquals(ReadValueSource.PRESET, result.source)
    }

    @Test
    fun `no overrides and no legacy falls to global`() {
        val context = BookReadStyleCompatibility.contextFor(
            scope = ReadValueScope.CJK,
            overrides = null,
            legacyConfig = null,
            globalScriptFont = "C",
            globalDefaultFont = "A",
        )
        val result = resolver.resolve(context)
        assertEquals("C", result.value)
        assertEquals(ReadValueSource.GLOBAL, result.source)
    }

    @Test
    fun `follow global base resolves current preset with preset source`() {
        val overrides = BookReadStyleOverrides(
            basePreset = BookBasePreset(mode = BookBasePreset.MODE_FOLLOW_GLOBAL),
        )
        val context = BookReadStyleCompatibility.contextFor(
            scope = ReadValueScope.DEFAULT,
            overrides = overrides,
            legacyConfig = null,
            globalScriptFont = null,
            globalDefaultFont = "B",
        )
        val result = resolver.resolve(context)
        assertEquals("B", result.value)
        assertEquals(ReadValueSource.PRESET, result.source)
    }

    @Test
    fun `empty overrides are recognized as empty`() {
        assertTrue(BookReadStyleOverrides().isEmpty())
        assertTrue(SparseFontOverrides().isEmpty())
        assertNull(BookReadStyleCompatibility.overridesFromJson(null))
        assertNull(BookReadStyleCompatibility.overridesFromJson(""))
    }

    @Test
    fun `sparse overrides layer on top of legacy pinned base`() {
        // 存量旧拷贝 + 首次写入稀疏覆盖：未被覆盖的维度不得丢回全局（语义悬崖回归用例）。
        val overrides = BookReadStyleOverrides(font = SparseFontOverrides(cjk = "D"))
        val legacy = ReadBookConfig.Config(textFont = "LegacyFont")
        val cjk = resolver.resolve(
            BookReadStyleCompatibility.contextFor(
                scope = ReadValueScope.CJK,
                overrides = overrides,
                legacyConfig = legacy,
                globalScriptFont = "C",
                globalDefaultFont = "A",
            )
        )
        assertEquals("D", cjk.value)
        assertEquals(ReadValueSource.THIS_BOOK, cjk.source)

        val latin = resolver.resolve(
            BookReadStyleCompatibility.contextFor(
                scope = ReadValueScope.LATIN,
                overrides = overrides,
                legacyConfig = legacy,
                globalScriptFont = "C",
                globalDefaultFont = "A",
            )
        )
        assertEquals("LegacyFont", latin.value)
        assertEquals(ReadValueSource.PRESET, latin.source)
    }

    @Test
    fun `explicit base preset supersedes legacy pinned base`() {
        // 新模型写入显式基准后，旧拷贝的隐式基准被取代（2d 首次编辑物化基准的契约）。
        val overrides = BookReadStyleOverrides(
            basePreset = BookBasePreset(mode = BookBasePreset.MODE_FOLLOW_GLOBAL),
        )
        val legacy = ReadBookConfig.Config(textFont = "LegacyFont")
        val result = resolver.resolve(
            BookReadStyleCompatibility.contextFor(
                scope = ReadValueScope.DEFAULT,
                overrides = overrides,
                legacyConfig = legacy,
                globalScriptFont = null,
                globalDefaultFont = "G",
            )
        )
        assertEquals("G", result.value)
        assertEquals(ReadValueSource.PRESET, result.source)
    }

    @Test
    fun `legacy empty textFont is preserved as explicit system font`() {
        // 旧数据里 textFont="" 表示「本书显式用系统字体」，不可当作缺失回落全局。
        val legacy = ReadBookConfig.Config(textFont = "")
        val context = BookReadStyleCompatibility.contextFor(
            scope = ReadValueScope.DEFAULT,
            overrides = null,
            legacyConfig = legacy,
            globalScriptFont = null,
            globalDefaultFont = "A",
        )
        val result = resolver.resolve(context)
        assertEquals("", result.value)
        assertEquals(ReadValueSource.PRESET, result.source)
    }

    @Test
    fun `legacy config gson round trip preserves empty textFont`() {
        val json = io.legado.app.utils.GSON.toJson(ReadBookConfig.Config(textFont = ""))
        val decoded = BookReadStyleSession.decode(json)
        assertNotNull(decoded)
        assertEquals("", decoded?.textFont)
    }
}
