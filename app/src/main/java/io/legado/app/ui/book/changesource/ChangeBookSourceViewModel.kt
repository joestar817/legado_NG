package io.legado.app.ui.book.changesource

import android.app.Application
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.primaryStr
import io.legado.app.help.book.releaseHtmlData
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.SourceConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.SourceHelp
import io.legado.app.help.source.SourceInteractionPolicy
import io.legado.app.model.webBook.WebBook
import io.legado.app.utils.cnCompare
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.internString
import io.legado.app.utils.mapParallel
import io.legado.app.utils.onEachIndexed
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Suppress("MemberVisibilityCanBePrivate")
open class ChangeBookSourceViewModel(application: Application) : BaseViewModel(application) {
    private val threadCount = AppConfig.threadCount
    private var searchPool: ExecutorCoroutineDispatcher? = null
    val searchStateData = MutableLiveData<Boolean>()
    var searchFinishCallback: ((isEmpty: Boolean) -> Unit)? = null
    var name: String = ""
    var author: String = ""
    private var fromReadBookActivity = false
    private var oldBook: Book? = null
    private var screenKey: String = ""
    private var bookSourceParts = arrayListOf<BookSourcePart>()
    val totalSourceCount: Int
        get() = bookSourceParts.size
    private var searchBookList = arrayListOf<SearchBook>()
    private val searchBooks = Collections.synchronizedList(arrayListOf<SearchBook>())
    private val searchLock = Any()
    // 仅后台数据库操作使用；主线程停止/切换范围不等待此锁。
    private val cacheLock = Any()
    private val requestGuard = ChangeSourceRequestGuard()
    val currentSourceType: Int
        get() = ChangeSourceFilter.sourceTypeForBook(
            oldBook?.type ?: 0, oldBook?.readConfig?.customBookType
        )
    @Volatile
    var selectedGroupUnavailable = false
        private set
    private val tocMap = ConcurrentHashMap<String, List<BookChapter>>()
    private val _changeSourceProgress = MutableStateFlow(0 to "")
    val changeSourceProgress = _changeSourceProgress.asStateFlow()
    private var tocMapChapterCount = 0
    private val contentProcessor by lazy {
        ContentProcessor.get(oldBook!!)
    }
    private var searchCallback: SourceCallback? = null
    private val chapterNumRegex = "^\\[(\\d+)]".toRegex()
    private val comparatorBase by lazy {
        compareByDescending<SearchBook> { getBookScore(it) }
            .thenByDescending { SourceConfig.getSourceScore(it.origin) }
    }
    private val defaultComparator by lazy {
        comparatorBase.thenBy { it.originOrder }
    }
    private val wordCountComparator by lazy {
        comparatorBase.thenByDescending { it.chapterWordCount > 1000 }
            .thenByDescending { getChapterNum(it.chapterWordCountText) }
            .thenByDescending { it.chapterWordCount }
            .thenBy { it.originOrder }
    }
    private var task: Job? = null
    private val interactionPolicy = SourceInteractionPolicy(
        blockDialogs = context.getPrefBoolean(PreferKey.searchBlockSourceDialogs)
    )
    val bookMap = ConcurrentHashMap<String, Book>()
    val searchDataFlow = callbackFlow {

        searchCallback = object : SourceCallback {

            override fun upAdapter() {
                trySend(Unit)
            }

        }

        val initialRequest = requestGuard.snapshot(currentFilter())
        val empty = refresh(initialRequest)
        synchronized(searchLock) {
            if (isCurrent(initialRequest) && empty) startSearch()
        }

        awaitClose {
            searchCallback = null
        }
    }.map {
        kotlin.runCatching {
            val comparator = if (AppConfig.changeSourceLoadWordCount) {
                wordCountComparator
            } else {
                defaultComparator
            }
            synchronized(searchBooks) { searchBooks.toList() }.sortedWith(comparator)
        }.onFailure {
            AppLog.put("换源排序出错\n${it.localizedMessage}", it)
        }.getOrElse { synchronized(searchBooks) { searchBooks.toList() } }
    }.flowOn(IO)

    override fun onCleared() {
        stopSearch()
        super.onCleared()
    }

    @CallSuper
    open fun initData(arguments: Bundle?, book: Book?, fromReadBookActivity: Boolean) {
        arguments?.let { bundle ->
            bundle.getString("name")?.let {
                name = it
            }
            bundle.getString("author")?.let {
                author = it.replace(AppPattern.authorRegex, "")
            }
            this.fromReadBookActivity = fromReadBookActivity
            oldBook = book
        }
    }

    private fun initSearchPool() {
        searchPool = Executors
            .newFixedThreadPool(threadCount).asCoroutineDispatcher()
    }

    private fun currentFilter() = ChangeSourceFilter(
        currentSourceType, AppConfig.changeSourceSameTypeOnly, AppConfig.searchGroup
    )

    fun sourceFilterGroups(sources: List<BookSourcePart>): List<String> {
        val filter = currentFilter()
        updateGroupAvailability(sources, filter)
        val groups = sources.asSequence().filter { it.enabled }
            .flatMap { ChangeSourceFilter.splitGroups(it.bookSourceGroup).asSequence() }
            .toMutableSet()
        if (filter.group.isNotBlank()) groups.add(filter.group)
        return groups.sortedWith { a, b -> a.cnCompare(b) }
    }

    private fun updateGroupAvailability(sources: List<BookSourcePart>, filter: ChangeSourceFilter) {
        if (filter != currentFilter()) return
        selectedGroupUnavailable = filter.group.isNotBlank() && sources.none {
            filter.acceptsSource(it.bookSourceType, it.enabled, it.bookSourceGroup)
        }
    }

    private fun enabledSources(filter: ChangeSourceFilter): List<BookSourcePart> {
        val sources = appDb.bookSourceDao.allEnabledPart
        updateGroupAvailability(sources, filter)
        return sources.filter {
            filter.acceptsSource(it.bookSourceType, it.enabled, it.bookSourceGroup)
        }
    }

    private fun beginRequest(): ChangeSourceRequestGuard.Request = synchronized(searchLock) {
        stopSearch()
        requestGuard.begin(currentFilter())
    }

    private fun isCurrent(request: ChangeSourceRequestGuard.Request): Boolean =
        requestGuard.isCurrent(request, currentFilter())

    fun onSearchScopeChanged() {
        val request = beginRequest()
        synchronized(searchLock) {
            if (isCurrent(request)) {
                bookSourceParts.clear()
                _changeSourceProgress.value = 0 to ""
            }
        }
        execute {
            if (!isCurrent(request)) return@execute
            val empty = refresh(request)
            synchronized(searchLock) {
                if (!isCurrent(request)) return@execute
                if (empty) startSearch()
            }
        }
    }

    fun refresh(): Boolean = refresh(requestGuard.snapshot(currentFilter()))

    private fun refresh(request: ChangeSourceRequestGuard.Request): Boolean = synchronized(cacheLock) {
        val books = getDbSearchBooks(request)
        synchronized(searchLock) {
            if (isCurrent(request)) {
                searchBooks.clear()
                searchBooks.addAll(books)
                searchCallback?.upAdapter()
            }
            searchBooks.isEmpty()
        }
    }

    /**
     * 搜索书籍
     */
    fun startSearch() {
        val request = beginRequest()
        execute {
            if (!isCurrent(request)) return@execute
            val cachedBooks = getDbSearchBooks(request)
            val sources = enabledSources(request.filter)
            // 刷新仅清理当前范围的缓存，跨类型或其它分组缓存仍可恢复。
            synchronized(cacheLock) {
                if (!isCurrent(request)) return@execute
                if (cachedBooks.isNotEmpty()) {
                    appDb.searchBookDao.delete(*cachedBooks.toTypedArray())
                }
            }
            synchronized(searchLock) {
                if (!isCurrent(request)) return@execute
                searchBooks.clear()
                searchCallback?.upAdapter()
                bookSourceParts.clear()
                tocMap.clear()
                bookMap.clear()
                tocMapChapterCount = 0
                _changeSourceProgress.value = 0 to ""
                bookSourceParts.addAll(sources)
                initSearchPool()
                search(request)
            }
        }
    }

    fun startSearch(origin: String) {
        val request = beginRequest()
        execute {
            if (!isCurrent(request)) return@execute
            val cachedBooks = getDbSearchBooks(request)
            val sources = enabledSources(request.filter).filter { it.bookSourceUrl == origin }
            synchronized(searchLock) {
                if (!isCurrent(request)) return@execute
                bookSourceParts.clear()
                tocMap.clear()
                bookMap.clear()
                tocMapChapterCount = 0
                bookSourceParts.addAll(sources)
                // 编辑可改变书源类型、分组和启用状态，其余结果也重新守住范围。
                searchBooks.clear()
                searchBooks.addAll(cachedBooks)
                searchBooks.removeIf { it.origin == origin }
                searchCallback?.upAdapter()
                initSearchPool()
                search(request)
            }
        }
    }

    private fun search(request: ChangeSourceRequestGuard.Request) {
        val sources = bookSourceParts.toList()
        task = viewModelScope.launch(searchPool!! + interactionPolicy) {
            flow {
                for (bs in sources) {
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent(request)) return@flow
                    bs.getBookSource()?.let {
                        if (request.filter.acceptsSource(it.bookSourceType, it.enabled, it.bookSourceGroup)) {
                            emit(it)
                        }
                    }
                }
            }.onStart {
                synchronized(searchLock) {
                    if (isCurrent(request)) searchStateData.postValue(true)
                }
            }.mapParallel(threadCount) {
                try {
                    withTimeout(60000L) {
                        search(it, request)
                    }
                } catch (_: Throwable) {
                    currentCoroutineContext().ensureActive()
                }
                it
            }.onEachIndexed { index, value ->
                synchronized(searchLock) {
                    if (isCurrent(request)) {
                        _changeSourceProgress.update { index + 1 to value.bookSourceName }
                    }
                }
            }.onCompletion {
                ensureActive()
                synchronized(searchLock) {
                    if (isCurrent(request)) {
                        searchStateData.postValue(false)
                        searchFinishCallback?.invoke(searchBooks.isEmpty())
                    }
                }
            }.catch {
                AppLog.put("换源搜索出错\n${it.localizedMessage}", it)
            }.collect()
        }
    }

    fun setBlockSourceDialogs(blockDialogs: Boolean) {
        interactionPolicy.updateBlockDialogs(blockDialogs)
    }

    private suspend fun search(source: BookSource, request: ChangeSourceRequestGuard.Request) {
        val checkAuthor = AppConfig.changeSourceCheckAuthor
        val loadInfo = AppConfig.changeSourceLoadInfo
        val loadToc = AppConfig.changeSourceLoadToc
        val loadWordCount = AppConfig.changeSourceLoadWordCount
        val resultBooks = WebBook.searchBookAwait(
            source, name,
            filter = { fName, fAuthor, _ ->
                fName == name && (!checkAuthor || fAuthor.contains(author))
            })
        resultBooks.forEach { searchBook ->
            currentCoroutineContext().ensureActive()
            if (!isCurrent(request)) return
            when {
                loadInfo || loadToc || loadWordCount -> {
                    loadBookInfo(source, searchBook.toBook(), request)
                }

                else -> {
                    publishSearchBook(searchBook, request)
                }
            }
        }
    }

    private suspend fun loadBookInfo(
        source: BookSource, book: Book, request: ChangeSourceRequestGuard.Request
    ) {
        currentCoroutineContext().ensureActive()
        if (!isCurrent(request)) return
        if (book.tocUrl.isEmpty()) {
            WebBook.getBookInfoAwait(source, book)
        }
        if (AppConfig.changeSourceLoadToc || AppConfig.changeSourceLoadWordCount) {
            loadBookToc(source, book, request)
        } else {
            //从详情页里获取最新章节
            val searchBook = book.toSearchBook()
            publishSearchBook(searchBook, request)
        }
    }

    private suspend fun loadBookToc(
        source: BookSource, book: Book, request: ChangeSourceRequestGuard.Request
    ) {
        val chapters = WebBook.getChapterListAwait(source, book).getOrThrow()
        for (chapter in chapters) {
            chapter.internString()
        }
        currentCoroutineContext().ensureActive()
        synchronized(searchLock) {
            if (!isCurrent(request)) return
            if (tocMapChapterCount < 30000) {
                tocMapChapterCount += chapters.size
                tocMap[book.primaryStr()] = chapters
            }
            bookMap[book.primaryStr()] = book
        }
        book.releaseHtmlData()
        if (AppConfig.changeSourceLoadWordCount) {
            loadBookWordCount(source, book, chapters, request)
        } else {
            val searchBook = book.toSearchBook()
            publishSearchBook(searchBook, request)
        }
    }

    private suspend fun loadBookWordCount(
        source: BookSource,
        book: Book,
        chapters: List<BookChapter>,
        request: ChangeSourceRequestGuard.Request
    ) = coroutineScope {
        val chapterIndex = if (fromReadBookActivity) {
            BookHelp.getDurChapter(oldBook!!, chapters)
        } else {
            chapters.lastIndex
        }
        val bookChapter = chapters[chapterIndex]
        var title = bookChapter.title.trim()
        if (title.length > 20) {
            title = title.substring(0, 20) + "…"
        }
        val startTime = System.currentTimeMillis()
        val pair = try {
            val nextChapterUrl = chapters.getOrNull(chapterIndex + 1)?.url
            var content = WebBook.getContentAwait(source, book, bookChapter, nextChapterUrl, false)
            content = contentProcessor.getContent(oldBook!!, bookChapter, content, false).toString()
            val len = content.length
            len to "[${chapterIndex + 1}] ${title}\n字数：${len}"
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            -1 to "[${chapterIndex + 1}] ${title}\n获取字数失败：${t.localizedMessage}"
        }
        val endTime = System.currentTimeMillis()
        val searchBook = book.toSearchBook().apply {
            chapterWordCountText = pair.second
            chapterWordCount = pair.first
            respondTime = (endTime - startTime).toInt()
        }
        publishSearchBook(searchBook, request)
    }

    private suspend fun publishSearchBook(
        searchBook: SearchBook, request: ChangeSourceRequestGuard.Request
    ) {
        currentCoroutineContext().ensureActive()
        synchronized(cacheLock) {
            if (!isCurrent(request)) return
            val source = appDb.bookSourceDao.getBookSourcePart(searchBook.origin) ?: return
            if (!request.filter.acceptsSource(
                    source.bookSourceType, source.enabled, source.bookSourceGroup
                )
            ) return
            searchBook.releaseHtmlData()
            appDb.searchBookDao.insert(searchBook)
        }
        currentCoroutineContext().ensureActive()
        val latestSource = appDb.bookSourceDao.getBookSourcePart(searchBook.origin) ?: return
        synchronized(searchLock) {
            if (!isCurrent(request)) return
            if (!request.filter.acceptsSource(
                    latestSource.bookSourceType, latestSource.enabled, latestSource.bookSourceGroup
                )
            ) return
            if (screenKey.isEmpty() || searchBook.originName.contains(screenKey, true) ||
                searchBook.latestChapterTitle?.contains(screenKey, true) == true
            ) {
                searchBooks.remove(searchBook)
                searchBooks.add(searchBook)
                searchCallback?.upAdapter()
            }
        }
    }

    fun onLoadWordCountChecked(isChecked: Boolean) {
        if (isChecked) {
            startRefreshList(true)
        }
    }

    /**
     * 刷新列表
     */
    fun startRefreshList(onlyRefreshNoWordCountBook: Boolean = false) {
        val request = beginRequest()
        execute {
            if (!isCurrent(request)) return@execute
            val cachedBooks = getDbSearchBooks(request)
            val sources = enabledSources(request.filter)
            synchronized(searchLock) {
                if (!isCurrent(request)) return@execute
                searchBooks.clear()
                searchBooks.addAll(cachedBooks)
                searchBookList.clear()
                if (onlyRefreshNoWordCountBook) {
                    synchronized(searchBooks) {
                        searchBooks.filterTo(searchBookList) { it.chapterWordCountText == null }
                    }
                    searchBooks.removeIf { it.chapterWordCountText == null }
                } else {
                    synchronized(searchBooks) { searchBookList.addAll(searchBooks) }
                    searchBooks.clear()
                }
                val origins = searchBookList.mapTo(hashSetOf()) { it.origin }
                bookSourceParts.clear()
                bookSourceParts.addAll(sources.filter {
                    it.bookSourceUrl in origins
                })
                _changeSourceProgress.value = 0 to ""
                searchCallback?.upAdapter()
                initSearchPool()
                refreshList(request)
            }
        }
    }

    private fun refreshList(request: ChangeSourceRequestGuard.Request) {
        val books = searchBookList.toList()
        val remaining = books.groupingBy { it.origin }.eachCount().toMutableMap()
        var completedSources = 0
        task = viewModelScope.launch(searchPool!! + interactionPolicy) {
            flow {
                for (searchBook in books) {
                    currentCoroutineContext().ensureActive()
                    if (!isCurrent(request)) return@flow
                    emit(searchBook)
                }
            }.onStart {
                synchronized(searchLock) {
                    if (isCurrent(request)) searchStateData.postValue(true)
                }
            }.mapParallel(threadCount) {
                try {
                    val source = appDb.bookSourceDao.getBookSource(it.origin)
                    if (source != null && request.filter.acceptsSource(
                            source.bookSourceType, source.enabled, source.bookSourceGroup
                        )
                    ) {
                        withTimeout(60000L) {
                            loadBookInfo(source, it.toBook(), request)
                        }
                    }
                } catch (_: Throwable) {
                    currentCoroutineContext().ensureActive()
                }
                it
            }.onEachIndexed { _, book ->
                val count = remaining.getValue(book.origin) - 1
                remaining[book.origin] = count
                if (count == 0) {
                    synchronized(searchLock) {
                        if (isCurrent(request)) {
                            _changeSourceProgress.value = ++completedSources to book.originName
                        }
                    }
                }
            }.onCompletion {
                ensureActive()
                synchronized(searchLock) {
                    if (isCurrent(request)) searchStateData.postValue(false)
                }
            }.catch {
                AppLog.put("换源刷新列表出错\n${it.localizedMessage}", it)
            }.collect()
        }
    }

    private fun getDbSearchBooks(request: ChangeSourceRequestGuard.Request): List<SearchBook> = synchronized(cacheLock) {
        if (!isCurrent(request)) return@synchronized emptyList()
        val filter = request.filter
        val origins = enabledSources(filter).mapTo(hashSetOf()) { it.bookSourceUrl }
        if (origins.isEmpty()) return@synchronized emptyList()
        val books = if (screenKey.isEmpty()) {
            if (AppConfig.changeSourceCheckAuthor) {
                appDb.searchBookDao.changeSourceByGroup(
                    name, author, ""
                )
            } else {
                appDb.searchBookDao.changeSourceByGroup(
                    name, "", ""
                )
            }
        } else {
            if (AppConfig.changeSourceCheckAuthor) {
                appDb.searchBookDao.changeSourceSearch(
                    name, author, screenKey, ""
                )
            } else {
                appDb.searchBookDao.changeSourceSearch(
                    name, "", screenKey, ""
                )
            }
        }
        books.filter { it.origin in origins }
    }

    /**
     * 筛选
     */
    fun screen(key: String?) {
        synchronized(searchLock) { screenKey = key?.trim() ?: "" }
        execute {
            refresh()
        }
    }

    fun startOrStopSearch() {
        if (task == null || !task!!.isActive) {
            startSearch()
        } else {
            stopSearch()
        }
    }

    fun stopSearch() {
        synchronized(searchLock) {
            requestGuard.invalidate()
            task?.cancel()
            task = null
            searchPool?.close()
            searchPool = null
            searchStateData.postValue(false)
        }
    }

    fun getToc(
        book: Book,
        onSuccess: (toc: List<BookChapter>, source: BookSource) -> Unit,
        onError: (e: Throwable) -> Unit
    ): Coroutine<Pair<List<BookChapter>, BookSource>> {
        return execute {
            val toc = tocMap[book.primaryStr()]
            if (toc != null) {
                val source = appDb.bookSourceDao.getBookSource(book.origin)
                return@execute Pair(toc, source!!)
            }
            val result = getToc(book).getOrThrow()
            tocMap[book.primaryStr()] = result.first
            return@execute result
        }.onSuccess {
            onSuccess.invoke(it.first, it.second)
        }.onError {
            onError.invoke(it)
        }
    }

    suspend fun getToc(book: Book): Result<Pair<List<BookChapter>, BookSource>> {
        return kotlin.runCatching {
            val source = appDb.bookSourceDao.getBookSource(book.origin)
                ?: throw NoStackTraceException("书源不存在")
            if (book.tocUrl.isEmpty()) {
                WebBook.getBookInfoAwait(source, book)
            }
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
            Pair(toc, source)
        }
    }

    fun disableSource(searchBook: SearchBook) {
        execute {
            appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
                source.enabled = false
                appDb.bookSourceDao.update(source)
            }
            refresh()
        }
    }

    fun topSource(searchBook: SearchBook) {
        execute {
            appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
                val minOrder = appDb.bookSourceDao.minOrder - 1
                source.customOrder = minOrder
                searchBook.originOrder = source.customOrder
                appDb.bookSourceDao.update(source)
                updateSource(searchBook)
            }
            searchCallback?.upAdapter()
        }
    }

    fun bottomSource(searchBook: SearchBook) {
        execute {
            appDb.bookSourceDao.getBookSource(searchBook.origin)?.let { source ->
                val maxOrder = appDb.bookSourceDao.maxOrder + 1
                source.customOrder = maxOrder
                searchBook.originOrder = source.customOrder
                appDb.bookSourceDao.update(source)
                updateSource(searchBook)
            }
            searchCallback?.upAdapter()
        }
    }

    fun updateSource(searchBook: SearchBook) {
        appDb.searchBookDao.update(searchBook)
    }

    fun del(searchBook: SearchBook) {
        execute {
            SourceHelp.deleteBookSource(searchBook.origin)
            appDb.searchBookDao.delete(searchBook)
            refresh()
        }
        searchBooks.remove(searchBook)
        searchCallback?.upAdapter()
    }

    fun autoChangeSource(
        bookType: Int?,
        onSuccess: (book: Book, toc: List<BookChapter>, source: BookSource) -> Unit
    ) {
        val request = requestGuard.snapshot(currentFilter())
        val filter = request.filter
        execute {
            val books = getDbSearchBooks(request)
            books.forEach {
                currentCoroutineContext().ensureActive()
                if (!isCurrent(request)) throw CancellationException("换源范围已变化")
                if (it.type == bookType) {
                    val book = it.toBook()
                    val result = getToc(book).getOrNull()
                    if (result != null &&
                        filter.acceptsSource(result.second.bookSourceType, result.second.enabled,
                            result.second.bookSourceGroup)
                    ) {
                        return@execute Triple(book, result.first, result.second)
                    }
                }
            }
            throw NoStackTraceException("没有有效源")
        }.onSuccess {
            if (isCurrent(request)) onSuccess.invoke(it.first, it.second, it.third)
        }.onError {
            if (it !is CancellationException) {
                context.toastOnUi("自动换源失败\n${it.localizedMessage}")
            }
        }
    }

    fun setBookScore(searchBook: SearchBook, score: Int) {
        execute {
            SourceConfig.setBookScore(searchBook.origin, searchBook.name, searchBook.author, score)
            searchCallback?.upAdapter()
        }
    }

    fun getBookScore(searchBook: SearchBook): Int {
        return SourceConfig.getBookScore(searchBook.origin, searchBook.name, searchBook.author)
    }

    private fun getChapterNum(wordCountText: String?): Int {
        wordCountText ?: return -1
        return chapterNumRegex.find(wordCountText)?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    interface SourceCallback {

        fun upAdapter()

    }

}
