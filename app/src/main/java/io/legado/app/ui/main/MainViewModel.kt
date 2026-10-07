package io.legado.app.ui.main

import android.app.Application
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isNotShelf
import io.legado.app.help.book.isUpError
import io.legado.app.help.book.removeType
import io.legado.app.help.book.sync
import io.legado.app.help.config.AppConfig
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.CacheBookService
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.putPrefLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.collections.forEach
import kotlin.math.min
import io.legado.app.model.RuleUpdate
import io.legado.app.model.SourceCallBack

class MainViewModel(application: Application) : BaseViewModel(application) {
    private var threadCount = AppConfig.threadCount
    private var poolSize = min(threadCount, AppConst.MAX_THREAD)
    private var upTocPool = Executors.newFixedThreadPool(poolSize).asCoroutineDispatcher()
    private val tocQueueLock = Any()
    private val waitUpTocBooks = ConcurrentLinkedQueue<String>()
    // 入队即占用，涵盖 poll 到并行 action 开始之间的窗口。
    private val reservedUpTocBooks = mutableSetOf<String>()
    private val onUpTocBooks = ConcurrentHashMap.newKeySet<String>()
    private val eventListenerSource = ConcurrentHashMap<BookSource, Boolean>()
    val onUpBooksLiveData = MutableLiveData<Int>()
    private val tocRefreshing = MutableLiveData(false)
    val isTocRefreshing: LiveData<Boolean> = tocRefreshing
    private var upTocJob: Job? = null
    private var cacheBookJob: Job? = null
    private var cleared = false
    private val bookNewsTracker = BookNewsRefreshTracker(
        context.getPrefLong(BookNewsRefreshPolicy.LAST_CHECKED_AT_PREF).takeIf { it > 0L }
    )
    private val mutableBookNewsRefreshState = MutableStateFlow(bookNewsTracker.state)
    internal val bookNewsRefreshState: StateFlow<BookNewsRefreshState> =
        mutableBookNewsRefreshState.asStateFlow()
    var callback: CallBack? = null
    fun setActivityCallback(callback: CallBack) {
        this.callback = callback
    }

    init {
        deleteNotShelfBook()
    }

    override fun onCleared() {
        synchronized(tocQueueLock) {
            cleared = true
            waitUpTocBooks.clear()
            reservedUpTocBooks.clear()
            onUpTocBooks.clear()
            bookNewsTracker.cancel()
            publishBookNewsState()
            tocRefreshing.postValue(false)
            postUpBooksLiveData()
        }
        upTocJob?.cancel()
        cacheBookJob?.cancel()
        super.onCleared()
        upTocPool.close()
    }

    fun upPool() = synchronized(tocQueueLock) {
        if (cleared) return@synchronized
        threadCount = AppConfig.threadCount
        if (upTocJob?.isActive == true || cacheBookJob?.isActive == true) {
            return@synchronized
        }
        val newPoolSize = min(threadCount, AppConst.MAX_THREAD)
        if (poolSize == newPoolSize) {
            return@synchronized
        }
        poolSize = newPoolSize
        upTocPool.close()
        upTocPool = Executors.newFixedThreadPool(poolSize).asCoroutineDispatcher()
    }

    fun isUpdate(bookUrl: String): Boolean {
        return onUpTocBooks.contains(bookUrl)
    }

    fun upAllBookToc() {
        execute {
            addToWaitUp(
                appDb.bookDao.hasUpdateBooks,
                AppConfig.onlyUpdateRead,
                skipBookNewsSuccess = true
            )
        }
    }

    internal fun refreshBookNews(force: Boolean = false) {
        synchronized(tocQueueLock) {
            if (cleared || !bookNewsTracker.tryStart(
                    System.currentTimeMillis(), SystemClock.elapsedRealtime(), force
                )
            ) return
            publishBookNewsState()
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val books = appDb.bookDao.hasUpdateBooks.filter {
                    BookNewsRefreshPolicy.isEligible(it.isLocal, it.isNotShelf, it.canUpdate)
                }
                currentCoroutineContext().ensureActive()
                synchronized(tocQueueLock) {
                    if (cleared) return@synchronized
                    bookNewsTracker.setTargets(
                        books.map { BookNewsCheckTarget(it.bookUrl, it.origin) },
                        System.currentTimeMillis(),
                        reuseSuccessful = !force
                    )
                    publishBookNewsState()
                    enqueueBooks(
                        books.filter { bookNewsTracker.needsCheck(BookNewsCheckTarget(it.bookUrl, it.origin)) },
                        onlyUpdateRead = false, skipBookNewsSuccess = false
                    )
                }
            } catch (e: Throwable) {
                synchronized(tocQueueLock) {
                    bookNewsTracker.cancel()
                    publishBookNewsState()
                }
                if (e is CancellationException) throw e
                AppLog.put("首页书讯检查失败\n${e.localizedMessage}", e)
            }
        }
    }

    /** 调用方持有 tocQueueLock，仅本轮实际检查结束时更新持久化时间。 */
    private fun publishBookNewsState() {
        val previous = mutableBookNewsRefreshState.value
        val next = bookNewsTracker.state
        if (next.lastCheckedAt != previous.lastCheckedAt && next.lastCheckedAt != null) {
            context.putPrefLong(BookNewsRefreshPolicy.LAST_CHECKED_AT_PREF, next.lastCheckedAt)
        }
        mutableBookNewsRefreshState.value = next
    }

    fun ruleSubsUp() {
        execute {
            val ruleSubs = appDb.ruleSubDao.all
            for (ruleSub in ruleSubs) {
                if (ruleSub.autoUpdate) {
                    val checkResult = RuleUpdate.cacheSource(ruleSub)
                    if(checkResult) {
                        callback?.openImportUi(ruleSub.type, ruleSub.url)
                    }
                }
            }
        }
    }

    fun upToc(books: List<Book>, onlyUpdateRead: Boolean) {
        execute(context = upTocPool) {
            books.filter {
                !it.isLocal && it.canUpdate
            }.let {
                addToWaitUp(it, onlyUpdateRead)
            }
        }
    }

    private fun addToWaitUp(
        books: List<Book>,
        onlyUpdateRead: Boolean,
        skipBookNewsSuccess: Boolean = false
    ) = synchronized(tocQueueLock) {
        enqueueBooks(books, onlyUpdateRead, skipBookNewsSuccess)
    }

    private fun enqueueBooks(
        books: List<Book>,
        onlyUpdateRead: Boolean,
        skipBookNewsSuccess: Boolean
    ) {
        if (cleared) return
        books.forEach { book ->
            if (onlyUpdateRead && book.getUnreadChapterNum() > 0) return@forEach
            if (skipBookNewsSuccess && bookNewsTracker.wasSuccessfullyChecked(
                    BookNewsCheckTarget(book.bookUrl, book.origin), System.currentTimeMillis()
                )
            ) return@forEach
            if (reservedUpTocBooks.add(book.bookUrl)) {
                waitUpTocBooks.add(book.bookUrl)
            }
        }
        if (upTocJob == null && waitUpTocBooks.isNotEmpty()) {
            startUpTocJob()
        }
    }

    private fun startUpTocJob() {
        upPool()
        tocRefreshing.postValue(waitUpTocBooks.isNotEmpty())
        postUpBooksLiveData()
        val job = viewModelScope.launch(upTocPool, start = CoroutineStart.LAZY) {
            flow<String> {
                while (true) {
                    val bookUrl = synchronized(tocQueueLock) { waitUpTocBooks.poll() } ?: break
                    emit(bookUrl)
                }
            }.onEachParallel(threadCount) {
                var success: Boolean? = null
                var checkedOrigin: String? = null
                try {
                    onUpTocBooks.add(it)
                    postEvent(EventBus.UP_BOOKSHELF, it)
                    val book = appDb.bookDao.getBook(it)
                    checkedOrigin = book?.origin
                    success = updateToc(it, book)
                    currentCoroutineContext().ensureActive()
                } finally {
                    val operationContext = currentCoroutineContext()
                    val result = success
                    synchronized(tocQueueLock) {
                        onUpTocBooks.remove(it)
                        reservedUpTocBooks.remove(it)
                        if (operationContext.isActive && !cleared && result != null) {
                            bookNewsTracker.complete(it, checkedOrigin, result,
                                System.currentTimeMillis())
                        } else {
                            // A cancelled or interrupted batch must not invent a check time.
                            bookNewsTracker.cancel()
                        }
                        publishBookNewsState()
                    }
                    postEvent(EventBus.UP_BOOKSHELF, it)
                    postUpBooksLiveData()
                }
            }.onCompletion { cause ->
                synchronized(tocQueueLock) {
                    upTocJob = null
                    if (cause != null || cleared) {
                        waitUpTocBooks.clear()
                        reservedUpTocBooks.clear()
                        onUpTocBooks.clear()
                        bookNewsTracker.cancel()
                        publishBookNewsState()
                        tocRefreshing.postValue(false)
                    } else if (waitUpTocBooks.isNotEmpty()) {
                        startUpTocJob()
                    } else {
                        tocRefreshing.postValue(false)
                        if (cacheBookJob == null && !CacheBookService.isRun) {
                            // 这里只分派回调和缓存任务，不执行同步网络；与下一次入队原子衔接。
                            cacheBook()
                        }
                    }
                }
                postUpBooksLiveData()
            }.catch {
                AppLog.put("更新目录出错\n${it.localizedMessage}", it)
            }.collect()
        }
        upTocJob = job
        job.start()
    }

    private suspend fun updateToc(bookUrl: String, book: Book?): Boolean {
        book ?: return false
        if (!BookNewsRefreshPolicy.isEligible(book.isLocal, book.isNotShelf, book.canUpdate)) {
            return false
        }
        val source = appDb.bookSourceDao.getBookSource(book.origin)
        if (source == null) {
            AppLog.put("${book.name} 更新目录失败\n书源不存在")
            markUpdateError(bookUrl, book.origin)
            return false
        }
        if (source.eventListener) {
            // 使用 putIfAbsent 确保只添加一次
            if (eventListenerSource.putIfAbsent(source, true) == null) {
                // 通知监听事件的书源，书架刷新开始
                SourceCallBack.callBackSource(viewModelScope, SourceCallBack.START_SHELF_REFRESH, source)
            }
        }
        return kotlin.runCatching {
            val oldBook = book.copy()
            if (book.tocUrl.isBlank()) {
                WebBook.getBookInfoAwait(source, book)
            } else {
                WebBook.runPreUpdateJs(source, book)
            }
            val toc = WebBook.getChapterListAwait(source, book).getOrThrow()
            book.removeType(BookType.updateError)
            val committed = appDb.withTransaction {
                val current = appDb.bookDao.getBook(bookUrl)
                if (current == null || current.origin != oldBook.origin ||
                    current.isNotShelf != oldBook.isNotShelf || current.isLocal != oldBook.isLocal
                ) return@withTransaction false
                book.sync(oldBook)
                if (book.bookUrl == bookUrl) appDb.bookDao.update(book)
                else appDb.bookDao.replace(oldBook, book)
                appDb.bookChapterDao.delByBook(bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                true
            }
            if (!committed) return@runCatching false
            if (book.bookUrl != bookUrl) {
                BookHelp.updateCacheFolder(oldBook, book)
            }
            ReadBook.onChapterListUpdated(book)
            addDownload(source, book)
            true
        }.onFailure {
            currentCoroutineContext().ensureActive()
            AppLog.put("${book.name} 更新目录失败\n${it.localizedMessage}", it)
            //这里可能因为时间太长书籍信息已经更改,所以重新获取
            markUpdateError(book.bookUrl, source.bookSourceUrl)
        }.getOrDefault(false)
    }

    private suspend fun markUpdateError(bookUrl: String, origin: String) {
        appDb.withTransaction {
            appDb.bookDao.getBook(bookUrl)?.takeIf {
                it.origin == origin && !it.isLocal && !it.isNotShelf && !it.isUpError
            }?.let {
                it.addType(BookType.updateError)
                appDb.bookDao.update(it)
            }
        }
    }

    fun postUpBooksLiveData(reset: Boolean = false) {
        if (AppConfig.showWaitUpCount) {
            onUpBooksLiveData.postValue(synchronized(tocQueueLock) { reservedUpTocBooks.size })
        } else if (reset) {
            onUpBooksLiveData.postValue(0)
        }
    }

    @Synchronized
    private fun addDownload(source: BookSource, book: Book) {
        if (AppConfig.preDownloadNum == 0) return
        val endIndex = min(
            book.totalChapterNum - 1,
            book.durChapterIndex.plus(AppConfig.preDownloadNum)
        )
        val cacheBook = CacheBook.getOrCreate(source, book)
        cacheBook.addDownload(book.durChapterIndex, endIndex)
    }

    /**
     * 缓存书籍
     */
    private fun cacheBook() {
        //开始缓存前，通知监听事件的书源，书架刷新已完成
        eventListenerSource.toList().forEach {
            SourceCallBack.callBackSource(viewModelScope, SourceCallBack.END_SHELF_REFRESH, it.first)
        }
        eventListenerSource.clear()
        if (AppConfig.preDownloadNum == 0) return
        cacheBookJob?.cancel()
        cacheBookJob = viewModelScope.launch(upTocPool) {
            launch {
                while (isActive && CacheBook.isRun) {
                    val isOnUpTocBooksEmpty = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        onUpTocBooks.isEmpty()
                    } else {
                        var isEmpty = true
                        onUpTocBooks.forEach { _ ->
                            isEmpty = false
                            return@forEach
                        }
                        isEmpty
                    }
                    //有目录更新是不缓存,优先更新目录,现在更多网站限制并发
                    CacheBook.setWorkingState(waitUpTocBooks.isEmpty() && isOnUpTocBooksEmpty)
                    delay(1000)
                }
            }
            CacheBook.startProcessJob(upTocPool)
        }
    }

    fun restoreWebDav(name: String) {
        execute {
            AppWebDav.restoreWebDav(name)
        }
    }

    private fun deleteNotShelfBook() {
        execute {
            appDb.bookDao.deleteNotShelfBook()
        }
    }

    interface CallBack {
        fun openImportUi(type: Int, source: String)
    }

}
