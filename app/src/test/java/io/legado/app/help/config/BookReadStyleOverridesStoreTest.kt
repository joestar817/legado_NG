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
    fun `materialization is transactional and retires legacy after success`() {
        val owner = legacyBook("B")
        val before = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("B", before.value)
        assertEquals(ReadValueSource.PRESET, before.source)

        val overrides = store.materializePinnedBaseIfNeeded(owner)
        assertTrue(overrides != null)
        assertNull(owner.config.independentReadStyle)
        assertEquals(ReadBasePresetMode.PINNED, overrides?.basePreset?.toReadBasePreset()?.mode)
        assertEquals("B", overrides?.basePreset?.snapshot?.defaultFont)

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
    fun `follow global materialization retires legacy and follows global preset`() {
        val owner = legacyBook("B")
        store.materializeFollowGlobal(owner)
        assertNull(owner.config.independentReadStyle)
        val effective = store.effectiveDefaultFont(owner, globalFont = "A")
        assertEquals("A", effective.value)
        assertEquals(ReadValueSource.PRESET, effective.source)
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
