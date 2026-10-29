package io.legado.app.model

import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.help.AppWebDav
import io.legado.app.help.ConcurrentRateLimiter
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isSameNameAuthor
import io.legado.app.help.book.readSimulating
import io.legado.app.help.book.simulatedTotalChapterNum
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.globalExecutor
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.manga.entities.BaseMangaPage
import io.legado.app.ui.book.manga.entities.MangaChapter
import io.legado.app.ui.book.manga.entities.MangaContent
import io.legado.app.ui.book.manga.entities.MangaPage
import io.legado.app.ui.book.manga.entities.ReaderLoading
import io.legado.app.utils.mapIndexed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Semaphore
import kotlin.math.min

@Suppress("MemberVisibilityCanBePrivate")
object ReadManga : CoroutineScope by MainScope() {
    var inBookshelf = false
    var book: Book? = null
    val executor = globalExecutor
    var durChapterIndex = 0 //章节位置
    var chapterSize = 0//总章节
    var durChapterPos = 0
    var chapterChanged = false
    var prevMangaChapter: MangaChapter? = null
    var curMangaChapter: MangaChapter? = null
    var nextMangaChapter: MangaChapter? = null
    var bookSource: BookSource? = null
    private val loadingChapters = arrayListOf<Int>()
    private val loadEpoch = ReadBookLoadEpoch()
    @Volatile
    private var tocOrderPausedBookUrl: String? = null
    var simulatedChapterSize = 0
    var mCallback: Callback? = null
    var preDownloadTask: Job? = null
    val downloadedChapters = hashSetOf<Int>()
    val downloadFailChapters = hashMapOf<Int, Int>()
    val downloadScope = CoroutineScope(SupervisorJob() + IO)
    val preDownloadSemaphore = Semaphore(2)
    var rateLimiter = ConcurrentRateLimiter(null)
    val mangaContents get() = buildMangaContent()
    val hasNextChapter get() = durChapterIndex < simulatedChapterSize - 1

    fun resetData(book: Book) {
        synchronized(this) { loadEpoch.invalidate() }
        ReadingRecordTracker.bindBook(ReadingRecordKind.MANGA, book.bookUrl, book.name)
        ReadManga.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }
        durChapterIndex = book.durChapterIndex
        durChapterPos = book.durChapterPos
        clearMangaChapter()
        upWebBook(book)
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
    }

    fun upData(book: Book) {
        ReadingRecordTracker.bindBook(ReadingRecordKind.MANGA, book.bookUrl, book.name)
        ReadManga.book = book
        chapterSize = appDb.bookChapterDao.getChapterCount(book.bookUrl)
        simulatedChapterSize = if (book.readSimulating()) {
            book.simulatedTotalChapterNum()
        } else {
            chapterSize
        }

        if (durChapterIndex != book.durChapterIndex) {
            durChapterIndex = book.durChapterIndex
            durChapterPos = book.durChapterPos
            clearMangaChapter()
        }
        upWebBook(book)
        synchronized(this) {
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
        }
    }

    fun upWebBook(book: Book) {
        appDb.bookSourceDao.getBookSource(book.origin)?.let {
            bookSource = it
            rateLimiter = ConcurrentRateLimiter(it)
        } ?: let {
            bookSource = null
        }
    }

    fun clearMangaChapter() {
        prevMangaChapter = null
        curMangaChapter = null
        nextMangaChapter = null
    }

    /** 从外部 scope 在 Main 调用；旧请求同时用 generation 隔离迟到回调。 */
    internal suspend fun pauseForTocReorder(bookUrl: String): Boolean {
        val jobs = synchronized(this) {
            if (book?.bookUrl != bookUrl) return false
            tocOrderPausedBookUrl = bookUrl
            loadEpoch.invalidate()
            preDownloadTask?.cancel()
            preDownloadTask = null
            val pending = (coroutineContext[Job]?.children?.toList().orEmpty() +
                downloadScope.coroutineContext[Job]?.children?.toList().orEmpty()).distinct()
            pending.forEach { it.cancel() }
            clearMangaChapter()
            loadingChapters.clear()
            downloadedChapters.clear()
            downloadFailChapters.clear()
            pending
        }
        jobs.joinAll()
        val progressBarrier = CompletableDeferred<Unit>()
        executor.execute { progressBarrier.complete(Unit) }
        progressBarrier.await()
        return true
    }

    @Synchronized
    internal fun resumeAfterTocReorder(updatedBook: Book) {
        if (tocOrderPausedBookUrl != updatedBook.bookUrl) return
        tocOrderPausedBookUrl = null
        if (book?.bookUrl != updatedBook.bookUrl) return
        loadEpoch.invalidate()
        book = updatedBook
        chapterSize = updatedBook.totalChapterNum
        simulatedChapterSize = updatedBook.simulatedTotalChapterNum()
        durChapterIndex = updatedBook.durChapterIndex
        durChapterPos = updatedBook.durChapterPos
        clearMangaChapter()
        if (mCallback != null) loadContent()
    }

    @Synchronized
    private fun currentLoad(): Pair<Book, Long>? = book
        ?.takeUnless { it.bookUrl == tocOrderPausedBookUrl }
        ?.let { it to loadEpoch.current }

    @Synchronized
    private fun isLoadCurrent(bookUrl: String, generation: Long): Boolean =
        book?.bookUrl == bookUrl && tocOrderPausedBookUrl != bookUrl && loadEpoch.isCurrent(generation)

    //每次切换章节更新阅读记录
    fun upReadTime() {
        ReadingRecordTracker.checkpoint(ReadingRecordKind.MANGA)
    }

    @Synchronized
    private fun addLoading(index: Int, bookUrl: String, generation: Long): Boolean {
        if (!isLoadCurrent(bookUrl, generation)) return false
        if (loadingChapters.contains(index)) return false
        loadingChapters.add(index)
        return true
    }

    @Synchronized
    fun removeLoading(index: Int) {
        loadingChapters.remove(index)
    }

    fun loadContent() {
        if (currentLoad() == null) return
        clearMangaChapter()
        loadContent(durChapterIndex)
        loadContent(durChapterIndex + 1)
        loadContent(durChapterIndex - 1)
    }

    fun loadOrUpContent() {
        if (currentLoad() == null) return
        if (curMangaChapter == null) {
            loadContent(durChapterIndex)
        } else {
            mCallback?.upContent()
        }
        if (nextMangaChapter == null) {
            loadContent(durChapterIndex + 1)
        }
        if (prevMangaChapter == null) {
            loadContent(durChapterIndex - 1)
        }
    }

    private fun loadContent(index: Int) {
        val (book, generation) = currentLoad() ?: return
        Coroutine.async(this) {
            if (!isLoadCurrent(book.bookUrl, generation)) return@async
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: return@async
            if (addLoading(index, book.bookUrl, generation)) {
                BookHelp.getContent(book, chapter)?.let {
                    contentLoadFinish(chapter, it, generation = generation)
                } ?: run {
                    download(downloadScope, chapter, book, generation)
                }
            }
        }.onError {
            if (it is CancellationException) throw it
            if (isLoadCurrent(book.bookUrl, generation)) {
                AppLog.put("加载正文出错\n${it.localizedMessage}")
            }
        }
    }

    /**
     * 内容加载完成
     */
    suspend fun contentLoadFinish(
        chapter: BookChapter,
        content: String?,
        errorMsg: String = "加载内容失败",
        canceled: Boolean = false,
        generation: Long = loadEpoch.current,
    ) {
        synchronized(this) {
            if (!isLoadCurrent(chapter.bookUrl, generation)) return
            removeLoading(chapter.index)
            if (canceled || chapter.index !in durChapterIndex - 1..durChapterIndex + 1) return
            if (content == null || (content.isEmpty() && !chapter.isVolume)) {
                if (chapter.index == durChapterIndex) {
                    mCallback?.loadFail(if (content == null) errorMsg else "正文内容为空")
                }
                return
            }
        }
        val mangaChapter = getManageChapter(chapter, content!!)
        synchronized(this) {
            if (!isLoadCurrent(chapter.bookUrl, generation)) return
            if (mangaChapter.imageCount == 0 && !chapter.isVolume) {
                if (chapter.index == durChapterIndex) mCallback?.loadFail("正文没有图片")
                return
            }
            when (chapter.index - durChapterIndex) {
                0 -> curMangaChapter = mangaChapter
                -1 -> prevMangaChapter = mangaChapter
                1 -> nextMangaChapter = mangaChapter
                else -> return
            }
            mCallback?.upContent()
        }
    }

    private fun buildMangaContent(): MangaContent {
        val items = arrayListOf<BaseMangaPage>()
        var pos = 0
        var curFinish = false
        var nextFinish = false
        prevMangaChapter?.let {
            pos += it.pages.size
            items.addAll(it.pages)
        }
        curMangaChapter?.let {
            curFinish = true
            items.addAll(it.pages)
            durChapterPos = if (it.imageCount > 0) {
                durChapterPos.coerceIn(0, it.imageCount - 1)
            } else {
                0
            }
            pos += durChapterPos
            if (!AppConfig.hideMangaTitle && it.imageCount > 0) {
                pos++
            }
        }
        nextMangaChapter?.let {
            nextFinish = true
            items.addAll(it.pages)
        }
        return MangaContent(pos, items, curFinish, nextFinish)
    }

    /**
     * 加载下一章
     */
    fun moveToNextChapter(toFirst: Boolean = false): Boolean {
        if (currentLoad() == null) return false
        if (durChapterIndex < simulatedChapterSize - 1) {
            if (toFirst) {
                mCallback?.showLoading()
                durChapterPos = 0
            }
            durChapterIndex++
            prevMangaChapter = curMangaChapter
            curMangaChapter = nextMangaChapter
            nextMangaChapter = null
            if (curMangaChapter == null) {
                mCallback?.startLoad()
                loadContent(durChapterIndex)
            } else {
                mCallback?.upContent()
            }
            loadContent(durChapterIndex + 1)
            saveRead()
            AppLog.putDebug("moveToNextChapter-curPageChanged()")
            curPageChanged()
            return true
        } else {
            AppLog.putDebug("跳转下一章失败,没有下一章")
            return false
        }
    }

    fun moveToPrevChapter(toFirst: Boolean = false): Boolean {
        if (currentLoad() == null) return false
        if (durChapterIndex > 0) {
            if (toFirst) {
                mCallback?.showLoading()
                durChapterPos = 0
            }
            durChapterIndex--
            nextMangaChapter = curMangaChapter
            curMangaChapter = prevMangaChapter
            prevMangaChapter = null
            if (curMangaChapter == null) {
                loadContent(durChapterIndex)
            } else {
                mCallback?.upContent()
            }
            loadContent(durChapterIndex - 1)
            saveRead()
            return true
        }
        return false
    }

    fun curPageChanged() {
        upReadTime()
        preDownload()
    }

    @Synchronized
    fun saveRead(pageChanged: Boolean = false) {
        if (book?.bookUrl == tocOrderPausedBookUrl) return
        executor.execute {
            kotlin.runCatching {
                val book = book ?: return@execute
                if (tocOrderPausedBookUrl == book.bookUrl) return@execute
                book.lastCheckCount = 0
                book.durChapterTime = System.currentTimeMillis()
                val chapterChanged = book.durChapterIndex != durChapterIndex
                book.durChapterIndex = durChapterIndex
                book.durChapterPos = durChapterPos
                if (!pageChanged || chapterChanged) {
                    appDb.bookChapterDao.getChapter(book.bookUrl, durChapterIndex)?.let {
                        book.durChapterTitle = it.getDisplayTitle(
                            ContentProcessor.get(book.name, book.origin).getTitleReplaceRules(),
                            book.getUseReplaceRule(),
                            replaceBook = book.toReplaceBook()
                        )
                    }
                }
                appDb.bookDao.update(book)
            }.onFailure {
                AppLog.put("保存漫画阅读进度信息出错\n$it", it)
            }
        }
    }

    private fun downloadNetworkContent(
        bookSource: BookSource,
        scope: CoroutineScope,
        chapter: BookChapter,
        book: Book,
        semaphore: Semaphore?,
        success: suspend (String) -> Unit = {},
        error: suspend () -> Unit = {},
        cancel: suspend () -> Unit = {},
    ) {
        WebBook.getContent(
            scope,
            bookSource,
            book,
            chapter,
            start = CoroutineStart.LAZY,
            executeContext = IO,
            semaphore = semaphore
        ).onSuccess { content ->
            success.invoke(content)
        }.onError {
            if (it is CancellationException) throw it
            error.invoke()
        }.onCancel {
            cancel.invoke()
        }.start()
    }

    private fun preDownload() {
        val (requestBook, generation) = currentLoad() ?: return
        if (requestBook.isLocal) return
        executor.execute {
            if (!isLoadCurrent(requestBook.bookUrl, generation)) return@execute
            if (AppConfig.preDownloadNum < 2) {
                upToc()
                return@execute
            }
            preDownloadTask?.cancel()
            preDownloadTask = launch(IO) {
                //预下载
                launch {
                    val maxChapterIndex =
                        min(durChapterIndex + AppConfig.preDownloadNum, chapterSize)
                    for (i in durChapterIndex.plus(2)..maxChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= 3) continue
                        downloadIndex(i, requestBook, generation)
                    }
                }
                launch {
                    val minChapterIndex = durChapterIndex - min(5, AppConfig.preDownloadNum)
                    for (i in durChapterIndex.minus(2) downTo minChapterIndex) {
                        if (downloadedChapters.contains(i)) continue
                        if ((downloadFailChapters[i] ?: 0) >= 3) continue
                        downloadIndex(i, requestBook, generation)
                    }
                }
            }
        }
    }

    fun cancelPreDownloadTask() {
        if (curMangaChapter != null && nextMangaChapter != null) {
            preDownloadTask?.cancel()
            downloadScope.coroutineContext.cancelChildren()
        }
    }

    private suspend fun downloadIndex(index: Int, book: Book, generation: Long) {
        if (!isLoadCurrent(book.bookUrl, generation)) return
        if (index < 0) return
        if (index > chapterSize - 1) {
            upToc()
            return
        }
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index) ?: return
        if (BookHelp.hasContent(book, chapter)) {
            synchronized(this) {
                if (isLoadCurrent(book.bookUrl, generation)) downloadedChapters.add(chapter.index)
            }
        } else {
            delay(1000)
            if (addLoading(index, book.bookUrl, generation)) {
                download(downloadScope, chapter, book, generation, preDownloadSemaphore)
            }
        }
    }

    /**
     * 获取正文
     */
    private suspend fun download(
        scope: CoroutineScope,
        chapter: BookChapter,
        book: Book,
        generation: Long,
        semaphore: Semaphore? = null,
    ) {
        if (!isLoadCurrent(book.bookUrl, generation)) return
        val bookSource = bookSource
        if (bookSource != null) {
            downloadNetworkContent(bookSource, scope, chapter, book, semaphore, success = {
                synchronized(this) {
                    if (isLoadCurrent(book.bookUrl, generation)) {
                        downloadedChapters.add(chapter.index)
                        downloadFailChapters.remove(chapter.index)
                    }
                }
                contentLoadFinish(chapter, it, generation = generation)
            }, error = {
                synchronized(this) {
                    if (isLoadCurrent(book.bookUrl, generation)) {
                        downloadFailChapters[chapter.index] =
                            (downloadFailChapters[chapter.index] ?: 0) + 1
                    }
                }
                contentLoadFinish(chapter, null, generation = generation)
            }, cancel = {
                contentLoadFinish(chapter, null, canceled = true, generation = generation)
            })
        } else {
            contentLoadFinish(chapter, null, "加载内容失败 没有书源", generation = generation)
        }
    }

    @Synchronized
    fun upToc() {
        val bookSource = bookSource ?: return
        val (book, generation) = currentLoad() ?: return
        if (!book.canUpdate) return
        if (chapterSize - durChapterIndex - 1 >= 3) return
        if (System.currentTimeMillis() - book.lastCheckTime < 600000) return
        book.lastCheckTime = System.currentTimeMillis()
        val oldBook = book.copy()
        WebBook.getChapterList(this, bookSource, book).onSuccess(IO) { cList ->
            ensureActive()
            if (!isLoadCurrent(book.bookUrl, generation)) return@onSuccess
            if (cList.size > chapterSize) {
                if (oldBook.bookUrl == book.bookUrl) {
                    appDb.bookDao.update(book)
                } else {
                    appDb.bookDao.replace(oldBook, book)
                    BookHelp.updateCacheFolder(oldBook, book)
                }
                appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                appDb.bookChapterDao.insert(*cList.toTypedArray())
                onChapterListUpdated(book, false)
                nextMangaChapter ?: loadContent(durChapterIndex + 1)
            }
        }
    }

    fun uploadProgress(successAction: (() -> Unit)? = null) {
        book?.let {
            launch(IO) {
                AppWebDav.uploadBookProgress(it) {
                    successAction?.invoke()
                }
                ensureActive()
                it.update()
            }
        }
    }

    /**
     * 同步阅读进度
     * 如果当前进度快于服务器进度或者没有进度进行上传，如果慢与服务器进度则执行传入动作
     */
    fun syncProgress(
        newProgressAction: ((progress: BookProgress) -> Unit)? = null,
        uploadSuccessAction: (() -> Unit)? = null,
        syncSuccessAction: (() -> Unit)? = null,
    ) {
        if (!AppConfig.syncBookProgress) return
        val book = book ?: return
        Coroutine.async {
            AppWebDav.getBookProgress(book)
        }.onError {
            AppLog.put("拉取阅读进度失败", it)
        }.onSuccess { progress ->
            if (progress == null || progress.durChapterIndex < book.durChapterIndex ||
                (progress.durChapterIndex == book.durChapterIndex
                        && progress.durChapterPos < book.durChapterPos)
            ) {
                // 服务器没有进度或者进度比服务器快，上传现有进度
                Coroutine.async {
                    AppWebDav.uploadBookProgress(BookProgress(book), uploadSuccessAction)
                    book.update()
                }
            } else if (progress.durChapterIndex > book.durChapterIndex ||
                progress.durChapterPos > book.durChapterPos
            ) {
                // 进度比服务器慢，执行传入动作
                newProgressAction?.invoke(progress)
            } else {
                syncSuccessAction?.invoke()
            }
        }
    }

    fun setProgress(progress: BookProgress) {
        if (currentLoad() == null) return
        if (progress.durChapterIndex < chapterSize &&
            (durChapterIndex != progress.durChapterIndex
                    || durChapterPos != progress.durChapterPos)
        ) {
            mCallback?.showLoading()
            if (progress.durChapterIndex == durChapterIndex) {
                durChapterPos = progress.durChapterPos
                mCallback?.upContent()
            } else {
                durChapterIndex = progress.durChapterIndex
                durChapterPos = progress.durChapterPos
                loadContent()
            }
            saveRead()
        }
    }

    fun showLoading() {
        mCallback?.showLoading()
    }

    fun loadFail(msg: String, retry: Boolean = true) {
        mCallback?.loadFail(msg, retry)
    }

    fun onChapterListUpdated(newBook: Book, loadContent: Boolean = true) {
        if (newBook.isSameNameAuthor(book)) {
            book = newBook
            chapterSize = newBook.totalChapterNum
            simulatedChapterSize = newBook.simulatedTotalChapterNum()
            if (simulatedChapterSize > 0 && durChapterIndex > simulatedChapterSize - 1) {
                durChapterIndex = simulatedChapterSize - 1
            }
            if (mCallback == null) {
                clearMangaChapter()
            } else if (loadContent) {
                loadContent()
            }
        }
    }

    /**
     * 注册回调
     */
    fun register(cb: Callback) {
        mCallback = cb
    }

    /**
     * 取消注册回调
     */
    fun unregister(cb: Callback) {
        if (mCallback === cb) {
            mCallback = null
        }
        preDownloadTask?.cancel()
        preDownloadTask = null
        downloadScope.coroutineContext.cancelChildren()
        coroutineContext.cancelChildren()
    }

    private suspend fun getManageChapter(chapter: BookChapter, content: String): MangaChapter {
        val list = BookHelp.flowImages(chapter, content)
            .distinctUntilChanged().mapIndexed { index, src ->
                MangaPage(
                    chapterIndex = chapter.index,
                    chapterSize = chapterSize,
                    mImageUrl = src,
                    index = index,
                    mChapterName = chapter.title
                )
            }.toList()

        val imageCount = list.size

        list.forEach {
            it.imageCount = imageCount
        }

        if (AppConfig.hideMangaTitle && imageCount > 0) {
            return MangaChapter(chapter, list, imageCount)
        }

        val pages = mutableListOf<BaseMangaPage>()

        if (imageCount == 0 && chapter.isVolume) {
            pages.add(ReaderLoading(chapter.index, -1, chapter.title, true))
        } else {
            pages.add(ReaderLoading(chapter.index, -1, "阅读 ${chapter.title}"))
            pages.addAll(list)
            pages.add(ReaderLoading(chapter.index, imageCount, "已读完 ${chapter.title}"))
        }

        return MangaChapter(chapter, pages, imageCount)
    }

    interface Callback {
        fun upContent()
        fun loadFail(msg: String, retry: Boolean = true)
        fun sureNewProgress(progress: BookProgress)
        fun showLoading()
        fun startLoad()
    }
}
