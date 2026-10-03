package io.legado.app.ui.book.read.page.provider

import android.graphics.Typeface
import androidx.collection.LruCache

/** Phase 3 字体缓存前置（§3.7-5）：styled-range 字体以 (path, weight, italic) 为键缓存。 */
internal data class StyledTypefaceCacheKey(
    val path: String,
    val weight: Int,
    val italic: Boolean,
)

/**
 * 键值 LruCache 包装，JVM 可测（androidx.collection.LruCache 为纯 Java 实现）。
 * 只缓存成功解析的 Typeface；解析失败不缓存（下次重试）。
 */
internal class KeyedTypefaceCache<T : Any>(maxEntries: Int = DEFAULT_MAX_ENTRIES) {
    private val cache = LruCache<StyledTypefaceCacheKey, T>(maxEntries)

    fun get(key: StyledTypefaceCacheKey): T? = cache.get(key)

    fun put(key: StyledTypefaceCacheKey, value: T): T? = cache.put(key, value)

    fun clear() = cache.evictAll()

    fun size(): Int = cache.size()

    companion object {
        const val DEFAULT_MAX_ENTRIES = 32
    }
}

/** 阅读正文/标题 styled-range 字体缓存（§3.7-5）。 */
internal object StyledTypefaceCache {
    private val cache = KeyedTypefaceCache<Typeface>()

    fun get(path: String, weight: Int, italic: Boolean): Typeface? =
        cache.get(StyledTypefaceCacheKey(path, weight, italic))

    fun put(path: String, weight: Int, italic: Boolean, typeface: Typeface) {
        cache.put(StyledTypefaceCacheKey(path, weight, italic), typeface)
    }

    fun clear() = cache.clear()
}
