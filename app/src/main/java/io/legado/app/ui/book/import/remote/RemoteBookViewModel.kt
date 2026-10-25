package io.legado.app.ui.book.import.remote

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.help.book.isNotShelf
import io.legado.app.model.remote.*
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.exists
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl

class RemoteBookViewModel(application: Application) : BaseViewModel(application) {
    var sortKey = RemoteBookSort.Default
    var sortAscending = false
    val dirList = arrayListOf<RemoteBook>()
    val permissionDenialLiveData = MutableLiveData<Int>()
    internal val dataFlow = MutableStateFlow<List<RemoteBook>>(emptyList())
    internal val statuses = MutableStateFlow<Map<String, String>>(emptyMap())
    internal val transfer = MutableStateFlow<TransferProgress?>(null)
    internal val conflict = MutableStateFlow<WebDavBookConflict?>(null)
    internal val loading = MutableStateFlow(false)
    internal var loadSucceeded = false
        private set
    internal val batchResult = MutableLiveData<BatchResult>()
    internal var focusedPath: String? = null
    private var rawItems = emptyList<RemoteBook>()
    private var filter = ""
    private var manager: RemoteBookWebDav? = null
    private var loadJob: Job? = null
    private var batchJob: Job? = null
    private var loadVersion = 0
    private var answer: CompletableDeferred<WebDavBookChoice>? = null
    var isDefaultWebdav = false
        private set

    internal data class TransferProgress(val name: String, val index: Int, val total: Int,
        val bytes: Long = 0, val size: Long = 0)
    internal data class BatchResult(val completed: Int, val skipped: Int, val failures: List<String>, val cancelled: Boolean)

    internal fun initData(location: WebDavBookLocation? = null, force: Boolean = false, onSuccess: () -> Unit) {
        if (manager != null && !force) { onSuccess(); return }
        viewModelScope.launch {
            try {
                manager = withContext(Dispatchers.IO) {
                    val id = if (location != null) location.serverId else AppConfig.remoteServerId.takeIf { it > 0 }
                    webDavBookManager(id)
                }
                isDefaultWebdav = manager!!.serverID == null
                if (force && location == null) focusedPath = null
                if (location != null) {
                    WebDavBookTransfer(manager!!).location(location.path)
                    focusedPath = location.path
                    dirList.clear()
                    val root = normalizedWebDavBookUrl(manager!!.rootBookUrl).trimEnd('/') + "/"
                    val directory = normalizedWebDavBookUrl(location.directory)
                    var parent = root
                    directory.removePrefix(root).trimEnd('/').split('/').filter { it.isNotEmpty() }.forEach { part ->
                        parent += "$part/"
                        val name = parent.toHttpUrl().pathSegments.dropLast(1).last()
                        dirList.add(RemoteBook(name, parent, 0, 0))
                    }
                }
                onSuccess()
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { context.toastOnUi(error.localizedMessage) }
        }
    }

    fun loadRemoteBookList(path: String?, loadCallback: (Boolean) -> Unit) {
        loadJob?.cancel()
        val version = ++loadVersion
        loadJob = viewModelScope.launch {
            loading.value = true
            loadSucceeded = false
            rawItems = emptyList()
            dataFlow.value = emptyList()
            statuses.value = emptyMap()
            loadCallback(true)
            try {
                val dav = manager ?: error("WebDAV 没有配置")
                val loaded = withContext(Dispatchers.IO) {
                    val items = dav.getRemoteBookList(path ?: dav.rootBookUrl)
                    val books = appDb.bookDao.all.filterNot { it.isNotShelf }.mapNotNull { book ->
                        runCatching { WebDavBookLocation.fromBook(book)?.key?.let { it to book } }.getOrNull()
                    }.toMap()
                    val records = WebDavBookRecords.all()
                    val labels = mutableMapOf<String, String>()
                    val snapshot = items.map { item ->
                        if (item.isDir) return@map item
                        val key = webDavBookKey(dav.serverID, item.path)
                        val book = books[key]
                        val record = records[key]
                        val uri = book?.bookUrl ?: record?.localUri
                        val doc = uri?.let { FileDoc.fromUri(webDavLocalUri(it), false) }
                        val label = when {
                            doc != null && !doc.exists() -> R.string.webdav_local_missing
                            record != null && doc != null && (doc.size != record.localSize ||
                                doc.lastModified != record.localModified) -> R.string.webdav_local_changed
                            record != null && (item.size != record.remoteSize ||
                                item.lastModify != record.remoteModified || item.etag != record.etag) -> R.string.webdav_remote_changed
                            book != null -> R.string.local_import_on_bookshelf
                            record != null -> R.string.webdav_downloaded
                            else -> null
                        }
                        if (label != null) labels[item.path] = context.getString(label)
                        item.copy(isOnBookShelf = book != null)
                    }
                    snapshot to labels.toMap()
                }
                if (version == loadVersion) {
                    rawItems = loaded.first
                    statuses.value = loaded.second
                    focusedPath?.let { focus ->
                        val key = webDavBookKey(dav.serverID, focus)
                        focusedPath = loaded.first.firstOrNull { webDavBookKey(dav.serverID, it.path) == key }?.path
                    }
                    publish()
                    loadSucceeded = true
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                AppLog.put("获取 WebDAV 书籍失败", error)
                context.toastOnUi(error.localizedMessage)
            } finally {
                if (version == loadVersion) {
                    loading.value = false
                    loadCallback(false)
                }
            }
        }
    }

    fun updateCallBackFlow(filterKey: String?) { filter = filterKey.orEmpty(); publish() }

    private fun publish() {
        val comparator = when (sortKey) {
            RemoteBookSort.Name -> Comparator<RemoteBook> { a, b -> AlphanumComparator.compare(a.filename, b.filename) }
            else -> compareBy<RemoteBook> { it.lastModify }
        }.let { if (sortAscending) it else it.reversed() }
        dataFlow.value = rawItems.filter { filter.isBlank() || it.filename.contains(filter) }
            .sortedWith(compareBy<RemoteBook> { !it.isDir }.then(comparator))
    }

    internal fun resolveConflict(choice: WebDavBookChoice) { answer?.complete(choice) }
    fun cancelTransfer() { batchJob?.cancel() }

    private suspend fun ask(value: WebDavBookConflict): WebDavBookChoice {
        val response = CompletableDeferred<WebDavBookChoice>()
        answer = response
        conflict.value = value
        return try { response.await() } finally {
            conflict.value = null
            answer = null
        }
    }

    fun addToBookshelf(remoteBooks: Collection<RemoteBook>, finally: () -> Unit) =
        download(remoteBooks, true, finally)

    fun download(remoteBooks: Collection<RemoteBook>, addToShelf: Boolean, finally: () -> Unit) {
        if (batchJob?.isActive == true) return
        val selected = remoteBooks.toList()
        batchJob = viewModelScope.launch {
            var completed = 0
            var skipped = 0
            var cancelled = false
            var permissionRequested = false
            val failures = arrayListOf<String>()
            try {
                val engine = WebDavBookTransfer(manager ?: error("WebDAV 没有配置"))
                for ((index, item) in selected.withIndex()) {
                    ensureActive()
                    transfer.value = TransferProgress(item.filename, index + 1, selected.size)
                    try {
                        var lastProgressAt = 0L
                        val result = engine.download(item, addToShelf, ::ask, onSaved = { completed++ }) { bytes, total ->
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastProgressAt >= 100 || total > 0 && bytes >= total) {
                                transfer.value = TransferProgress(item.filename, index + 1, selected.size, bytes, total)
                                lastProgressAt = now
                            }
                        }
                        if (result.skipped) { skipped++; continue }
                        if (addToShelf && !result.savedCopy && ArchiveUtils.isArchive(item.filename)) withContext(Dispatchers.IO) {
                            val archiveWork = java.io.File(context.cacheDir, "webdav-extract-${java.util.UUID.randomUUID()}")
                            try {
                                val extracted = ArchiveUtils.deCompress(FileDoc.fromUri(result.uri, false),
                                    path = archiveWork.absolutePath, filter = { it.matches(AppPattern.bookFileRegex) })
                                check(extracted.isNotEmpty()) { context.getString(R.string.unsupport_archivefile_entry) }
                                for (file in extracted) {
                                    try { engine.importExtracted(file, ::ask) }
                                    catch (error: CancellationException) { throw error }
                                    catch (error: Exception) { failures.add("${file.name}: ${error.localizedMessage}") }
                                }
                            } finally {
                                archiveWork.deleteRecursively()
                            }
                        }
                    } catch (error: CancellationException) { throw error }
                    catch (error: Exception) {
                        failures.add("${item.filename}: ${error.localizedMessage}")
                        AppLog.put("下载 WebDAV 书籍失败：${item.filename}", error)
                        if (error is SecurityException && !permissionRequested) {
                            permissionRequested = true
                            permissionDenialLiveData.value = 1
                        }
                    }
                }
            } catch (error: CancellationException) { cancelled = true; throw error }
            catch (error: Exception) { failures.add(error.localizedMessage.orEmpty()) }
            finally {
                transfer.value = null
                conflict.value = null
                batchResult.value = BatchResult(completed, skipped, failures.toList(), cancelled)
                finally()
            }
        }
    }

    internal suspend fun findLocal(item: RemoteBook) = withContext(Dispatchers.IO) {
        manager?.let { WebDavBookTransfer.findBook(WebDavBookLocation(it.serverID, item.path)) }
    }
}
