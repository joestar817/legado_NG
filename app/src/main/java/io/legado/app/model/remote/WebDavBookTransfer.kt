package io.legado.app.model.remote

import android.net.Uri
import androidx.core.net.toUri
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.updateTo
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.webdav.WebDav
import io.legado.app.model.BookCacheManager
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import splitties.init.appCtx
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID

/** File transfer only. Parsing, shelf state and reading state stay in the existing services. */
internal class WebDavBookTransfer(private val manager: RemoteBookWebDav) {
    data class Download(val uri: Uri, val book: Book? = null, val skipped: Boolean = false, val savedCopy: Boolean = false)

    fun location(path: String) = WebDavBookLocation(manager.serverID, path).also {
        manager.requireBookPath(path)
    }

    suspend fun download(
        item: RemoteBook,
        addToShelf: Boolean,
        ask: suspend (WebDavBookConflict) -> WebDavBookChoice,
        onSaved: () -> Unit = {},
        progress: (Long, Long) -> Unit,
    ): Download = withContext(Dispatchers.IO) {
        val location = location(item.path)
        validateFileName(item.filename)
        val workDir = File(appCtx.cacheDir, "webdav-books/${UUID.randomUUID()}")
        check(workDir.mkdirs()) { "无法创建下载暂存目录" }
        val staged = File(workDir, item.filename)
        var created: FileDoc? = null
        var committed = false
        var notified = false
        fun markSaved() {
            committed = true
            if (!notified) { notified = true; onSaved() }
        }
        var preview: Book? = null
        try {
            val fresh = manager.getRemoteBook(location.path) ?: throw IOException("云端文件已不存在")
            check(!fresh.isDir) { "不能下载文件夹" }
            WebDav(location.path, manager.authorization).downloadInputStream().use { input ->
                staged.outputStream().use { output ->
                    val copied = copy(input, output) { progress(it, fresh.size) }
                    check(copied > 0 && (fresh.size <= 0 || copied == fresh.size)) {
                        "下载不完整，请重试"
                    }
                }
            }
            val after = manager.getRemoteBook(location.path) ?: throw IOException("云端文件已不存在")
            check(fresh.size == after.size && fresh.lastModify == after.lastModify && fresh.etag == after.etag) {
                "下载期间云端文件发生变化，请重试"
            }
            val remoteHash = hash(staged.toUri())
            val record = WebDavBookRecords.get(location)
            val linked = findBook(location)
            val oldUri = linked?.bookUrl?.let(::webDavLocalUri) ?: record?.localUri?.let(::webDavLocalUri)
            val localHash = oldUri?.let { uri ->
                if (FileDoc.fromUri(uri, false).exists()) hash(uri) else null
            }
            val change = webDavBookChange(record?.sha256, localHash, remoteHash)
            if (change == WebDavBookChange.SAME && oldUri != null && (!addToShelf || linked != null)) {
                checkpoint(location, oldUri, remoteHash, after)
                markSaved()
                return@withContext Download(oldUri, linked)
            }

            val archive = ArchiveUtils.isArchive(item.filename)
            if (addToShelf && !archive) preview = LocalBook.inspectFile(staged.toUri())
            val sameWork = preview?.let { appDb.bookDao.getBook(it.name, it.author) }
            val replacing = linked ?: sameWork
            var saveCopy = !addToShelf && linked != null
            if (addToShelf && (localHash != null && change != WebDavBookChange.SAME ||
                replacing != null && linked == null)) {
                when (ask(WebDavBookConflict(item.filename, change, linked == null && sameWork != null))) {
                    WebDavBookChoice.KEEP_LOCAL -> return@withContext Download(oldUri ?: webDavLocalUri(replacing!!.bookUrl), skipped = true)
                    WebDavBookChoice.SAVE_COPY -> saveCopy = true
                    WebDavBookChoice.USE_CLOUD -> Unit
                }
            }
            currentCoroutineContext().ensureActive()
            val latest = manager.getRemoteBook(location.path) ?: throw IOException("云端文件已不存在")
            check(latest.size == after.size && latest.lastModify == after.lastModify && latest.etag == after.etag) {
                "确认期间云端文件发生变化，请重试"
            }
            // A new file is always used, including SAF providers without atomic replacement.
            created = saveNewFile(staged, item.filename, remoteHash)
            val saved = created!!.uri
            val imported = if (addToShelf && !archive && !saveCopy) {
                importVerified(saved, preview!!, location, after.lastModify, replacing, ::markSaved)
            } else null
            markSaved()
            // Keeping a separate copy must not hide unresolved changes of the linked shelf file.
            if (!saveCopy || linked == null) checkpoint(location, saved, remoteHash, after)
            Download(saved, imported, savedCopy = saveCopy)
        } finally {
            if (!committed) created?.delete()
            preview?.let { LocalBook.deleteBook(it, false) }
            staged.delete()
            workDir.delete()
        }
    }

    /** Explicit import of a selected extracted entry; the archive remains a standalone download. */
    suspend fun importExtracted(
        file: File,
        ask: suspend (WebDavBookConflict) -> WebDavBookChoice,
    ): Book? = withContext(Dispatchers.IO) {
        val preview = LocalBook.inspectFile(file.toUri())
        var created: FileDoc? = null
        var committed = false
        try {
            val existing = appDb.bookDao.getBook(preview.name, preview.author)
            if (existing != null) {
                when (ask(WebDavBookConflict(file.name, WebDavBookChange.UNTRACKED, true, false))) {
                    WebDavBookChoice.KEEP_LOCAL, WebDavBookChoice.SAVE_COPY -> return@withContext null
                    WebDavBookChoice.USE_CLOUD -> Unit
                }
            }
            created = saveNewFile(file, file.name, hash(file.toUri()))
            val book = importVerified(created!!.uri, preview, null, 0, existing) { committed = true }
            committed = true
            book
        } finally {
            if (!committed) created?.delete()
            LocalBook.deleteBook(preview, false)
        }
    }

    private suspend fun importVerified(
        uri: Uri,
        preview: Book,
        location: WebDavBookLocation?,
        remoteModified: Long,
        expected: Book?,
        onCommitted: () -> Unit,
    ): Book {
        val book = LocalBook.inspectFile(uri).apply {
            name = expected?.name ?: preview.name
            author = expected?.author ?: preview.author
            origin = location?.let {
                BookType.webDavTag + CustomUrl(it.path).putAttribute("serverID", it.serverId)
            } ?: BookType.localTag
            lastCheckTime = remoteModified
        }
        try {
            expected?.updateTo(book)
            val chapters = LocalBook.getChapterList(book)
            currentCoroutineContext().ensureActive()
            appDb.runInTransaction {
                val collision = appDb.bookDao.getBook(book.name, book.author)
                check(collision?.bookUrl == expected?.bookUrl) { "书架条目已变化，请重试" }
                val old = expected?.let { appDb.bookDao.getBook(it.bookUrl) }
                check(expected == null || old != null) { "书架条目已变化，请重试" }
                // Re-read progress/settings after the confirmation dialog, before committing.
                old?.updateTo(book)
                if (old != null) appDb.bookDao.delete(old)
                appDb.bookDao.insert(book)
                appDb.bookChapterDao.insert(*chapters.toTypedArray())
            }
            onCommitted()
        } catch (error: Throwable) {
            BookCacheManager.clear(book)
            throw error
        }
        expected?.let { BookCacheManager.clear(it) }
        withContext(Dispatchers.Main.immediate) { ReadBook.onChapterListUpdated(book) }
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
        return book
    }

    private suspend fun saveNewFile(staged: File, name: String, sha256: String): FileDoc {
        val directory = AppConfig.defaultBookTreeUri ?: throw IOException("请先选择书籍保存位置")
        val dir = FileDoc.fromUri(directory.toUri(), true)
        val extension = name.substringAfterLast('.', "")
        val base = name.substringBeforeLast('.', name).take(90)
        // Exclusive creation: never open an existing target, even if another transfer races us.
        val unique = "$base-${UUID.randomUUID().toString().take(8)}" + if (extension.isEmpty()) "" else ".$extension"
        val target = if (dir.isContentScheme) {
            FileDoc.fromDocumentFile(dir.asDocumentFile()!!.createFile("application/octet-stream", unique)
                ?: throw IOException("无法创建下载文件"))
        } else {
            val target = File(dir.asFile(), unique)
            check(target.createNewFile()) { "下载文件已存在，请重试" }
            FileDoc.fromFile(target)
        }
        try {
            staged.inputStream().use { input ->
                target.openOutputStream().getOrThrow().use { output -> copy(input, output) {} }
            }
            check(hash(target.uri) == sha256) { "本地文件校验失败" }
            return target
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    companion object {
        fun findBook(location: WebDavBookLocation): Book? = appDb.bookDao.all.firstOrNull {
            !it.isNotShelf && runCatching { WebDavBookLocation.fromBook(it)?.key }.getOrNull() == location.key
        }

        suspend fun checkpoint(location: WebDavBookLocation, uri: Uri, hash: String, remote: RemoteBook) {
            currentCoroutineContext().ensureActive()
            val doc = FileDoc.fromUri(uri, false)
            val uploadDirectory = WebDavBookRecords.get(location)?.uploadDirectory
            WebDavBookRecords.put(WebDavBookRecord(location.serverId, location.path, uri.toString(), hash,
                remote.lastModify, remote.size, doc.lastModified, doc.size, remote.etag, uploadDirectory))
        }

        suspend fun hash(uri: Uri): String {
            val digest = MessageDigest.getInstance("SHA-256")
            uri.inputStream(appCtx).getOrThrow().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private suspend fun copy(input: InputStream, output: OutputStream, progress: (Long) -> Unit): Long {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                copied += read
                progress(copied)
            }
            return copied
        }

        private fun validateFileName(name: String) {
            require(name.isNotBlank() && name != "." && name != ".." &&
                name.none { it == '/' || it == '\\' || it == '\u0000' }) { "无效文件名" }
        }
    }
}
