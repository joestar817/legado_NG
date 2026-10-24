package io.legado.app.ui.book.manage

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookCacheStatus
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.bookCacheDirectoryBytes
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.loadBookCacheStatus
import io.legado.app.help.book.menuCapabilities
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.model.BookCacheManager
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.model.SourceCallBack
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.normalizeFileName
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File


internal data class BookshelfCacheInfo(val status: BookCacheStatus, val bytes: Long)

internal data class BookshelfCacheState(
    val books: Map<String, BookshelfCacheInfo> = emptyMap(),
    val loading: Boolean = false,
    val failedBookUrls: Set<String> = emptySet(),
)

class BookshelfManageViewModel(
    application: Application,
    private val savedState: SavedStateHandle,
) : BaseViewModel(application) {
    var groupId: Long = -1L
    val batchChangeSourceState = MutableLiveData<Boolean>()
    val batchChangeSourceProcessLiveData = MutableLiveData<String>()
    var batchChangeSourceCoroutine: Coroutine<Unit>? = null
    internal var sortOrder: BookshelfManageSort
        get() = BookshelfManageSort.entries.firstOrNull {
            it.name == savedState.get<String>("sortOrder")
        } ?: BookshelfManageSort.FOLLOW_BOOKSHELF
        set(value) { savedState["sortOrder"] = value.name }

    private val mutableCacheState = MutableStateFlow(BookshelfCacheState())
    internal val cacheState = mutableCacheState.asStateFlow()
    private val cacheRequests = Channel<Unit>(Channel.CONFLATED)
    private var cacheBooks = emptyMap<String, Book>()
    private var cacheGroupId: Long? = null
    private val pendingCacheUrls = linkedSetOf<String>()
    private val loadingCacheUrls = hashSetOf<String>()
    private val cacheVersions = hashMapOf<String, Long>()
    private var nextCacheVersion = 0L

    init {
        viewModelScope.launch {
            for (request in cacheRequests) {
                delay(250)
                val books = pendingCacheUrls.mapNotNull(cacheBooks::get)
                pendingCacheUrls.clear()
                val versions = books.associate { it.bookUrl to cacheVersions[it.bookUrl] }
                loadingCacheUrls.addAll(versions.keys)
                val results = withContext(IO) {
                    // 音频索引每批只读一次，避免逐书反复遍历全部媒体片段。
                    var audioError: Exception? = null
                    val audioSizes = try {
                        AudioDownloadCache.cachedBytesByBook(books.filter { it.isAudio })
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        audioError = e
                        emptyMap()
                    }
                    val context = currentCoroutineContext()
                    books.associate { book ->
                        context.ensureActive()
                        book.bookUrl to runCatching {
                            if (book.isAudio) audioError?.let { throw it }
                            val status = loadBookCacheStatus(book, appDb.bookChapterDao.getChapterList(book.bookUrl)) {
                                context.ensureActive()
                            }
                            val bytes = if (book.menuCapabilities.showCache) {
                                bookCacheDirectoryBytes(File(BookHelp.cachePath, book.getFolderName())) {
                                    context.ensureActive()
                                } +
                                    audioSizes.getOrDefault(book.bookUrl, 0L)
                            } else 0L
                            BookshelfCacheInfo(status.copy(hasCache = status.hasCache || bytes > 0L), bytes)
                        }.onFailure { if (it is CancellationException) throw it }
                    }
                }
                loadingCacheUrls.removeAll(versions.keys)
                mutableCacheState.update { state ->
                    val current = results.filterKeys { url ->
                        url in cacheBooks && cacheVersions[url] == versions[url]
                    }
                    val success = current.mapNotNull { (url, result) ->
                        result.getOrNull()?.let { url to it }
                    }.toMap()
                    state.copy(
                        books = state.books + success,
                        loading = pendingCacheUrls.isNotEmpty(),
                        failedBookUrls = (state.failedBookUrls - current.keys) +
                            current.filterValues { it.isFailure }.keys,
                    )
                }
            }
        }
    }

    fun loadCacheFiles(books: List<Book>) {
        val previousBooks = cacheBooks
        val groupChanged = cacheGroupId != groupId
        cacheGroupId = groupId
        cacheBooks = books.associateBy(Book::bookUrl)
        pendingCacheUrls.retainAll(cacheBooks.keys)
        cacheVersions.keys.retainAll(cacheBooks.keys)
        mutableCacheState.update { state ->
            state.copy(
                books = state.books.filterKeys(cacheBooks::containsKey),
                failedBookUrls = state.failedBookUrls.intersect(cacheBooks.keys),
            )
        }
        requestCacheRefresh(books.filter { book ->
            val previous = previousBooks[book.bookUrl]
            groupChanged || previous?.origin != book.origin || previous?.type != book.type ||
                (book.bookUrl !in cacheState.value.books &&
                    book.bookUrl !in cacheState.value.failedBookUrls &&
                    book.bookUrl !in pendingCacheUrls && book.bookUrl !in loadingCacheUrls)
        })
    }

    fun refreshCache(bookUrl: String) {
        requestCacheRefresh(if (bookUrl.isEmpty()) cacheBooks.values else listOfNotNull(cacheBooks[bookUrl]))
    }

    private fun requestCacheRefresh(books: Collection<Book>) {
        if (books.isEmpty()) return
        books.forEach { book ->
            cacheVersions[book.bookUrl] = ++nextCacheVersion
            pendingCacheUrls.add(book.bookUrl)
        }
        mutableCacheState.update { it.copy(loading = true) }
        cacheRequests.trySend(Unit)
    }

    fun upCanUpdate(books: List<Book>, canUpdate: Boolean) {
        execute {
            val array = books.filter { it.menuCapabilities.update }.map { book ->
                book.copy(canUpdate = canUpdate).apply {
                    if (!canUpdate) {
                        removeType(BookType.updateError)
                    }
                }
            }
            appDb.bookDao.update(*array.toTypedArray())
        }
    }

    fun updateBook(vararg book: Book) {
        execute {
            appDb.bookDao.update(*book)
        }
    }

    fun deleteBook(books: List<Book>, deleteOriginal: Boolean = false) {
        execute {
            books.forEach {
                it.delete()
                if (it.isLocal) {
                    LocalBook.deleteBook(it, deleteOriginal)
                } else {
                    val source = appDb.bookSourceDao.getBookSource(it.origin)
                    SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, source, it)
                }
            }
        }
    }

    fun saveBookSourcesToFile(
        books: List<Book>,
        success: (file: File, name: String) -> Unit
    ) {
        execute {
            val sources = books.asSequence()
                .filter { it.menuCapabilities.changeSource }
                .mapNotNull { appDb.bookSourceDao.getBookSource(it.origin) }
                .distinctBy { it.bookSourceUrl }
                .toList()
            check(sources.isNotEmpty()) { context.getString(R.string.error_no_source) }
            val path = "${context.filesDir}/shareBookSource.json"
            FileUtils.delete(path)
            val file = FileUtils.createFileWithReplace(path)
            file.outputStream().buffered().use {
                GSON.writeToOutputStream(it, sources)
            }
            val name = if (sources.size == 1) {
                "bookSource_${sources.first().bookSourceName.normalizeFileName()}.json"
            } else {
                "bookSource.json"
            }
            file to name
        }.onSuccess { (file, name) ->
            success(file, name)
        }.onError {
            context.toastOnUi(it.localizedMessage ?: context.getString(R.string.error_no_source))
        }
    }

    fun changeSource(books: List<Book>, source: BookSource) {
        batchChangeSourceCoroutine?.cancel()
        batchChangeSourceCoroutine = execute {
            val changeSourceDelay = AppConfig.batchChangeSourceDelay * 1000L
            books.forEachIndexed { index, book ->
                batchChangeSourceProcessLiveData.postValue("${index + 1} / ${books.size}")
                if (!book.menuCapabilities.changeSource) return@forEachIndexed
                if (book.origin == source.bookSourceUrl) return@forEachIndexed
                val newBook = WebBook.preciseSearchAwait(source, book.name, book.author)
                    .onFailure {
                        AppLog.put("搜索书籍出错\n${it.localizedMessage}", it, true)
                    }.getOrNull() ?: return@forEachIndexed
                kotlin.runCatching {
                    if (newBook.tocUrl.isEmpty()) {
                        WebBook.getBookInfoAwait(source, newBook)
                    }
                }.onFailure {
                    AppLog.put("获取书籍详情出错\n${it.localizedMessage}", it, true)
                    return@forEachIndexed
                }
                WebBook.getChapterListAwait(source, newBook)
                    .onFailure {
                        AppLog.put("获取目录出错\n${it.localizedMessage}", it, true)
                    }.getOrNull()?.let { toc ->
                        book.migrateTo(newBook, toc)
                        book.removeType(BookType.updateError)
                        appDb.bookDao.insert(newBook)
                        appDb.bookChapterDao.insert(*toc.toTypedArray())
                    }
                delay(changeSourceDelay)
            }
        }.onStart {
            batchChangeSourceState.postValue(true)
        }.onFinally {
            batchChangeSourceState.postValue(false)
        }
    }

    fun clearCache(books: List<Book>) {
        execute {
            books.forEach {
                BookCacheManager.clear(it)
            }
        }.onSuccess {
            books.forEach { refreshCache(it.bookUrl) }
            context.toastOnUi(R.string.clear_cache_success)
        }
    }

}
