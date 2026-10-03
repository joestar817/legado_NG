package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §3.7-5 字体缓存契约：(path, weight, italic) 键维度 + LRU 淘汰 + 只缓存成功值。
 * 用 String 值在 JVM 上验证 KeyedTypefaceCache（androidx.collection.LruCache 为纯 Java 实现）。
 */
class StyledTypefaceCacheTest {

    @Test
    fun `same key returns cached value`() {
        val cache = KeyedTypefaceCache<String>(maxEntries = 4)
        val key = StyledTypefaceCacheKey("/fonts/a.ttf", 700, false)
        assertNull(cache.get(key))
        cache.put(key, "typeface-a-700")
        assertEquals("typeface-a-700", cache.get(key))
        assertEquals(1, cache.size())
    }

    @Test
    fun `path weight and italic are distinct key dimensions`() {
        val cache = KeyedTypefaceCache<String>(maxEntries = 8)
        cache.put(StyledTypefaceCacheKey("/fonts/a.ttf", 400, false), "a-400")
        cache.put(StyledTypefaceCacheKey("/fonts/a.ttf", 700, false), "a-700")
        cache.put(StyledTypefaceCacheKey("/fonts/a.ttf", 400, true), "a-400-i")
        cache.put(StyledTypefaceCacheKey("/fonts/b.ttf", 400, false), "b-400")
        assertEquals("a-400", cache.get(StyledTypefaceCacheKey("/fonts/a.ttf", 400, false)))
        assertEquals("a-700", cache.get(StyledTypefaceCacheKey("/fonts/a.ttf", 700, false)))
        assertEquals("a-400-i", cache.get(StyledTypefaceCacheKey("/fonts/a.ttf", 400, true)))
        assertEquals("b-400", cache.get(StyledTypefaceCacheKey("/fonts/b.ttf", 400, false)))
        assertEquals(4, cache.size())
    }

    @Test
    fun `put overwrites same key`() {
        val cache = KeyedTypefaceCache<String>(maxEntries = 2)
        val key = StyledTypefaceCacheKey("/fonts/a.ttf", 400, false)
        cache.put(key, "old")
        cache.put(key, "new")
        assertEquals("new", cache.get(key))
        assertEquals(1, cache.size())
    }

    @Test
    fun `lru evicts eldest entry`() {
        val cache = KeyedTypefaceCache<String>(maxEntries = 2)
        cache.put(StyledTypefaceCacheKey("/fonts/a.ttf", 400, false), "a")
        cache.put(StyledTypefaceCacheKey("/fonts/b.ttf", 400, false), "b")
        // 访问 a，使其变为最近使用；再写入 c 时应淘汰 b
        cache.get(StyledTypefaceCacheKey("/fonts/a.ttf", 400, false))
        cache.put(StyledTypefaceCacheKey("/fonts/c.ttf", 400, false), "c")
        assertEquals("a", cache.get(StyledTypefaceCacheKey("/fonts/a.ttf", 400, false)))
        assertNull(cache.get(StyledTypefaceCacheKey("/fonts/b.ttf", 400, false)))
        assertEquals("c", cache.get(StyledTypefaceCacheKey("/fonts/c.ttf", 400, false)))
    }

    @Test
    fun `clear empties cache`() {
        val cache = KeyedTypefaceCache<String>(maxEntries = 4)
        cache.put(StyledTypefaceCacheKey("/fonts/a.ttf", 400, false), "a")
        cache.clear()
        assertEquals(0, cache.size())
        assertNull(cache.get(StyledTypefaceCacheKey("/fonts/a.ttf", 400, false)))
    }
}
