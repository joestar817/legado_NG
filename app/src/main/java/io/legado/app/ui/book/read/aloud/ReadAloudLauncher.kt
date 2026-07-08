package io.legado.app.ui.book.read.aloud

import android.content.Context
import android.app.Activity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalModified
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.localBook.LocalBook
import io.legado.app.model.webBook.WebBook
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.startActivity
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
        chapterChanged: Boolean
    ): Boolean {
        return withContext(IO) {
            kotlin.runCatching {
                ReadBook.inBookshelf = inBookshelf
                ReadBook.chapterChanged = chapterChanged
                initBookState(book)
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

    suspend fun loadCurrentChapter(context: Context): Boolean {
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
                ReadBook.saveRead()
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

    @Suppress("DEPRECATION")
    fun openPlayer(context: Context, autoStart: Boolean = false) {
        context.startActivity<ReadAloudPlayerActivity> {
            putExtra(EXTRA_AUTO_START, autoStart)
        }
        (context as? Activity)?.overridePendingTransition(0, 0)
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
}
