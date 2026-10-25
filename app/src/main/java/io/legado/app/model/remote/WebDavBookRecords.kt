package io.legado.app.model.remote

import android.util.AtomicFile
import com.google.gson.reflect.TypeToken
import io.legado.app.utils.GSON
import splitties.init.appCtx
import java.io.File
import java.io.IOException

/** Small IO-only file index. Atomic writes do not publish an uncommitted checkpoint. */
internal object WebDavBookRecords {
    private val lock = Any()
    private val file get() = File(appCtx.filesDir, "webdav-book-files.v1.json")
    private val type = object : TypeToken<List<WebDavBookRecord>>() {}.type

    fun all(): Map<String, WebDavBookRecord> = synchronized(lock) {
        read().associateBy { it.location.key }
    }

    fun get(location: WebDavBookLocation): WebDavBookRecord? = all()[location.key]

    fun put(record: WebDavBookRecord) = synchronized(lock) {
        val records = read().associateBy { it.location.key }.toMutableMap()
        records[record.location.key] = record
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(GSON.toJson(records.values.toList(), type).toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (error: Throwable) {
            atomic.failWrite(output)
            throw error
        }
    }

    private fun read(): List<WebDavBookRecord> {
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptyList()
        return try {
            AtomicFile(file).openRead().bufferedReader().use {
                GSON.fromJson<List<WebDavBookRecord>>(it, type)
                    ?: throw IOException("WebDAV file index is empty")
            }
        } catch (error: Exception) {
            throw IOException("WebDAV文件记录读取失败", error)
        }
    }
}
