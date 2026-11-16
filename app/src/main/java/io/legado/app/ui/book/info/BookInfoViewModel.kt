package io.legado.app.ui.book.info

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoBooksDirException
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.getExportFileName
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.book.updateTo
import io.legado.app.help.book.withChapterCachePublication
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.SourceInteractionBlockedException
import io.legado.app.help.source.SourceInteractionPolicy
import io.legado.app.model.AudioPlay
import io.legado.app.model.BookCacheManager
import io.legado.app.model.BookCover
import io.legado.app.model.ReadBook
import io.legado.app.model.ReadManga
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.UrlUtil
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class BookInfoViewModel(application: Application) : BaseViewModel(application) {
    val bookData = MutableLiveData<Book>()
    val chapterListData = MutableLiveData<List<BookChapter>>()
    val otherWorksData = MutableLiveData<OtherWorksState>(OtherWorksState.Idle)
    val otherWorksLoadingData = MutableLiveData(false)
    val bookshelfChanged = MutableLiveData<Unit>()
    val webFiles = mutableListOf<WebFile>()
    private class BookRequest(
        val scope: CoroutineScope,
        @Volatile var inBookshelf: Boolean = false,
        @Volatile var hasCustomBtn: Boolean = false,
        @Volatile var bookSource: BookSource? = null,
    ) {
        val loadedChapters = BookInfoLoadedChapters()
    }

    private suspend fun <T> withRequestChapterPublication(
        request: BookRequest,
        bookUrl: String,
        block: () -> T,
    ): T = request.loadedChapters.withPublication { withChapterCachePublication(bookUrl, block) }

    private fun newBookRequest(previous: BookRequest? = null): BookRequest {
        val job = SupervisorJob(viewModelScope.coroutineContext[Job])
        return BookRequest(
            scope = CoroutineScope(viewModelScope.coroutineContext + job),
            inBookshelf = previous?.inBookshelf ?: false,
            hasCustomBtn = previous?.hasCustomBtn ?: false,
            bookSource = previous?.bookSource,
        )
    }

    private val bookRequests = BookInfoRequestGate(newBookRequest()) { update ->
        viewModelScope.launch(Dispatchers.Main.immediate) { update() }
    }

    private fun beginBookRequest(): BookRequest {
        val previous = bookRequests.current
        val request = newBookRequest(previous)
        bookRequests.begin(request)
        previous.scope.cancel()
        return request
    }

    var inBookshelf: Boolean
        get() = bookRequests.current.inBookshelf
        set(value) { bookRequests.current.inBookshelf = value }
    var hasCustomBtn: Boolean
        get() = bookRequests.current.hasCustomBtn
        set(value) { bookRequests.current.hasCustomBtn = value }
    var bookSource: BookSource?
        get() = bookRequests.current.bookSource
        set(value) { bookRequests.current.bookSource = value }
    private var changeSourceCoroutine: Coroutine<*>? = null
    private var otherWorksBookKey: String? = null
    private var otherWorksSearchCoroutine: Coroutine<*>? = null
    private var otherWorksSearchKey: String? = null
    private var otherWorksSearchInBackground = false
    private var lastManualOtherWorksSearchAt = 0L
    private var autoLoadOtherWorksEnabled = false
    private val bookshelf: MutableSet<String> = ConcurrentHashMap.newKeySet()
    val waitDialogData = MutableLiveData<Boolean>()
    val actionLive = MutableLiveData<String>()

    init {
        execute {
            appDb.bookDao.flowAll()
                .catch {
                    AppLog.put("书籍详情页获取书架数据失败\n${it.localizedMessage}", it)
                }.collect { books ->
                    val keys = arrayListOf<String>()
                    books.filterNot { it.isNotShelf }
                        .forEach {
                            keys.add("${it.name}-${it.author}")
                            keys.add(it.name)
                            keys.add(it.bookUrl)
                        }
                    bookshelf.clear()
                    bookshelf.addAll(keys)
                    bookshelfChanged.postValue(Unit)
                }
        }.onError {
            AppLog.put("加载书架数据失败", it)
        }
    }

    private data class ResolvedBook(
        val book: Book,
        val inBookshelf: Boolean,
        val source: BookSource?,
    )

    fun initData(intent: Intent) {
        val request = beginBookRequest()
        execute(request.scope) {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            val bookUrl = intent.getStringExtra("bookUrl") ?: ""
            val saved = appDb.bookDao.getBook(name, author)
                ?: bookUrl.takeIf(String::isNotBlank)?.let { appDb.bookDao.getBook(it) }
            val book = saved
                ?: bookUrl.takeIf(String::isNotBlank)?.let { appDb.searchBookDao.getSearchBook(it)?.toBook() }
                ?: appDb.searchBookDao.getFirstByNameAuthor(name, author)?.toBook()
                ?: throw NoStackTraceException("未找到书籍")
            val source = if (book.isLocal) null else appDb.bookSourceDao.getBookSource(book.origin)
            ResolvedBook(book, saved != null && !saved.isNotShelf, source)
        }.onSuccess {
            showResolvedBook(request, it)
        }.onError {
            if (!bookRequests.isCurrent(request)) return@onError
            AppLog.put(it.localizedMessage, it)
            context.toastOnUi(it.localizedMessage)
        }
    }

    fun upBook(intent: Intent) {
        val request = bookRequests.current
        execute(request.scope) {
            val name = intent.getStringExtra("name") ?: ""
            val author = intent.getStringExtra("author") ?: ""
            appDb.bookDao.getBook(name, author)?.let { book ->
                val source = if (book.isLocal) null else appDb.bookSourceDao.getBookSource(book.origin)
                ResolvedBook(book, request.inBookshelf, source)
            }
        }.onSuccess {
            it?.let { resolved -> showResolvedBook(request, resolved, updateShelf = false) }
        }
    }

    private fun showResolvedBook(request: BookRequest, resolved: ResolvedBook, updateShelf: Boolean = true) {
        if (!bookRequests.isCurrent(request)) return
        val book = resolved.book
        if (updateShelf) request.inBookshelf = resolved.inBookshelf
        request.bookSource = resolved.source
        request.hasCustomBtn = resolved.source?.customButton ?: false
        if (bookData.value?.bookUrl != book.bookUrl) {
            chapterListData.value = emptyList()
            webFiles.clear()
        }
        bookData.value = book
        upCoverByRule(book, request)
        if (book.tocUrl.isEmpty() && !book.isLocal) {
            loadBookInfoForRequest(book, true, request.inBookshelf, request.scope, request)
        } else {
            execute(request.scope) {
                appDb.bookChapterDao.getChapterList(book.bookUrl)
            }.onSuccess { chapterList ->
                if (!bookRequests.isCurrent(request) || bookData.value?.bookUrl != book.bookUrl) return@onSuccess
                if (chapterList.isNotEmpty()) {
                    request.loadedChapters.record(book.bookUrl, chapterList)
                    chapterListData.value = chapterList
                } else {
                    loadChapterForRequest(book, true, request.scope, true, request)
                }
            }
        }
    }

    private fun upCoverByRule(book: Book, request: BookRequest) {
        execute(request.scope) {
            if (book.coverUrl.isNullOrBlank() && book.customCoverUrl.isNullOrBlank()) {
                BookCover.searchCover(book)
            } else null
        }.onSuccess { coverUrl ->
            if (!bookRequests.isCurrent(request) || bookData.value !== book) return@onSuccess
            if (!coverUrl.isNullOrBlank()) {
                book.customCoverUrl = coverUrl
                bookData.value = book
                if (request.inBookshelf) {
                    saveBook(book)
                }
            }
        }
    }

    fun prepareOtherWorks(book: Book, autoLoad: Boolean) {
        autoLoadOtherWorksEnabled = autoLoad
        val key = "${book.name}\n${book.author}\n${book.origin}"
        if (otherWorksBookKey != key) {
            otherWorksSearchCoroutine?.cancel()
            otherWorksSearchCoroutine = null
            otherWorksSearchKey = null
            otherWorksBookKey = key
            otherWorksData.value = OtherWorksState.Idle
            otherWorksLoadingData.value = false
            loadCachedOtherWorks(book, autoLoad)
        }
    }

    fun setAutoLoadOtherWorks(enabled: Boolean) {
        autoLoadOtherWorksEnabled = enabled
        if (!enabled) {
            if (otherWorksSearchInBackground) {
                otherWorksSearchCoroutine?.cancel()
                otherWorksSearchCoroutine = null
                otherWorksSearchKey = null
                otherWorksLoadingData.value = false
            }
            return
        }
        bookData.value?.let { loadCachedOtherWorks(it, autoLoad = true) }
    }

    private fun loadCachedOtherWorks(book: Book, autoLoad: Boolean) {
        val source = bookSource ?: return
        val author = normalizeAuthor(book.author)
        if (author.isBlank()) return
        val bookKey = "${book.name}\n${book.author}\n${book.origin}"
        val requestKey = otherWorksRequestKey(source, author)
        execute {
            val cached = filterOtherWorks(
                book,
                appDb.searchBookDao.getByOriginAuthor(source.bookSourceUrl, author),
            )
            CachedOtherWorks(
                items = cached,
                isFresh = maxOf(
                    cached.maxOfOrNull(SearchBook::time) ?: 0L,
                    context.getPrefLong(otherWorksLastCheckKey(requestKey)),
                ).let { newest ->
                    newest > 0L &&
                        System.currentTimeMillis() - newest <= OTHER_WORKS_CACHE_TTL
                },
            )
        }.onSuccess {
            if (otherWorksBookKey != bookKey) {
                return@onSuccess
            }
            if (it.items.isNotEmpty()) {
                otherWorksData.value = OtherWorksState.Success(it.items)
            }
            if (autoLoadOtherWorksEnabled && autoLoad && !it.isFresh) {
                searchOtherWorks(
                    book = book,
                    source = source,
                    author = author,
                    requestKey = requestKey,
                    background = true,
                )
            }
        }.onError {
            AppLog.put("加载作者其它作品缓存失败\n${it.localizedMessage}", it)
            if (otherWorksBookKey != bookKey) {
                return@onError
            }
            if (autoLoadOtherWorksEnabled && autoLoad) {
                searchOtherWorks(
                    book = book,
                    source = source,
                    author = author,
                    requestKey = requestKey,
                    background = true,
                )
            }
        }
    }

    fun searchOtherWorks() {
        if (otherWorksSearchCoroutine?.isActive == true && !otherWorksSearchInBackground) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastManualOtherWorksSearchAt < OTHER_WORKS_REFRESH_DEBOUNCE) {
            return
        }
        lastManualOtherWorksSearchAt = now
        val book = bookData.value ?: return
        val source = bookSource ?: let {
            otherWorksData.value = OtherWorksState.Error(context.getString(R.string.error_no_source))
            return
        }
        val author = normalizeAuthor(book.author)
        if (author.isBlank()) {
            otherWorksData.value = OtherWorksState.Empty
            return
        }
        searchOtherWorks(
            book = book,
            source = source,
            author = author,
            requestKey = otherWorksRequestKey(source, author),
            background = false,
        )
    }

    private fun searchOtherWorks(
        book: Book,
        source: BookSource,
        author: String,
        requestKey: String,
        background: Boolean,
    ) {
        if (background && otherWorksSearchCoroutine?.isActive == true) {
            if (!otherWorksSearchInBackground || otherWorksSearchKey == requestKey) {
                return
            }
        }
        otherWorksSearchCoroutine?.cancel()
        otherWorksSearchKey = requestKey
        otherWorksSearchInBackground = background
        if (!background) {
            otherWorksData.value = OtherWorksState.Loading
        }
        otherWorksLoadingData.value = true
        val blockDialogs = background ||
            context.getPrefBoolean(PreferKey.searchBlockSourceDialogs)
        var coroutine: Coroutine<List<SearchBook>>? = null
        coroutine = execute(context = IO + SourceInteractionPolicy(blockDialogs)) {
            context.putPrefLong(otherWorksLastCheckKey(requestKey), System.currentTimeMillis())
            val items = try {
                WebBook.searchBookAwait(
                    bookSource = source,
                    key = author,
                    page = 1,
                    filter = { _, itemAuthor, _ ->
                        normalizeAuthor(itemAuthor) == author
                    },
                )
            } catch (error: SourceInteractionBlockedException) {
                currentCoroutineContext().ensureActive()
                AppLog.putDebug("${source.bookSourceName}\n${error.localizedMessage}")
                emptyList()
            } catch (error: Throwable) {
                currentCoroutineContext().ensureActive()
                if (!background) throw error
                AppLog.putDebug(
                    "后台加载作者其它作品失败\n${source.bookSourceName}\n${error.localizedMessage}",
                )
                emptyList()
            }.onEach {
                it.releaseHtmlData()
            }.let { filterOtherWorks(book, it) }
            if (items.isNotEmpty()) {
                appDb.searchBookDao.insert(*items.toTypedArray())
            }
            items
        }.onSuccess { items ->
            if (otherWorksBookKey != "${book.name}\n${book.author}\n${book.origin}") {
                return@onSuccess
            }
            if (background) {
                if (items.isNotEmpty()) {
                    otherWorksData.value = OtherWorksState.Success(items)
                }
            } else {
                otherWorksData.value =
                    if (items.isEmpty()) {
                        OtherWorksState.Empty
                    } else {
                        OtherWorksState.Success(items)
                    }
            }
        }.onError { error ->
            AppLog.put("搜索作者其它作品失败\n${error.localizedMessage}", error)
            if (!background && otherWorksBookKey == "${book.name}\n${book.author}\n${book.origin}") {
                otherWorksData.value = OtherWorksState.Error(error.localizedMessage ?: error.javaClass.simpleName)
            }
        }.onFinally {
            if (otherWorksSearchCoroutine === coroutine) {
                otherWorksSearchCoroutine = null
                otherWorksSearchKey = null
                otherWorksLoadingData.value = false
            }
        }
        otherWorksSearchCoroutine = coroutine
    }

    fun isInBookShelf(book: SearchBook): Boolean {
        val name = book.name
        val author = book.author
        val bookUrl = book.bookUrl
        val key = if (author.isNotBlank()) "$name-$author" else name
        return bookshelf.contains(key) || bookshelf.contains(bookUrl)
    }

    private fun filterOtherWorks(book: Book, books: List<SearchBook>): List<SearchBook> {
        val author = normalizeAuthor(book.author)
        val currentName = book.name.trim()
        return books.filter {
            normalizeAuthor(it.author) == author && it.name.trim() != currentName
        }.distinctBy {
            it.bookUrl.ifBlank { "${it.name}-${it.author}-${it.origin}" }
        }
    }

    private fun normalizeAuthor(author: String): String {
        return author.replace(AppPattern.authorRegex, "").trim()
    }

    private fun otherWorksRequestKey(source: BookSource, author: String): String {
        return "${source.bookSourceUrl}\n$author"
    }

    private fun otherWorksLastCheckKey(requestKey: String): String {
        return PreferKey.bookOtherWorksLastCheckPrefix + MD5Utils.md5Encode16(requestKey)
    }

    fun refreshBook(book: Book) {
        val request = bookRequests.current
        executeLazy(scope = request.scope, executeContext = IO) {
            if (book.isLocal) {
                book.tocUrl = ""
            } else {
                val bs = request.bookSource ?: return@executeLazy
                if (book.originName != bs.bookSourceName) {
                    book.originName = bs.bookSourceName
                }
            }
        }.onError {
            if (!bookRequests.isCurrent(request)) return@onError
            AppLog.put("刷新书籍<${book.name}>失败", it)
        }.onFinally {
            if (bookRequests.isCurrent(request)) {
                loadBookInfoForRequest(book, false, true, request.scope, request)
            }
        }.start()
    }

    fun loadBookInfo(
        book: Book,
        canReName: Boolean = true,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = bookRequests.current.scope
    ) {
        loadBookInfoForRequest(book, canReName, runPreUpdateJs, scope, bookRequests.current)
    }

    private fun loadBookInfoForRequest(
        book: Book,
        canReName: Boolean,
        runPreUpdateJs: Boolean,
        scope: CoroutineScope,
        request: BookRequest,
    ) {
        if (!bookRequests.isCurrent(request)) return
        if (book.isLocal) {
            LocalBook.upBookInfo(book)
            bookRequests.publish(request) { bookData.value = book }
            loadChapterForRequest(book, true, scope, false, request)
        } else {
            val bookSource = request.bookSource ?: let {
                bookRequests.publish(request) {
                    chapterListData.value = emptyList()
                    context.toastOnUi(R.string.error_no_source)
                }
                return
            }
            val requestBookUrl = book.bookUrl
            val requestReverseToc = book.getReverseToc()
            WebBook.getBookInfo(scope, bookSource, book, canReName = canReName)
                .onSuccess(IO) { loadedBook ->
                    withRequestChapterPublication(request, requestBookUrl) {
                        if (!bookRequests.isCurrent(request)) return@withRequestChapterPublication
                        val latest = appDb.bookDao.getBook(requestBookUrl)
                        if (latest != null && latest.getReverseToc() != requestReverseToc) {
                            val chapters = appDb.bookChapterDao.getChapterList(latest.bookUrl)
                            request.loadedChapters.record(latest.bookUrl, chapters)
                            bookRequests.publish(request) {
                                bookData.value = latest
                                chapterListData.value = chapters
                            }
                            return@withRequestChapterPublication
                        }
                        val dbBook = appDb.bookDao.getBook(book.name, book.author)
                        if (!bookRequests.isCurrent(request)) return@withRequestChapterPublication
                        if (!request.inBookshelf && dbBook != null && !dbBook.isNotShelf && dbBook.origin == book.origin) {
                            /**
                             * book 来自搜索时(inBookshelf == false)，搜索的书名不存在于书架，但是加载详情后，书名更新，存在同名书籍
                             * 此时 book 的数据会与数据库中的不同，需要更新 #3652 #4619
                             * book 加载详情后虽然书名作者相同，但是又可能不是数据库中(书源不同)的那本书 #3149
                             */
                            dbBook.updateTo(loadedBook)
                            request.inBookshelf = true
                        }
                        bookRequests.publish(request) { bookData.value = loadedBook }
                        if (request.inBookshelf) {
                            loadedBook.save()
                        }
                        if (loadedBook.isWebFile) {
                            loadWebFile(loadedBook, request)
                        } else {
                            loadChapterForRequest(loadedBook, runPreUpdateJs, scope, true, request)
                        }
                    }
                }.onError {
                    if (!bookRequests.isCurrent(request)) return@onError
                    AppLog.put("获取书籍信息失败\n${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_book_info)
                }
        }
    }

    fun loadChapter(
        book: Book,
        runPreUpdateJs: Boolean = true,
        scope: CoroutineScope = bookRequests.current.scope,
        isFromBookInfo: Boolean = false
    ) {
        loadChapterForRequest(book, runPreUpdateJs, scope, isFromBookInfo, bookRequests.current)
    }

    private fun loadChapterForRequest(
        book: Book,
        runPreUpdateJs: Boolean,
        scope: CoroutineScope,
        isFromBookInfo: Boolean,
        request: BookRequest,
    ) {
        if (!bookRequests.isCurrent(request)) return
        val requestBookUrl = book.bookUrl
        val requestReverseToc = book.getReverseToc()
        if (book.isLocal) {
            execute(scope) {
                val chapters = LocalBook.getChapterList(book)
                withRequestChapterPublication(request, requestBookUrl) {
                    if (!bookRequests.isCurrent(request)) return@withRequestChapterPublication
                    val latest = appDb.bookDao.getBook(requestBookUrl)
                    if (latest != null && latest.getReverseToc() != requestReverseToc) {
                        val currentChapters = appDb.bookChapterDao.getChapterList(latest.bookUrl)
                        request.loadedChapters.record(latest.bookUrl, currentChapters)
                        bookRequests.publish(request) {
                            bookData.value = latest
                            chapterListData.value = currentChapters
                        }
                        return@withRequestChapterPublication
                    }
                    if (!bookRequests.isCurrent(request)) return@withRequestChapterPublication
                    request.loadedChapters.record(book.bookUrl, chapters)
                    appDb.bookDao.update(book)
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*chapters.toTypedArray())
                    ReadBook.onChapterListUpdated(book)
                    bookRequests.publish(request) {
                        bookData.value = book
                        chapterListData.value = chapters
                    }
                }
            }.onError {
                if (!bookRequests.isCurrent(request)) return@onError
                context.toastOnUi("LoadTocError:${it.localizedMessage}")
            }
        } else {
            val bookSource = request.bookSource ?: let {
                bookRequests.publish(request) {
                    chapterListData.value = emptyList()
                    context.toastOnUi(R.string.error_no_source)
                }
                return
            }
            val oldBook = book.copy()
            WebBook.getChapterList(scope, bookSource, book, runPreUpdateJs, isFromBookInfo = isFromBookInfo)
                .onSuccess(IO) { chapters ->
                    withRequestChapterPublication(request, requestBookUrl) {
                        if (!bookRequests.isCurrent(request)) return@withRequestChapterPublication
                        val latest = appDb.bookDao.getBook(requestBookUrl)
                        if (latest != null && latest.getReverseToc() != requestReverseToc) {
                            val currentChapters = appDb.bookChapterDao.getChapterList(latest.bookUrl)
                            request.loadedChapters.record(latest.bookUrl, currentChapters)
                            bookRequests.publish(request) {
                                bookData.value = latest
                                chapterListData.value = currentChapters
                            }
                            return@withRequestChapterPublication
                        }
                        if (!bookRequests.isCurrent(request)) return@withRequestChapterPublication
                        request.loadedChapters.record(book.bookUrl, chapters)
                        if (request.inBookshelf) {
                            book.removeType(BookType.updateError)
                            appDb.bookDao.replace(oldBook, book)
                            /**
                             * runPreUpdateJs 有可能会修改 book 的 bookUrl
                             */
                            if (oldBook.bookUrl != book.bookUrl) {
                                BookHelp.updateCacheFolder(oldBook, book)
                            }
                            appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                            appDb.bookChapterDao.insert(*chapters.toTypedArray())
                            ReadBook.onChapterListUpdated(book)
                        }
                        bookRequests.publish(request) {
                            bookData.value = book
                            chapterListData.value = chapters
                        }
                    }
                }.onError {
                    if (!bookRequests.isCurrent(request)) return@onError
                    request.loadedChapters.clear()
                    chapterListData.value = emptyList()
                    AppLog.put("获取目录失败\n${it.localizedMessage}", it)
                    context.toastOnUi(R.string.error_get_chapter_list)
                }
        }
    }


    fun loadGroup(groupId: Long, success: ((groupNames: String?) -> Unit)) {
        execute {
            appDb.bookGroupDao.getGroupNames(groupId).joinToString(",")
        }.onSuccess {
            success.invoke(it)
        }
    }

    private fun loadWebFile(book: Book, request: BookRequest) {
        execute(request.scope) {
            val fileNameNoExtension = if (book.author.isBlank()) book.name
            else "${book.name} 作者：${book.author}"
            book.downloadUrls!!.map {
                val analyzeUrl = AnalyzeUrl(
                    it, source = request.bookSource,
                    coroutineContext = coroutineContext
                )
                var mFileName = UrlUtil.getFileName(analyzeUrl)
                    ?: fileNameNoExtension
                analyzeUrl.type?.let { suffix ->
                    mFileName += ".${suffix}"
                }
                WebFile(it, mFileName)
            }
        }.onError {
            if (!bookRequests.isCurrent(request)) return@onError
            context.toastOnUi("LoadWebFileError\n${it.localizedMessage}")
        }.onSuccess {
            if (!bookRequests.isCurrent(request)) return@onSuccess
            webFiles.clear()
            webFiles.addAll(it)
            book.latestChapterTitle = "已下载"
            bookData.value = book
            chapterListData.value = emptyList()
        }
    }

    /* 导入或者下载在线文件 */
    fun <T> importOrDownloadWebFile(webFile: WebFile, success: ((T) -> Unit)?) {
        val request = bookRequests.current
        val source = request.bookSource ?: return
        val previousBook = bookData.value ?: return
        execute {
            waitDialogData.postValue(true)
            if (webFile.isSupported) {
                val book = LocalBook.importFileOnLine(
                    webFile.url,
                    previousBook.getExportFileName(webFile.suffix),
                    source
                )
                changeToLocalBook(book, previousBook, request)
            } else {
                LocalBook.saveBookFile(
                    webFile.url,
                    previousBook.getExportFileName(webFile.suffix),
                    source
                )
            }
        }.onSuccess {
            if (!bookRequests.isCurrent(request)) return@onSuccess
            @Suppress("unchecked_cast")
            success?.invoke(it as T)
        }.onError {
            if (!bookRequests.isCurrent(request)) return@onError
            when (it) {
                is NoBooksDirException -> actionLive.postValue("selectBooksDir")
                else -> {
                    AppLog.put("ImportWebFileError\n${it.localizedMessage}", it)
                    context.toastOnUi("ImportWebFileError\n${it.localizedMessage}")
                    webFiles.remove(webFile)
                }
            }
        }.onFinally {
            waitDialogData.postValue(false)
        }
    }

    fun getArchiveFilesName(archiveFileUri: Uri, onSuccess: (List<String>) -> Unit) {
        execute {
            ArchiveUtils.getArchiveFilesName(archiveFileUri) {
                AppPattern.bookFileRegex.matches(it)
            }
        }.onError {
            AppLog.put("getArchiveEntriesName Error:\n${it.localizedMessage}", it)
            context.toastOnUi("getArchiveEntriesName Error:\n${it.localizedMessage}")
        }.onSuccess {
            onSuccess.invoke(it)
        }
    }

    fun importArchiveBook(
        archiveFileUri: Uri,
        archiveEntryName: String,
        success: ((Book) -> Unit)? = null
    ) {
        val request = bookRequests.current
        val previousBook = bookData.value ?: return
        execute {
            val suffix = archiveEntryName.substringAfterLast(".")
            LocalBook.importArchiveFile(
                archiveFileUri,
                previousBook.getExportFileName(suffix)
            ) {
                it.contains(archiveEntryName)
            }.first()
        }.onSuccess {
            val book = changeToLocalBook(it, previousBook, request)
            if (bookRequests.isCurrent(request)) success?.invoke(book)
        }.onError {
            AppLog.put("importArchiveBook Error:\n${it.localizedMessage}", it)
            if (bookRequests.isCurrent(request)) {
                context.toastOnUi("importArchiveBook Error:\n${it.localizedMessage}")
            }
        }
    }

    fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        changeSourceCoroutine?.cancel()
        val request = beginBookRequest()
        val previousBook = bookData.value
        changeSourceCoroutine = execute {
            if (!bookRequests.isCurrent(request)) return@execute
            request.bookSource = source
            request.hasCustomBtn = source.customButton
            previousBook?.migrateTo(book, toc)
            if (book.isWebFile) {
                loadWebFile(book, request)
            }
            if (request.inBookshelf) {
                book.removeType(BookType.updateError)
                previousBook?.delete()
                appDb.bookDao.insert(book)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
            }
            request.loadedChapters.record(book.bookUrl, toc)
            bookRequests.publish(request) {
                bookData.value = book
                chapterListData.value = toc
            }
        }.onFinally {
            postEvent(EventBus.SOURCE_CHANGED, book.bookUrl)
        }
    }

    fun topBook() {
        val request = bookRequests.current
        val book = bookData.value ?: return
        execute {
            if (!bookRequests.isCurrent(request)) return@execute
            val minOrder = appDb.bookDao.minOrder
            book.order = minOrder - 1
            book.durChapterTime = System.currentTimeMillis()
            appDb.bookDao.update(book)
        }
    }

    fun saveBook(book: Book?, success: (() -> Unit)? = null) {
        book ?: return
        val request = bookRequests.current
        execute {
            if (book.order == 0) {
                book.order = appDb.bookDao.minOrder - 1
            }
            appDb.bookDao.getBook(book.name, book.author)?.let {
                book.durChapterIndex = it.durChapterIndex
                book.durChapterPos = it.durChapterPos
                book.durChapterTitle = it.durChapterTitle
            }
            book.save()
            if (ReadBook.book?.isSameNameAuthor(book) == true) {
                ReadBook.book = book
            } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                AudioPlay.book = book
            }
        }.onSuccess {
            if (bookRequests.isCurrent(request)) success?.invoke()
        }
    }

    fun currentChapterList(book: Book? = bookData.value): List<BookChapter>? =
        chaptersForBook(book?.bookUrl, chapterListData.value)

    fun saveChapterList(success: (() -> Unit)?) {
        val request = bookRequests.current
        val chapters = currentChapterList() ?: return
        execute {
            if (!bookRequests.isCurrent(request)) return@execute
            appDb.bookChapterDao.insert(*chapters.toTypedArray())
        }.onSuccess {
            if (bookRequests.isCurrent(request)) success?.invoke()
        }
    }

    fun addToBookshelf(success: (() -> Unit)?) { //点击书架按钮或在加分组时触发
        val request = bookRequests.current
        val book = bookData.value ?: return
        val displayedChapters = currentChapterList(book)
        val requestBookUrl = book.bookUrl
        val source = request.bookSource
        execute {
            if (!bookRequests.isCurrent(request)) return@execute
            withRequestChapterPublication(request, requestBookUrl) {
                if (!bookRequests.isCurrent(request)) return@withRequestChapterPublication
                book.removeType(BookType.notShelf)
                if (book.order == 0) {
                    book.order = appDb.bookDao.minOrder - 1
                }
                appDb.bookDao.getBook(book.name, book.author)?.let {
                    book.durChapterIndex = it.durChapterIndex
                    book.durChapterPos = it.durChapterPos
                    book.durChapterTitle = it.durChapterTitle
                }
                if (ReadBook.book?.isSameNameAuthor(book) == true) {
                    ReadBook.book = book
                } else if (AudioPlay.book?.isSameNameAuthor(book) == true) {
                    AudioPlay.book = book
                }
                book.save()
                SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, source, book)
                request.loadedChapters.forBook(book.bookUrl, displayedChapters)?.let {
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                }
                request.inBookshelf = true
            }
        }.onSuccess {
            if (bookRequests.isCurrent(request)) success?.invoke()
        }
    }

    fun getBook(toastNull: Boolean = true): Book? {
        val book = bookData.value
        if (toastNull && book == null) {
            context.toastOnUi("book is null")
        }
        return book
    }

    fun delBook(deleteOriginal: Boolean = false, success: (() -> Unit)? = null) {
        val request = bookRequests.current
        val book = bookData.value ?: return
        execute {
            if (!bookRequests.isCurrent(request)) return@execute
            book.delete()
            request.inBookshelf = false
            if (book.isLocal) {
                LocalBook.deleteBook(book, deleteOriginal)
            }
        }.onSuccess {
            if (bookRequests.isCurrent(request)) success?.invoke()
        }
    }

    fun clearCache(book: Book) {
        execute {
            BookCacheManager.clear(book)
            if (ReadBook.book?.bookUrl == book.bookUrl) {
                ReadBook.clearTextChapter()
            }
            if (ReadManga.book?.bookUrl == book.bookUrl) {
                ReadManga.clearMangaChapter()
            }
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
        }.onError {
            context.toastOnUi("清理缓存出错\n${it.localizedMessage}")
        }
    }

    fun upEditBook() {
        val request = bookRequests.current
        bookData.value?.let {
            appDb.bookDao.getBook(it.bookUrl)?.let { book ->
                bookRequests.publish(request) { bookData.value = book }
            }
        }
    }

    private fun changeToLocalBook(localBook: Book, previousBook: Book, request: BookRequest): Book {
        return LocalBook.mergeBook(localBook, previousBook).let {
            request.inBookshelf = true
            bookRequests.publish(request) {
                if (bookData.value?.bookUrl != it.bookUrl) chapterListData.value = emptyList()
                bookData.value = it
            }
            loadChapterForRequest(it, true, request.scope, false, request)
            it
        }
    }

    fun onButtonClick(activity: AppCompatActivity, name: String, click: String) {
        val source = bookSource ?: return
        val book = bookData.value ?: return
        execute {
            val java = SourceLoginJsExtensions(activity, source)
            runScriptWithContext {
                source.evalJS(click) {
                    put("result", null)
                    put("java", java)
                    put("book", book)
                }
            }
        }.onError {
            AppLog.put("${source.bookSourceName}: ${it.localizedMessage}", it)
            context.toastOnUi("$name click error\n${it.localizedMessage}")
        }
    }

    private data class CachedOtherWorks(
        val items: List<SearchBook>,
        val isFresh: Boolean,
    )

    data class WebFile(
        val url: String,
        val name: String,
    ) {

        override fun toString(): String {
            return name
        }

        // 后缀
        val suffix: String = UrlUtil.getSuffix(name)

        // txt epub umd pdf等文件
        val isSupported: Boolean = AppPattern.bookFileRegex.matches(name)

        // 压缩包形式的txt epub umd pdf文件
        val isSupportDecompress: Boolean = AppPattern.archiveFileRegex.matches(name)

    }

    sealed class OtherWorksState {
        object Idle : OtherWorksState()
        object Loading : OtherWorksState()
        object Empty : OtherWorksState()
        data class Success(val books: List<SearchBook>) : OtherWorksState()
        data class Error(val message: String) : OtherWorksState()
    }

    private companion object {
        const val OTHER_WORKS_CACHE_TTL = 24 * 60 * 60 * 1000L
        const val OTHER_WORKS_REFRESH_DEBOUNCE = 700L
    }

}
