package io.legado.app.help.source

import androidx.annotation.Keep
import io.legado.app.data.appDb
import io.legado.app.data.dao.deleteByKeysChunked
import io.legado.app.help.CacheManager
import io.legado.app.utils.ACache
import splitties.init.appCtx
import java.io.File

/** 仅用于清理升级前按书源 URL 隔离的旧缓存，不再注入源脚本。 */
@Keep
@Suppress("unused")
class BookSourceCacheStore(
    sourceUrl: String
) {

    private val namespace = BookSourceStorageScope.namespace(sourceUrl)
    private val storagePrefix = "book_source_cache_$namespace:"
    private val registryPrefix = "book_source_cache_registry_$namespace:"
    private val fileCache by lazy {
        ACache.get(File(appCtx.cacheDir, "bookSourceCache${File.separator}$namespace"))
    }

    private fun scopedKey(key: String): String = storagePrefix + key

    private fun remember(scopedKey: String) {
        val registryKey = registryPrefix + BookSourceStorageScope.namespace(scopedKey)
        CacheManager.put(registryKey, scopedKey)
    }

    @JvmOverloads
    fun put(key: String, value: Any, saveTime: Int = 0) {
        val scopedKey = scopedKey(key)
        remember(scopedKey)
        if (value is ByteArray) {
            fileCache.put(scopedKey, value, saveTime)
        } else {
            CacheManager.put(scopedKey, value, saveTime)
        }
    }

    fun putMemory(key: String, value: Any) {
        CacheManager.putMemory(scopedKey(key), value)
    }

    fun getFromMemory(key: String): Any? = CacheManager.getFromMemory(scopedKey(key))

    fun deleteMemory(key: String) {
        CacheManager.deleteMemory(scopedKey(key))
    }

    fun get(key: String): String? = CacheManager.get(scopedKey(key))

    fun get(key: String, onlyDisk: Boolean): String? {
        return CacheManager.get(scopedKey(key), onlyDisk)
    }

    fun getInt(key: String): Int? = CacheManager.getInt(scopedKey(key))

    fun getLong(key: String): Long? = CacheManager.getLong(scopedKey(key))

    fun getDouble(key: String): Double? = CacheManager.getDouble(scopedKey(key))

    fun getFloat(key: String): Float? = CacheManager.getFloat(scopedKey(key))

    fun getByteArray(key: String): ByteArray? = fileCache.getAsBinary(scopedKey(key))

    @JvmOverloads
    fun putFile(key: String, value: String, saveTime: Int = 0) {
        val scopedKey = scopedKey(key)
        remember(scopedKey)
        fileCache.put(scopedKey, value, saveTime)
    }

    fun getFile(key: String): String? = fileCache.getAsString(scopedKey(key))

    fun delete(key: String) {
        val scopedKey = scopedKey(key)
        CacheManager.delete(scopedKey)
        fileCache.remove(scopedKey)
    }

    internal companion object {
        fun clear(sourceUrl: String) {
            clear(listOf(sourceUrl), appDb.cacheDao.allKeys())
        }

        fun clear(sourceUrls: Collection<String>, cacheKeys: Collection<String>) {
            if (sourceUrls.isEmpty()) return
            val namespaces = sourceUrls.distinct().map(BookSourceStorageScope::namespace)
            val storagePrefixes = namespaces.map { "book_source_cache_$it:" }
            val registryPrefixes = namespaces.map { "book_source_cache_registry_$it:" }
            val prefixes = storagePrefixes + registryPrefixes
            val registryValues = appDb.cacheDao
                .getByPrefix("book_source_cache_registry_")
                .filter { registry -> registryPrefixes.any(registry.key::startsWith) }
                .mapNotNull { it.value }
            val scopedKeys = cacheKeys.filter { key ->
                key.startsWith("book_source_cache_") && prefixes.any(key::startsWith)
            }

            appDb.cacheDao.deleteByKeysChunked((scopedKeys + registryValues).distinct())
            CacheManager.deleteMemoryByPrefixes(prefixes)
            registryValues.forEach(CacheManager::deleteMemory)
            val globalFileCache = ACache.get()
            registryValues.forEach(globalFileCache::remove)
            namespaces.forEach { namespace ->
                ACache.get(
                    File(appCtx.cacheDir, "bookSourceCache${File.separator}$namespace")
                ).clear()
            }
        }
    }
}
