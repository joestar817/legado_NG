package io.legado.app.ui.book.read.aloud

import android.content.Intent
import android.content.Context
import android.app.Activity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.help.tts.TtsEngineStore
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException

object ReadAloudLauncher {

    suspend fun prepareState(
        book: Book,
        inBookshelf: Boolean,
        chapterChanged: Boolean,
        restoreProgress: Boolean = false,
        restoreChapterIndex: Int = book.durChapterIndex,
        restorePosition: Int = book.durChapterPos,
        restoreGuard: () -> Boolean = { true },
    ): Boolean {
        return withContext(IO) {
            if (restoreProgress && !restoreGuard()) return@withContext false
            kotlin.runCatching {
                ReadBook.inBookshelf = inBookshelf
                ReadBook.chapterChanged = chapterChanged
                if (restoreProgress) {
                    // A listening bookmark may differ within the same chapter from reading progress.
                    ReadBook.resetData(book)
                    ReadBook.durChapterIndex = restoreChapterIndex
                    ReadBook.durChapterPos = restorePosition
                    ReadBook.clearTextChapter()
                    ReadBook.upMsg(null)
                    true
                } else {
                    initBookState(book)
                }
            }.onFailure {
                val msg = "初始化听书失败\n${it.localizedMessage}"
                ReadBook.upMsg(msg)
                AppLog.put(msg, it)
            }.getOrDefault(false)
        }
    }

    private suspend fun initReadLayout(context: Context, book: Book) = withContext(Main) {
        ReadBook.upReadBookConfig(book)
        val metrics = context.resources.displayMetrics
        ChapterProvider.upViewSize(metrics.widthPixels, metrics.heightPixels)
        ChapterProvider.upStyle()
    }

    suspend fun loadCurrentChapter(context: Context, saveProgress: Boolean = true): Boolean {
        val book = ReadBook.book ?: run {
            ReadBook.upMsg("当前书籍为空")
            return false
        }
        initReadLayout(context, book)
        return withContext(IO) {
            kotlin.runCatching {
                if (!book.isLocal && book.tocUrl.isEmpty() && !loadBookInfo(book)) {
                    return@withContext false
                }
                if (book.isLocal && !checkLocalBookFileExist(book)) {
                    return@withContext false
                }
                if ((ReadBook.chapterSize == 0 || book.isLocalModified()) && !loadChapterList(book)) {
                    return@withContext false
                }
                ReadBook.upMsg(null)
                if (ReadBook.curTextChapter?.isCompleted != true) {
                    ReadBook.loadContentAwait(
                        ReadBook.durChapterIndex,
                        upContent = false,
                        resetPageOffset = true
                    ) {
                        ReadBook.bookSource?.let {
                            SourceCallBack.callBackBook(
                                SourceCallBack.START_READ,
                                it,
                                book,
                                ReadBook.curTextChapter?.chapter
                            )
                        }
                    }
                }
                if (saveProgress) ReadBook.saveRead()
                if (ReadBook.curTextChapter?.isCompleted != true) {
                    ReadBook.upMsg("加载正文失败")
                    return@withContext false
                }
                true
            }.onFailure {
                val msg = "加载正文失败\n${it.localizedMessage}"
                ReadBook.upMsg(msg)
                AppLog.put(msg, it)
            }.getOrDefault(false)
        }
    }

    /** Dispatches the existing service without opening a player or changing the bookmark. */
    internal suspend fun playPreparedHistory(
        context: Context,
        expectedBook: Book,
        expectedChapterIndex: Int,
        restoreGuard: () -> Boolean,
    ): Boolean = withContext(Main.immediate) {
        fun isCurrent(): Boolean = restoreGuard() &&
            ReadBook.book === expectedBook && ReadBook.durChapterIndex == expectedChapterIndex

        if (!isCurrent()) return@withContext false
        val hasEnabledEngine = withContext(IO) { TtsEngineStore.hasEnabledEngine() }
        if (!isCurrent()) return@withContext false
        if (!hasEnabledEngine) {
            context.toastOnUi("未启用朗读引擎")
            return@withContext false
        }

        val loaded = loadCurrentChapter(context, saveProgress = false)
        if (!isCurrent()) return@withContext false
        val chapter = ReadBook.curTextChapter?.takeIf {
            it.isCompleted && it.chapter.bookUrl == expectedBook.bookUrl &&
                it.chapter.index == expectedChapterIndex
        }
        if (!loaded || chapter == null) {
            context.toastOnUi(ReadBook.msg ?: "加载正文失败")
            return@withContext false
        }

        withContext(IO) { ReadAloud.refreshReadAloudClass() }
        if (!isCurrent() || ReadBook.curTextChapter !== chapter || !chapter.isCompleted ||
            chapter.chapter.bookUrl != expectedBook.bookUrl || chapter.chapter.index != expectedChapterIndex
        ) return@withContext false
        val startPos = (ReadBook.durChapterPos - chapter.getReadLength(ReadBook.durPageIndex))
            .coerceAtLeast(0)
        ReadBook.readAloud(play = true, startPos = startPos, engineVerified = true)
        // The service publishes preparation and real playback through its existing events.
        true
    }

    @Suppress("DEPRECATION")
    fun openPlayer(context: Context, autoStart: Boolean = false) {
        context.startActivity<ReadAloudPlayerActivity> {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(EXTRA_AUTO_START, autoStart)
            putExtra(EXTRA_BOOK_URL, ReadBook.book?.bookUrl)
        }
        (context as? Activity)?.overridePendingTransition(0, 0)
    }

    fun markPlayerDerived(intent: Intent) {
        intent.putExtra(EXTRA_SUPPRESS_MINI_PLAYER, true)
    }

    private fun initBookState(book: Book): Boolean {
        val isSameBook = ReadBook.book?.bookUrl == book.bookUrl
        if (isSameBook) {
            ReadBook.upData(book)
        } else {
            ReadBook.resetData(book)
        }
        ReadBook.upMsg(null)
        return true
    }

    private fun checkLocalBookFileExist(book: Book): Boolean {
        return try {
            LocalBook.getBookInputStream(book)
            true
        } catch (e: Throwable) {
            ReadBook.upMsg("打开本地书籍出错: ${e.localizedMessage}")
            false
        }
    }

    private suspend fun loadBookInfo(book: Book): Boolean {
        val source = ReadBook.bookSource ?: return true
        return try {
            WebBook.getBookInfoAwait(source, book, canReName = false)
            true
        } catch (e: Throwable) {
            currentCoroutineContext().ensureActive()
            ReadBook.upMsg("详情页出错: ${e.localizedMessage}")
            false
        }
    }

    private suspend fun loadChapterList(book: Book): Boolean {
        if (book.isLocal) {
            return kotlin.runCatching {
                LocalBook.getChapterList(book).let {
                    appDb.bookChapterDao.delByBook(book.bookUrl)
                    appDb.bookChapterDao.insert(*it.toTypedArray())
                    appDb.bookDao.update(book)
                    ReadBook.onChapterListUpdated(book)
                }
                true
            }.onFailure {
                when (it) {
                    is SecurityException, is FileNotFoundException -> {
                        ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                    }

                    else -> {
                        AppLog.put("LoadTocError:${it.localizedMessage}", it)
                        ReadBook.upMsg("LoadTocError:${it.localizedMessage}")
                    }
                }
            }.getOrDefault(false)
        }
        ReadBook.bookSource?.let { source ->
            val oldBook = book.copy()
            WebBook.getChapterListAwait(source, book, true)
                .onSuccess { cList ->
                    if (oldBook.bookUrl == book.bookUrl) {
                        appDb.bookDao.update(book)
                    } else {
                        appDb.bookDao.replace(oldBook, book)
                        BookHelp.updateCacheFolder(oldBook, book)
                    }
                    appDb.bookChapterDao.delByBook(oldBook.bookUrl)
                    appDb.bookChapterDao.insert(*cList.toTypedArray())
                    ReadBook.onChapterListUpdated(book)
                    return true
                }.onFailure {
                    currentCoroutineContext().ensureActive()
                    ReadBook.upMsg("加载目录失败")
                    return false
                }
        }
        if (ReadBook.chapterSize <= 0) {
            ReadBook.upMsg("加载目录失败")
            return false
        }
        return true
    }

    const val EXTRA_AUTO_START = "autoStartReadAloud"
    const val EXTRA_BOOK_URL = "readAloudBookUrl"
    const val EXTRA_SUPPRESS_MINI_PLAYER = "suppressReadAloudMiniPlayer"
}
