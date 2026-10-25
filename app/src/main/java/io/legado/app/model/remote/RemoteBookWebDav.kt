package io.legado.app.model.remote

import android.net.Uri
import io.legado.app.constant.AppPattern.archiveFileRegex
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.webdav.Authorization
import io.legado.app.lib.webdav.WebDav
import io.legado.app.lib.webdav.WebDavFile
import io.legado.app.model.analyzeRule.CustomUrl
import io.legado.app.utils.NetworkUtils
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl

class RemoteBookWebDav(
    val rootBookUrl: String,
    val authorization: Authorization,
    val serverID: Long? = null
) : RemoteBookManager() {

    init {
        runBlocking {
            WebDav(rootBookUrl, authorization).makeAsDir()
        }
    }


    @Throws(Exception::class)
    override suspend fun getRemoteBookList(path: String): MutableList<RemoteBook> {
        requireBookPath(path)
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可用")
        val remoteBooks = mutableListOf<RemoteBook>()
        //读取文件列表
        val remoteWebDavFileList: List<WebDavFile> = WebDav(path, authorization).listFiles()
        //转化远程文件信息到本地对象
        remoteWebDavFileList.forEach { webDavFile ->
            if (webDavFile.isDir
                || bookFileRegex.matches(webDavFile.displayName)
                || archiveFileRegex.matches(webDavFile.displayName)
            ) {
                //扩展名符合阅读的格式则认为是书籍
                remoteBooks.add(RemoteBook(webDavFile))
            }
        }
        return remoteBooks
    }

    override suspend fun getRemoteBook(path: String): RemoteBook? {
        requireBookPath(path)
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可用")
        val webDavFile = WebDav(path, authorization).getWebDavFile()
            ?: return null
        return RemoteBook(webDavFile)
    }

    override suspend fun downloadRemoteBook(remoteBook: RemoteBook): Uri {
        AppConfig.defaultBookTreeUri
            ?: throw NoStackTraceException("没有设置书籍保存位置!")
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可用")
        return WebDavBookTransfer(this).download(remoteBook, false,
            ask = { WebDavBookChoice.SAVE_COPY }, progress = { _, _ -> }).uri
    }

    override suspend fun upload(book: Book) {
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可用")
        val localBookUri = webDavLocalUri(book.bookUrl)
        val previous = WebDavBookLocation.fromBook(book)?.takeIf { it.serverId == serverID }
        if (previous != null) WebDavBookTransfer(this).location(previous.path)
        val name = previous?.path?.toHttpUrl()?.pathSegments?.last()
            ?: book.originName
        val directory = previous?.let { WebDavBookRecords.get(it)?.uploadDirectory ?: it.directory } ?: rootBookUrl
        requireBookPath(directory)
        val uploaded = uploadWebDavBook(localBookUri, name, "application/octet-stream",
            directory, authorization, serverID)
        book.origin = BookType.webDavTag + CustomUrl(uploaded.path)
            .putAttribute("serverID", serverID)
            .toString()
        withContext(Dispatchers.IO) { book.update() }
    }

    override suspend fun delete(remoteBookUrl: String) {
        requireBookPath(remoteBookUrl)
        if (!NetworkUtils.isAvailable()) throw NoStackTraceException("网络不可用")
        WebDav(remoteBookUrl, authorization).delete()
    }

    internal fun requireBookPath(path: String) {
        val root = normalizedWebDavBookUrl(rootBookUrl).toHttpUrl()
        val target = normalizedWebDavBookUrl(path).toHttpUrl()
        require(target.scheme == root.scheme && target.host == root.host && target.port == root.port &&
            (target.encodedPath == root.encodedPath ||
                target.encodedPath.startsWith(root.encodedPath.trimEnd('/') + "/"))) {
            "文件不在当前 WebDAV 目录中"
        }
    }

}
