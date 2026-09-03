package io.legado.app.ui.book.manage

import android.app.Application
import androidx.lifecycle.MutableLiveData
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isLocal
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
import io.legado.app.utils.sendValue
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.writeToOutputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import java.io.File


class BookshelfManageViewModel(application: Application) : BaseViewModel(application) {
    var groupId: Long = -1L
    val batchChangeSourceState = MutableLiveData<Boolean>()
    val batchChangeSourceProcessLiveData = MutableLiveData<String>()
    var batchChangeSourceCoroutine: Coroutine<Unit>? = null
    val cacheChapterCountLiveData = MutableLiveData<String>()
    val cacheChapters = hashMapOf<String, HashSet<String>>()
    private var loadCacheFilesCoroutine: Coroutine<Unit>? = null

    fun loadCacheFiles(books: List<Book>) {
        loadCacheFilesCoroutine?.cancel()
        loadCacheFilesCoroutine = execute {
            books.forEach { book ->
                if (!book.isLocal && !cacheChapters.contains(book.bookUrl)) {
                    val chapterCaches = hashSetOf<String>()
                    val cacheNames = if (book.isAudio) {
                        appDb.bookSourceDao.getBookSource(book.origin)?.let {
                            AudioDownloadCache.getCachedChapterFileNames(it, book)
                        }.orEmpty()
                    } else {
                        BookHelp.getChapterFiles(book)
                    }
                    if (cacheNames.isNotEmpty()) {
                        appDb.bookChapterDao.getChapterList(book.bookUrl).forEach { chapter ->
                            if (cacheNames.contains(chapter.getFileName()) ||
                                (!book.isAudio && chapter.isVolume)
                            ) {
                                chapterCaches.add(chapter.url)
                            }
                        }
                    }
                    cacheChapters[book.bookUrl] = chapterCaches
                    cacheChapterCountLiveData.sendValue(book.bookUrl)
                }
                ensureActive()
            }
        }
    }

    fun addCachedChapter(bookUrl: String, chapterUrl: String) {
        cacheChapters[bookUrl]?.let { chapters ->
            if (chapters.add(chapterUrl)) {
                cacheChapterCountLiveData.sendValue(bookUrl)
            }
        }
    }

    fun upCanUpdate(books: List<Book>, canUpdate: Boolean) {
        execute {
            val array = Array(books.size) {
                books[it].copy(canUpdate = canUpdate).apply {
                    if (!canUpdate) {
                        removeType(BookType.updateError)
                    }
                }
            }
            appDb.bookDao.update(*array)
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
                .filterNot { it.isLocal }
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
                if (book.isLocal) return@forEachIndexed
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
            books.filterNot { it.isLocal }.forEach { book ->
                cacheChapters[book.bookUrl] = hashSetOf()
                cacheChapterCountLiveData.sendValue(book.bookUrl)
            }
            context.toastOnUi(R.string.clear_cache_success)
        }
    }

}
