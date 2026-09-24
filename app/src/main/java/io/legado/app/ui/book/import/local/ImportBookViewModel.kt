package io.legado.app.ui.book.import.local

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import io.legado.app.model.localBook.BookImportObserver
import io.legado.app.model.localBook.BookImportPhase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicBoolean
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern.archiveFileRegex
import io.legado.app.constant.AppPattern.bookFileRegex
import io.legado.app.constant.PreferKey
import io.legado.app.model.localBook.LocalBook
import io.legado.app.utils.AlphanumComparator
import io.legado.app.utils.FileDoc
import io.legado.app.utils.delete
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.list
import io.legado.app.utils.mapParallel
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import java.util.Collections

class ImportBookViewModel(application: Application) : BaseViewModel(application) {
    var rootDoc: FileDoc? = null
    val subDocs = arrayListOf<FileDoc>()
    var sort = context.getPrefInt(PreferKey.localBookImportSort)
    var dataCallback: DataCallback? = null
    var dataFlowStart: (() -> Unit)? = null
    var filterKey: String? = null
    val dataFlow = callbackFlow<List<ImportBook>> {

        val list = Collections.synchronizedList(ArrayList<ImportBook>())

        dataCallback = object : DataCallback {

            override fun setItems(fileDocs: List<FileDoc>) {
                list.clear()
                fileDocs.mapTo(list) {
                    ImportBook(it)
                }
                trySend(list)
            }

            override fun addItems(fileDocs: List<FileDoc>) {
                fileDocs.mapTo(list) {
                    ImportBook(it)
                }
                trySend(list)
            }

            override fun clear() {
                list.clear()
                trySend(emptyList())
            }

            override fun upAdapter() {
                trySend(list)
            }

            override fun markImported(fileKey: String) {
                synchronized(list) {
                    val index = list.indexOfFirst { it.file.toString() == fileKey }
                    if (index >= 0) list[index] = list[index].copy(isOnBookShelf = true)
                    trySend(list.toList())
                }
            }
        }

        withContext(Main) {
            dataFlowStart?.invoke()
        }

        awaitClose {
            dataCallback = null
        }

    }.map { docList ->
        val docList = docList.toList()
        val filterKey = filterKey
        val skipFilter = filterKey.isNullOrBlank()
        val comparator = when (sort) {
            2 -> compareBy<ImportBook>({ !it.isDir }, { -it.lastModified })
            1 -> compareBy({ !it.isDir }, { -it.size })
            else -> compareBy { !it.isDir }
        } then compareBy(AlphanumComparator) { it.name }
        docList.asSequence().filter {
            skipFilter || it.name.contains(filterKey)
        }.sortedWith(comparator).toList()
    }.flowOn(IO)

    private val importState = MutableStateFlow<BookImportBatch?>(null)
    internal val importBatch = importState.asStateFlow()
    private val stopImport = AtomicBoolean(false)
    private var importFiles = emptyList<FileDoc>()
    private var importArchive: FileDoc? = null

    internal fun addToBookshelf(books: List<ImportBook>) {
        if (importState.value != null || books.isEmpty()) return
        importFiles = books.map { it.file }
        importArchive = null
        importState.value = BookImportBatch("本地书籍", importFiles.map {
            BookImportItem(it.toString(), it.name)
        }, SystemClock.elapsedRealtime())
        runImport()
    }

    internal fun addArchiveEntries(archive: FileDoc, entryNames: List<String>) {
        if (importState.value != null || entryNames.isEmpty()) return
        importFiles = emptyList()
        importArchive = archive
        importState.value = BookImportBatch(archive.name, entryNames.distinct().map {
            BookImportItem(it, it.substringAfterLast('/').substringAfterLast('\\'))
        }, SystemClock.elapsedRealtime())
        runImport()
    }

    internal fun stopImport() {
        if (importState.value?.running != true) return
        stopImport.set(true)
        importState.update { it?.copy(stopRequested = true) }
    }

    internal fun dismissImport() {
        if (importState.value?.running == true) return
        importState.value = null
        importFiles = emptyList()
        importArchive = null
    }

    internal fun retryImport() {
        val batch = importState.value ?: return
        if (batch.running || batch.items.none { it.canRetry }) return
        importState.value = batch.retry(SystemClock.elapsedRealtime())
        runImport()
    }

    private fun runImport() {
        stopImport.set(false)
        val keys = importState.value?.items?.filter { it.phase == BookImportPhase.WAITING }
            ?.mapTo(hashSetOf()) { it.key } ?: return
        val archive = importArchive
        val files = importFiles.filter { it.toString() in keys }
        viewModelScope.launch(IO) {
            var stopped = false
            var batchError = ImportFailureInfo("未找到书籍", "压缩包中找不到所选条目")
            var lastProgressAt = 0L
            var lastProgressKey = ""
            var lastPhase = BookImportPhase.WAITING
            val observer = object : BookImportObserver {
                override fun beforeEntry(key: String) {
                    coroutineContext.ensureActive()
                    if (stopImport.get()) throw CancellationException("停止导入")
                }

                override fun onProgress(key: String, phase: BookImportPhase, bytes: Long, total: Long) {
                    val now = SystemClock.elapsedRealtime()
                    // Copy callbacks may fire for each 64KB; do not copy/recompose the list that often.
                    if (key == lastProgressKey && phase == lastPhase && phase == BookImportPhase.COPYING
                        && bytes < total && now - lastProgressAt < 150) return
                    lastProgressAt = now
                    lastProgressKey = key
                    lastPhase = phase
                    importState.update { it?.progress(key, phase, now, bytes, total) }
                    if (phase == BookImportPhase.SUCCESS && archive == null) dataCallback?.markImported(key)
                }

                override fun onFailure(key: String, error: Exception) {
                    importState.update { it?.progress(key, BookImportPhase.FAILED,
                        SystemClock.elapsedRealtime(), error = ImportFailureInfo.from(error)) }
                    AppLog.put("导入失败：$key\n${error.localizedMessage}", error)
                }
            }
            try {
                if (archive != null) {
                    LocalBook.importArchiveFile(archive.uri, observer = observer, filter = { it in keys })
                } else {
                    files.forEach { file ->
                        val key = file.toString()
                        observer.beforeEntry(key)
                        observer.onProgress(key, BookImportPhase.PARSING)
                        try {
                            LocalBook.importFile(file.uri)
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            observer.onFailure(key, error)
                            return@forEach
                        }
                        observer.onProgress(key, BookImportPhase.SUCCESS)
                    }
                }
            } catch (error: CancellationException) {
                stopped = true
                if (!stopImport.get()) throw error
            } catch (error: Exception) {
                batchError = ImportFailureInfo.from(error)
                AppLog.put("导入失败\n${error.localizedMessage}", error)
            } finally {
                importState.update { it?.finish(SystemClock.elapsedRealtime(), stopped, batchError) }
            }
        }
    }

    fun deleteDoc(bookList: HashSet<ImportBook>, finally: () -> Unit) {
        execute {
            bookList.forEach {
                it.file.delete()
            }
        }.onFinally {
            finally.invoke()
        }
    }

    fun loadDoc(fileDoc: FileDoc, finally: () -> Unit = {}) {
        execute {
            val docList = fileDoc.list { item ->
                when {
                    item.name.startsWith(".") -> false
                    item.isDir -> true
                    else -> item.name.matches(bookFileRegex) || item.name.matches(archiveFileRegex)
                }
            }
            dataCallback?.setItems(docList!!)
        }.onError {
            context.toastOnUi("获取文件列表出错\n${it.localizedMessage}")
        }.onFinally {
            finally()
        }
    }

    suspend fun scanDoc(fileDoc: FileDoc) {
        dataCallback?.clear()
        val channel = Channel<FileDoc>(UNLIMITED)
        var n = 1
        channel.trySend(fileDoc)
        val list = arrayListOf<FileDoc>()
        channel.consumeAsFlow()
            .mapParallel(16) { fileDoc ->
                fileDoc.list()!!
            }.onEach { fileDocs ->
                n--
                list.clear()
                fileDocs.forEach {
                    if (it.isDir) {
                        n++
                        channel.trySend(it)
                    } else if (it.name.matches(bookFileRegex)
                        || it.name.matches(archiveFileRegex)
                    ) {
                        list.add(it)
                    }
                }
                dataCallback?.addItems(list)
            }.takeWhile {
                n > 0
            }.catch {
                context.toastOnUi("扫描文件夹出错\n${it.localizedMessage}")
            }.collect()
    }

    fun updateCallBackFlow(filterKey: String?) {
        this.filterKey = filterKey
        dataCallback?.upAdapter()
    }

    interface DataCallback {

        fun setItems(fileDocs: List<FileDoc>)

        fun addItems(fileDocs: List<FileDoc>)

        fun clear()

        fun upAdapter()

        fun markImported(fileKey: String)

    }

}
