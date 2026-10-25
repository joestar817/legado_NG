package io.legado.app.model.remote

import android.net.Uri
import androidx.core.net.toUri
import io.legado.app.help.book.bookExportWebDavUrl
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.utils.FileDoc
import io.legado.app.utils.inputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import splitties.init.appCtx
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Identical uploads reuse the object; changed uploads preserve the old cloud object. */
internal suspend fun uploadWebDavBook(
    uri: Uri,
    fileName: String,
    contentType: String,
    directory: String,
    authorization: Authorization,
    serverId: Long? = null,
): WebDavBookLocation = withContext(Dispatchers.IO) {
    require(fileName.isNotBlank() && fileName.none { it == '/' || it == '\\' || it == '\u0000' })
    val work = File(appCtx.cacheDir, "webdav-upload-${UUID.randomUUID()}")
    val original = FileDoc.fromUri(uri, false)
    var generatedCollection: String? = null
    var completed = false
    try {
        uri.inputStream(appCtx).getOrThrow().use { input ->
            work.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
        }
        check(work.length() > 0) { "不能上传空文件" }
        val hash = WebDavBookTransfer.hash(work.toUri())
        val root = normalizedWebDavBookUrl(directory)
        val previous = WebDavBookRecords.all().values.filter {
            it.serverId == serverId && webDavLocalUri(it.localUri) == uri && it.sha256 == hash &&
                normalizedWebDavBookUrl(it.uploadDirectory ?: it.location.directory) == root
        }
        var target: String? = null
        var remote: io.legado.app.lib.webdav.WebDavFile? = null
        // Reuse a verified earlier version, or a same-content file already at the requested name.
        for (candidate in (previous.map { it.path } + bookExportWebDavUrl(directory, fileName)).distinct()) {
            val dav = WebDav(candidate, authorization)
            val file = dav.getWebDavFile() ?: continue
            if (!file.isDir && (file.size <= 0 || file.size == work.length()) &&
                dav.downloadInputStream().use { hashStream(it) } == hash) {
                target = candidate
                remote = file
                break
            }
        }
        if (target == null) {
            // Some providers ignore conditional PUT. Use a fresh 128-bit version namespace,
            // keeping the logical filename intact for TXT metadata and cross-device imports.
            val folder = fileName.substringBeforeLast('.', fileName).take(50) + "-${UUID.randomUUID()}"
            val collection = directory.toHttpUrl().newBuilder().addPathSegment(folder).addPathSegment("").build().toString()
            val collectionDav = WebDav(collection, authorization)
            check(collectionDav.getWebDavFile() == null) { "云端版本目录已存在，请重试" }
            collectionDav.createBookCollection()
            generatedCollection = collection
            target = bookExportWebDavUrl(collection, fileName)
            check(WebDav(target, authorization).getWebDavFile() == null) { "云端版本文件已存在，请重试" }
            WebDav(target, authorization).uploadBookSnapshot(work.toUri(), contentType)
            remote = WebDav(target, authorization).getWebDavFile() ?: error("上传后未找到云端文件")
        }
        val dav = WebDav(target, authorization)
        val uploaded = remote ?: error("上传后未找到云端文件")
        check((uploaded.size <= 0 || uploaded.size == work.length()) && dav.downloadInputStream().use { hashStream(it) } == hash) {
            "云端文件校验失败，请重试"
        }
        val location = WebDavBookLocation(serverId, target)
        val checkpoint = WebDavBookRecords.get(location)
        if (checkpoint == null || webDavLocalUri(checkpoint.localUri) == uri) {
            WebDavBookRecords.put(WebDavBookRecord(serverId, target, uri.toString(), hash,
                uploaded.lastModify, uploaded.size, original.lastModified, original.size, uploaded.etag, directory))
        }
        completed = true
        location
    } finally {
        work.delete()
        if (!completed) generatedCollection?.let { collection ->
            withContext(NonCancellable + Dispatchers.IO) {
                withTimeoutOrNull(10_000) { WebDav(collection, authorization).delete() }
            }
        }
    }
}

private suspend fun hashStream(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
