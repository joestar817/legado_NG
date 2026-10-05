package io.legado.app.help.config

import io.legado.app.data.entities.Book
import io.legado.app.utils.GSON
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 2d-1 契约测试：BookReadStyleOverridesStore（Gate A/B 语义）。
 *
 * 验收闭环：
 *   Global font = A；Legacy book font = B
 *   首次编辑 → materialize pinned base = B（legacy 退役）
 *   book font override = C → effective = C
 *   ↺ 重置字体 → effective = B
 *   Reset all → effective = A
 */
class BookReadStyleOverridesStoreTest {

    private val stored = linkedMapOf<String, Pair<String?, String?>>()
    private val store = BookReadStyleOverridesStore { url, overrides, legacy ->
        stored[url] = overrides to legacy
    }

    private fun legacyBook(font: String): Book = Book(bookUrl = "a").apply {
        config.independentReadStyle = GSON.toJson(ReadBookConfig.Config(textFont = font))
    }

    @Test
    fun `materialization keeps legacy as implicit full base`() {
        val owner = legacyBook("B").apply {
            config.independentReadStyle = GSON.toJson(
                ReadBookConfig.Config(textFont = "B", textSize = 22, bgStr = "#112233")
            )
        }
        val before = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("B", before.value)
        assertEquals(ReadValueSource.PRESET, before.source)

        val overrides = store.materializePinnedBaseIfNeeded(owner)
        assertTrue(overrides != null)
        // 修订后的 Gate B：legacy 保留为隐式全量基准，非字体属性（textSize/bgStr）仍由 legacy 承载
        val legacy = BookReadStyleSession.decode(owner.config.independentReadStyle)
        assertEquals(22, legacy?.textSize)
        assertEquals("#112233", legacy?.bgStr)
        assertEquals(ReadBasePresetMode.PINNED, overrides?.basePreset?.toReadBasePreset()?.mode)
        assertEquals("B", overrides?.basePreset?.snapshot?.defaultFont)
        assertEquals(emptyMap<ReadValueScope, String>(), overrides?.basePreset?.snapshot?.scriptFonts)

        val scripted = legacyBook("B").apply {
            config.independentReadStyle = GSON.toJson(
                ReadBookConfig.Config(
                    textFont = "B",
                    scriptFonts = SparseFontOverrides(cjk = "CJK.ttf"),
                )
            )
        }
        val scriptedOverrides = store.materializePinnedBaseIfNeeded(scripted)
        assertEquals(
            mapOf(ReadValueScope.CJK to "CJK.ttf"),
            scriptedOverrides?.basePreset?.snapshot?.scriptFonts,
        )

        val after = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("B", after.value)
        assertEquals(ReadValueSource.PRESET, after.source)
    }

    @Test
    fun `write default font overrides pinned base`() {
        val owner = legacyBook("B")
        store.materializePinnedBaseIfNeeded(owner)
        store.writeDefaultFont(owner, "C")
        val effective = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("C", effective.value)
        assertEquals(ReadValueSource.THIS_BOOK, effective.source)
    }

    @Test
    fun `write scope font stores single script dimension and clears it`() {
        val owner = legacyBook("B")
        store.materializePinnedBaseIfNeeded(owner)
        store.writeScope(owner, ReadValueScope.CJK, "CJK.ttf")
        assertEquals("CJK.ttf", store.current(owner)?.font?.forScope(ReadValueScope.CJK))
        assertNull(store.current(owner)?.font?.forScope(ReadValueScope.LATIN))
        store.writeScope(owner, ReadValueScope.CJK, null)
        assertNull(store.current(owner)?.font?.forScope(ReadValueScope.CJK))
    }

    @Test
    fun `property reset falls back to pinned base`() {
        val owner = legacyBook("B")
        store.materializePinnedBaseIfNeeded(owner)
        store.writeDefaultFont(owner, "C")
        store.writeDefaultFont(owner, null)
        val effective = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("B", effective.value)
        assertEquals(ReadValueSource.PRESET, effective.source)
    }

    @Test
    fun `reset all clears both layers and falls to global`() {
        val owner = legacyBook("B")
        store.materializePinnedBaseIfNeeded(owner)
        store.writeDefaultFont(owner, "C")
        store.resetAll(owner)
        assertNull(owner.config.independentOverrides)
        assertNull(owner.config.independentReadStyle)
        val effective = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("A", effective.value)
        assertEquals(ReadValueSource.GLOBAL, effective.source)
    }

    @Test
    fun `follow global materialization keeps legacy and follows global preset`() {
        val owner = legacyBook("B")
        store.materializeFollowGlobal(owner)
        assertTrue(owner.config.independentReadStyle != null)
        val effective = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("A", effective.value)
        assertEquals(ReadValueSource.PRESET, effective.source)
    }

    @Test
    fun `follow global keeps existing book font overrides`() {
        val owner = legacyBook("B")
        store.materializePinnedBaseIfNeeded(owner)
        store.writeScope(owner, ReadValueScope.CJK, "CJK.ttf")
        store.materializeFollowGlobal(owner)
        assertEquals("CJK.ttf", store.current(owner)?.font?.forScope(ReadValueScope.CJK))
        assertEquals(BookBasePreset.MODE_FOLLOW_GLOBAL, store.current(owner)?.basePreset?.mode)
    }

    @Test
    fun `restore snapshot rolls back book script font overrides`() {
        val owner = legacyBook("B")
        store.materializePinnedBaseIfNeeded(owner)
        store.writeScope(owner, ReadValueScope.CJK, "CJK.ttf")
        val snapshot = store.snapshotJson(owner)
        store.writeScope(owner, ReadValueScope.LATIN, "Latin.ttf")
        assertEquals("Latin.ttf", store.current(owner)?.font?.forScope(ReadValueScope.LATIN))
        store.restoreSnapshot(owner, snapshot)
        assertEquals("CJK.ttf", store.current(owner)?.font?.forScope(ReadValueScope.CJK))
        assertNull(store.current(owner)?.font?.forScope(ReadValueScope.LATIN))
        assertEquals(snapshot, stored[owner.bookUrl]?.first)
    }

    @Test
    fun `restore snapshot to null clears overrides written this session`() {
        val owner = Book(bookUrl = "a")
        assertNull(store.snapshotJson(owner))
        store.writeScope(owner, ReadValueScope.OTHER, "Other.ttf")
        store.restoreSnapshot(owner, null)
        assertNull(owner.config.independentOverrides)
        assertNull(store.current(owner)?.font?.forScope(ReadValueScope.OTHER))
    }

    @Test
    fun `no legacy and no overrides writes sparse font directly`() {
        val owner = Book(bookUrl = "a")
        store.writeDefaultFont(owner, "X")
        val effective = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("X", effective.value)
        assertEquals(ReadValueSource.THIS_BOOK, effective.source)
    }
}
