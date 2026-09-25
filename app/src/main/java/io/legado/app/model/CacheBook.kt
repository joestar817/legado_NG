package io.legado.app.model

import android.content.Context
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.exception.ConcurrentException
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.coroutine.CompositeCoroutine
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.model.webBook.WebBook
import io.legado.app.service.CacheBookService
import io.legado.app.utils.onEachParallel
import io.legado.app.utils.postEvent
import io.legado.app.utils.startService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

object CacheBook {

    val cacheBookMap = ConcurrentHashMap<String, CacheBookModel>()

    private val workingState = MutableStateFlow(true)
    private val mutex = Mutex()

    @Synchronized
    fun getOrCreate(bookUrl: String): CacheBookModel? {
        val book = appDb.bookDao.getBook(bookUrl) ?: return null
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin) ?: return null
        updateBookSource(bookSource)
        var cacheBook = cacheBookMap[bookUrl]
        if (cacheBook != null) {
            //存在时更新,书源可能会变化,必须更新
            cacheBook.bookSource = bookSource
            cacheBook.book = book
            return cacheBook
        }
        cacheBook = CacheBookModel(bookSource, book)
        cacheBookMap[bookUrl] = cacheBook
        return cacheBook
    }

    @Synchronized
    fun getOrCreate(bookSource: BookSource, book: Book): CacheBookModel {
        updateBookSource(bookSource)
        var cacheBook = cacheBookMap[book.bookUrl]
        if (cacheBook != null) {
            //存在时更新,书源可能会变化,必须更新
            cacheBook.bookSource = bookSource
            cacheBook.book = book
            return cacheBook
        }
        cacheBook = CacheBookModel(bookSource, book)
        cacheBookMap[book.bookUrl] = cacheBook
        return cacheBook
    }

    private fun updateBookSource(newBookSource: BookSource) {
        cacheBookMap.forEach {
            val model = it.value
            if (model.bookSource.bookSourceUrl == newBookSource.bookSourceUrl) {
                model.bookSource = newBookSource
            }
        }
    }

    fun start(context: Context, book: Book, start: Int, end: Int) {
        if (!book.isLocal) {
            context.startService<CacheBookService> {
                action = IntentAction.start
                putExtra("bookUrl", book.bookUrl)
                putExtra("start", start)
                putExtra("end", end)
            }
        }
    }

    fun remove(context: Context, bookUrl: String) {
        context.startService<CacheBookService> {
            action = IntentAction.remove
            putExtra("bookUrl", bookUrl)
        }
    }

    fun cancel(bookUrl: String) {
        cacheBookMap[bookUrl]?.stop()
    }

    fun stop(context: Context) {
        if (CacheBookService.isRun) {
            context.startService<CacheBookService> {
                action = IntentAction.stop
            }
        }
    }

    fun close() {
        cacheBookMap.forEach { it.value.stop() }
        cacheBookMap.clear()
        successDownloadSet.clear()
        errorDownloadMap.clear()
    }

    fun setWorkingState(value: Boolean) {
        workingState.value = value
    }

    suspend fun startProcessJob(context: CoroutineContext) = mutex.withLock {
        setWorkingState(true)
        flow {
            while (currentCoroutineContext().isActive && cacheBookMap.isNotEmpty()) {
                var emitted = false

                cacheBookMap.forEach { (_, model) ->
                    if (!model.isLoading() && model.hasWaitingDownload()) {
                        emit(model)
                        emitted = true
                    }
                    workingState.first { it }
                }

                if (!emitted) {
                    delay(1000)
                }
            }
        }.onStart {
            postEvent(EventBus.UP_DOWNLOAD_STATE, "")
        }.onEachParallel(AppConfig.threadCount) {
            coroutineScope {
                it.download(this, context)
            }
        }.onCompletion {
            postEvent(EventBus.UP_DOWNLOAD_STATE, "")
        }.collect()
    }


    val downloadSummary: String
        get() {
            return "正在下载:${onDownloadCount}|等待中:${waitCount}|失败:${errorDownloadMap.count()}|成功:${successDownloadSet.size}"
        }

    val isRun: Boolean
        get() {
            cacheBookMap.forEach {
                if (it.value.isRun()) {
                    return true
                }
            }
            return false
        }

    private val waitCount: Int
        get() {
            var count = 0
            cacheBookMap.forEach {
                count += it.value.waitCount
            }
            return count
        }

    val onDownloadCount: Int
        get() {
            var count = 0
            cacheBookMap.forEach {
                count += it.value.onDownloadCount
            }
            return count
        }

    val successDownloadSet = linkedSetOf<String>()
    val errorDownloadMap = hashMapOf<String, Int>()

    class CacheBookModel(var bookSource: BookSource, var book: Book) {

        private val waitDownloadSet = linkedSetOf<Int>()
        private val onDownloadSet = linkedSetOf<Int>()
        private val downloadGenerations = hashMapOf<Int, Long?>()
        private val downloadCompletions = hashMapOf<Int, CompletableDeferred<Unit>>()
        private val tasks = CompositeCoroutine()
        private var isStopped = false
        private var waitingRetry = false
        private var isLoading = false

        val waitCount get() = waitDownloadSet.size
        val onDownloadCount get() = onDownloadSet.size

        init {
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        @Synchronized
        fun isRun(): Boolean {
            return waitDownloadSet.isNotEmpty() || onDownloadSet.isNotEmpty() || isLoading
        }

        @Synchronized
        fun isStop(): Boolean {
            return isStopped || (!isRun() && !waitingRetry)
        }

        @Synchronized
        fun isLoading(): Boolean {
            return isLoading
        }

        @Synchronized
        fun hasWaitingDownload(): Boolean {
            return waitDownloadSet.isNotEmpty()
        }

        @Synchronized
        fun setLoading() {
            isLoading = true
        }

        @Synchronized
        fun stop() {
            waitDownloadSet.clear()
            tasks.clear()
            isStopped = true
            isLoading = false
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        @Synchronized
        fun addDownload(start: Int, end: Int) {
            isStopped = false
            for (i in start..end) {
                if (!onDownloadSet.contains(i)) {
                    waitDownloadSet.add(i)
                }
            }
            cacheBookMap[book.bookUrl] = this
            isLoading = false
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        // Called under the model lock. Waiters only resume on their own scope and
        // never call ReadBook while holding this lock.
        private fun finishDownload(index: Int) {
            onDownloadSet.remove(index)
            downloadGenerations.remove(index)
            downloadCompletions.remove(index)?.complete(Unit)
        }

        @Synchronized
        private fun onSuccess(chapter: BookChapter) {
            finishDownload(chapter.index)
            successDownloadSet.add(chapter.primaryStr())
            errorDownloadMap.remove(chapter.primaryStr())
        }

        @Synchronized
        private fun onPreError(chapter: BookChapter, error: Throwable) {
            waitingRetry = true
            if (error !is ConcurrentException) {
                errorDownloadMap[chapter.primaryStr()] =
                    (errorDownloadMap[chapter.primaryStr()] ?: 0) + 1
            }
            finishDownload(chapter.index)
        }

        @Synchronized
        private fun onPostError(chapter: BookChapter, error: Throwable) {
            //重试3次
            if ((errorDownloadMap[chapter.primaryStr()] ?: 0) < 3 && !isStopped) {
                waitDownloadSet.add(chapter.index)
            } else {
                AppLog.put(
                    "下载${book.name}-${chapter.title}失败\n${error.localizedMessage}",
                    error
                )
            }
            waitingRetry = false
        }

        @Synchronized
        private fun onError(chapter: BookChapter, error: Throwable) {
            onPreError(chapter, error)
            onPostError(chapter, error)
        }

        @Synchronized
        private fun onCancel(index: Int) {
            finishDownload(index)
            if (!isStopped) waitDownloadSet.add(index)
        }

        @Synchronized
        private fun onFinally() {
            if (waitDownloadSet.isEmpty() && onDownloadSet.isEmpty()) {
                cacheBookMap.remove(book.bookUrl)
            }
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        /**
         * 从待下载列表内取第一条下载
         */
        fun download(scope: CoroutineScope, context: CoroutineContext) {
            val generation = ReadBook.captureLoadGeneration(book.bookUrl)
            downloadNext(scope, context, generation)
        }

        @Synchronized
        private fun downloadNext(scope: CoroutineScope, context: CoroutineContext, generation: Long?) {
            val chapterIndex = waitDownloadSet.firstOrNull()
            if (chapterIndex == null) {
                if (!isLoading && onDownloadSet.isEmpty()) {
                    cacheBookMap.remove(book.bookUrl)
                }
                return
            }
            if (onDownloadSet.contains(chapterIndex)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: let {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (chapter.isVolume) {
                /** 修正下载计数 */
                postEvent(EventBus.SAVE_CONTENT, Pair(book, chapter))
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (book.isAudio) {
                downloadAudio(scope, context, chapter)
                return
            }
            if (BookHelp.hasImageContent(book, chapter)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            waitDownloadSet.remove(chapterIndex)
            onDownloadSet.add(chapterIndex)
            if (BookHelp.hasContent(book, chapter)) {
                Coroutine.async(scope, context, executeContext = context) {
                    BookHelp.getContent(book, chapter)?.let {
                        BookHelp.saveImages(bookSource, book, chapter, it, 1)
                    }
                }.onSuccess {
                    onSuccess(chapter)
                }.onError {
                    onPreError(chapter, it)
                    //出现错误等待一秒后重新加入待下载列表
                    delay(1000)
                    onPostError(chapter, it)
                }.onCancel {
                    onCancel(chapterIndex)
                }.onFinally {
                    onFinally()
                }.let {
                    tasks.add(it)
                }
                return
            }
            val requestBook = book
            downloadGenerations[chapter.index] = generation
            WebBook.getContent(
                scope,
                bookSource,
                requestBook,
                chapter,
                context = context,
                start = CoroutineStart.LAZY,
                executeContext = context
            ).onSuccess { content ->
                onSuccess(chapter)
                downloadFinish(requestBook, generation, chapter, content)
            }.onError {
                onPreError(chapter, it)
                //出现错误等待一秒后重新加入待下载列表
                delay(1000)
                onPostError(chapter, it)
                downloadFinish(requestBook, generation, chapter, "获取正文失败\n${it.localizedMessage}")
            }.onCancel {
                onCancel(chapterIndex)
            }.onFinally {
                onFinally()
            }.apply {
                tasks.add(this)
            }.start()
        }

        private fun downloadAudio(
            scope: CoroutineScope,
            context: CoroutineContext,
            chapter: BookChapter,
        ) {
            waitDownloadSet.remove(chapter.index)
            AudioDownloadCache.getCachedChapter(bookSource, book, chapter)?.let {
                onSuccess(chapter)
                postEvent(EventBus.SAVE_CONTENT, Pair(book, chapter))
                onFinally()
                return
            }
            onDownloadSet.add(chapter.index)
            Coroutine.async(scope, context, executeContext = context) {
                val content = WebBook.getContentAwait(
                    bookSource = bookSource,
                    book = book,
                    bookChapter = chapter,
                    needSave = false,
                ).trim()
                AudioDownloadCache.cacheChapter(bookSource, book, chapter, content)
            }.onSuccess {
                onSuccess(chapter)
                postEvent(EventBus.SAVE_CONTENT, Pair(book, chapter))
            }.onError {
                onPreError(chapter, it)
                delay(1000)
                onPostError(chapter, it)
            }.onCancel {
                onCancel(chapter.index)
            }.onFinally {
                onFinally()
            }.let {
                tasks.add(it)
            }
        }

        suspend fun downloadAwait(
            chapter: BookChapter,
            generation: Long? = ReadBook.captureLoadGeneration(chapter.bookUrl)
        ): String {
            val requestBook = book
            val requestSource = bookSource
            // Serialize await callers with foreground/background downloads too.
            while (true) {
                val completion = synchronized(this) {
                    if (onDownloadSet.contains(chapter.index)) {
                        downloadCompletions.getOrPut(chapter.index) { CompletableDeferred() }
                    } else {
                        onDownloadSet.add(chapter.index)
                        waitDownloadSet.remove(chapter.index)
                        null
                    }
                }
                if (completion == null) break
                completion.await()
            }
            try {
                val content = BookHelp.getContent(requestBook, chapter)
                    ?: WebBook.getContentAwait(requestSource, requestBook, chapter)
                onSuccess(chapter)
                ReadBook.recordDownload(requestBook.bookUrl, generation, chapter.index, false)
                return content
            } catch (e: Exception) {
                if (e is CancellationException) {
                    onCancel(chapter.index)
                    throw e
                }
                onError(chapter, e)
                ReadBook.recordDownload(requestBook.bookUrl, generation, chapter.index, true)
                return "获取正文失败\n${e.localizedMessage}"
            } finally {
                postEvent(EventBus.UP_DOWNLOAD, requestBook.bookUrl)
            }
        }

        @Synchronized
        fun download(
            scope: CoroutineScope,
            chapter: BookChapter,
            semaphore: Semaphore?,
            resetPageOffset: Boolean = false,
            generation: Long? = ReadBook.captureLoadGeneration(chapter.bookUrl)
        ) {
            val requestBook = book
            if (onDownloadSet.contains(chapter.index)) {
                // Same reader request is already delivered by the original callback.
                if (generation != null && downloadGenerations[chapter.index] == generation) return
                val completion = downloadCompletions.getOrPut(chapter.index) { CompletableDeferred() }
                Coroutine.async(scope) {
                    completion.await()
                    ensureActive()
                    if (generation != null) {
                        ReadBook.resumePendingContent(requestBook, generation, chapter.index, resetPageOffset)
                    }
                }.onError {
                    if (it !is CancellationException) AppLog.put("等待正文下载失败", it)
                }
                return
            }
            downloadGenerations[chapter.index] = generation
            onDownloadSet.add(chapter.index)
            waitDownloadSet.remove(chapter.index)
            WebBook.getContent(
                scope,
                bookSource,
                requestBook,
                chapter,
                start = CoroutineStart.LAZY,
                executeContext = IO,
                semaphore = semaphore
            ).onSuccess { content ->
                onSuccess(chapter)
                ReadBook.recordDownload(requestBook.bookUrl, generation, chapter.index, false)
                downloadFinish(requestBook, generation, chapter, content, resetPageOffset)
            }.onError {
                onError(chapter, it)
                ReadBook.recordDownload(requestBook.bookUrl, generation, chapter.index, true)
                downloadFinish(requestBook, generation, chapter, "获取正文失败\n${it.localizedMessage}", resetPageOffset)
            }.onCancel {
                onCancel(chapter.index)
                downloadFinish(requestBook, generation, chapter, "download canceled", resetPageOffset, true)
            }.onFinally {
                postEvent(EventBus.UP_DOWNLOAD, requestBook.bookUrl)
            }.start()
        }

        private fun downloadFinish(
            requestBook: Book,
            generation: Long?,
            chapter: BookChapter,
            content: String,
            resetPageOffset: Boolean = false,
            canceled: Boolean = false
        ) {
            if (generation != null) {
                ReadBook.contentLoadFinish(
                    requestBook, chapter, content,
                    resetPageOffset = resetPageOffset,
                    canceled = canceled,
                    generation = generation
                )
            }
        }

    }

}
