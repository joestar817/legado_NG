package io.legado.app.help.source

import android.webkit.JavascriptInterface
import androidx.annotation.Keep
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.help.CacheManager
import io.legado.app.help.WebCacheManager
import io.legado.app.utils.ACache
import splitties.init.appCtx
import java.io.File

/**
 * 书源与订阅源共用的脚本缓存。
 *
 * 对脚本保持原 cache 方法语义，但通过独立前缀和文件目录与 App 内部缓存隔离，
 * 避免重新暴露原始 CacheManager 键空间。下载文件仍由 JsExtensions 的源级文件根管理。
 */
@Keep
@Suppress("unused")
object SourceSharedCacheStore {

    private const val storagePrefix = "source_shared_cache:"
    private val fileCache by lazy {
        ACache.get(File(appCtx.cacheDir, "sourceSharedCache"))
    }

    private fun scopedKey(key: String): String = storagePrefix + key

    @JvmOverloads
    fun put(key: String, value: Any, saveTime: Int = 0) {
        val scopedKey = scopedKey(key)
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
        fileCache.put(scopedKey(key), value, saveTime)
    }

    fun getFile(key: String): String? = fileCache.getAsString(scopedKey(key))

    fun delete(key: String) {
        val scopedKey = scopedKey(key)
        CacheManager.delete(scopedKey)
        fileCache.remove(scopedKey)
    }
}

@Keep
@Suppress("unused")
object SourceSharedWebCacheStore {

    @JavascriptInterface
    fun put(key: String, value: String) = SourceSharedCacheStore.put(key, value)

    @JavascriptInterface
    fun put(key: String, value: String, saveTime: Int) {
        SourceSharedCacheStore.put(key, value, saveTime)
    }

    @JavascriptInterface
    fun putMemory(key: String, value: String) = SourceSharedCacheStore.putMemory(key, value)

    @JavascriptInterface
    fun getFromMemory(key: String): String? {
        return SourceSharedCacheStore.getFromMemory(key)?.toString()
    }

    @JavascriptInterface
    fun deleteMemory(key: String) = SourceSharedCacheStore.deleteMemory(key)

    @JavascriptInterface
    fun get(key: String): String? = SourceSharedCacheStore.get(key)

    @JavascriptInterface
    fun get(key: String, onlyDisk: Boolean): String? {
        return SourceSharedCacheStore.get(key, onlyDisk)
    }

    @JavascriptInterface
    fun putFile(key: String, value: String) = SourceSharedCacheStore.putFile(key, value)

    @JavascriptInterface
    fun putFile(key: String, value: String, saveTime: Int) {
        SourceSharedCacheStore.putFile(key, value, saveTime)
    }

    @JavascriptInterface
    fun getFile(key: String): String? = SourceSharedCacheStore.getFile(key)

    @JavascriptInterface
    fun delete(key: String) = SourceSharedCacheStore.delete(key)
}

internal fun BaseSource?.sourceSharedCacheStoreOrNull(): SourceSharedCacheStore? {
    return when (this) {
        is BookSource, is RssSource -> SourceSharedCacheStore
        else -> null
    }
}

internal fun BaseSource?.scriptCacheObject(): Any {
    return sourceSharedCacheStoreOrNull() ?: CacheManager
}

internal fun BaseSource?.webCacheObject(): Any {
    return if (sourceSharedCacheStoreOrNull() != null) {
        SourceSharedWebCacheStore
    } else {
        WebCacheManager
    }
}
