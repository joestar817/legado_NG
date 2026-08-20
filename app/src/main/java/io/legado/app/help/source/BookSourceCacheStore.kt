package io.legado.app.help.source

import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.help.CacheManager
import io.legado.app.help.WebCacheManager
import io.legado.app.utils.ACache
import splitties.init.appCtx
import java.io.File

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
            val namespace = BookSourceStorageScope.namespace(sourceUrl)
            val storagePrefix = "book_source_cache_$namespace:"
            val registryPrefix = "book_source_cache_registry_$namespace:"
            appDb.cacheDao.getByPrefix(registryPrefix).forEach { registry ->
                registry.value?.let(CacheManager::delete)
            }
            appDb.cacheDao.deleteByPrefix(storagePrefix)
            appDb.cacheDao.deleteByPrefix(registryPrefix)
            CacheManager.deleteMemoryByPrefix(storagePrefix)
            CacheManager.deleteMemoryByPrefix(registryPrefix)
            ACache.get(
                File(appCtx.cacheDir, "bookSourceCache${File.separator}$namespace")
            ).clear()
        }
    }
}

@Keep
@Suppress("unused")
class BookSourceWebCacheStore(sourceUrl: String) {

    private val delegate = BookSourceCacheStore(sourceUrl)

    @JavascriptInterface
    fun put(key: String, value: String) = delegate.put(key, value)

    @JavascriptInterface
    fun put(key: String, value: String, saveTime: Int) = delegate.put(key, value, saveTime)

    @JavascriptInterface
    fun putMemory(key: String, value: String) = delegate.putMemory(key, value)

    @JavascriptInterface
    fun getFromMemory(key: String): String? = delegate.getFromMemory(key)?.toString()

    @JavascriptInterface
    fun deleteMemory(key: String) = delegate.deleteMemory(key)

    @JavascriptInterface
    fun get(key: String): String? = delegate.get(key)

    @JavascriptInterface
    fun get(key: String, onlyDisk: Boolean): String? = delegate.get(key, onlyDisk)

    @JavascriptInterface
    fun putFile(key: String, value: String) = delegate.putFile(key, value)

    @JavascriptInterface
    fun putFile(key: String, value: String, saveTime: Int) {
        delegate.putFile(key, value, saveTime)
    }

    @JavascriptInterface
    fun getFile(key: String): String? = delegate.getFile(key)

    @JavascriptInterface
    fun delete(key: String) = delegate.delete(key)
}

internal fun BaseSource?.bookSourceCacheStoreOrNull(): BookSourceCacheStore? {
    return (this as? BookSource)?.let { BookSourceCacheStore(it.bookSourceUrl) }
}

internal fun BaseSource?.scriptCacheObject(): Any {
    return bookSourceCacheStoreOrNull() ?: CacheManager
}

internal fun BaseSource?.webCacheObject(): Any {
    return (this as? BookSource)?.let { BookSourceWebCacheStore(it.bookSourceUrl) }
        ?: WebCacheManager
}
