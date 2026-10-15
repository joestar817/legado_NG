package io.legado.app.ui.book.manage

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isLocal
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MAX
import io.legado.app.help.config.PACE_INTERVAL_DEFAULT_MIN
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.model.BookCacheManager
import io.legado.app.model.CacheBook
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.toastOnUi
import java.io.File

/** 已占用磁盘缓存的一本书。 */
internal data class BookshelfCacheEntry(
    val bookUrl: String,
    val name: String,
    val author: String,
    val sizeBytes: Long,
    /** 已缓存章节数（按 book_cache 下的 .nb 正文文件统计）。 */
    val chapterCount: Int,
    /** 该书总章节数，用于算缓存进度。 */
    val totalChapterNum: Int,
    val isAudio: Boolean,
    val isEpub: Boolean,
)

/** 书目列表的缓存筛选维度。 */
internal enum class BookshelfCacheFilter {
    ALL,
    CACHED,
    UNCACHED,
}

/**
 * 判断一本书是否落在当前筛选维度内。
 *
 * 「已缓存」以该书在 `book_cache` 下确有占用为准(见 [BookshelfCacheState.sizeByBookUrl]),
 * 因此本地书与未缓存书一样会被 CACHED 排除。
 */
internal fun BookshelfCacheFilter.matches(book: Book, state: BookshelfCacheState): Boolean =
    when (this) {
        BookshelfCacheFilter.ALL -> true
        BookshelfCacheFilter.CACHED -> state.sizeByBookUrl.containsKey(book.bookUrl)
        BookshelfCacheFilter.UNCACHED -> !state.sizeByBookUrl.containsKey(book.bookUrl)
    }

internal data class BookshelfCacheState(
    val loading: Boolean = true,
    val books: List<BookshelfCacheEntry> = emptyList(),
    val bookBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val epubBytes: Long = 0L,
    val appBytes: Long = 0L,
    /** 是否由用户手动指定缓存间隔；false 时使用内置默认节奏。 */
    val paceManualEnabled: Boolean = false,
    val paceMinInterval: Int = PACE_INTERVAL_DEFAULT_MIN,
    val paceMaxInterval: Int = PACE_INTERVAL_DEFAULT_MAX,
    /** 后台缓存任务是否在跑(见 `CacheBook.isRun`)。 */
    val downloading: Boolean = false,
    /** 缓存任务是否被用户暂停(见 `CacheBook.isUserPaused`)。 */
    val paused: Boolean = false,
    /** 每本书的任务状态,驱动书卡上的环形缓存按钮(见 `CacheBook.stateOf`)。 */
    val bookStates: Map<String, CacheBook.BookState> = emptyMap(),
    /** 正在缓存的书(书名 → 已完成/总数),驱动概览条与通知栏。 */
    val activeBooks: List<CacheBook.BookProgress> = emptyList(),
) {
    val totalBytes: Long get() = bookBytes + audioBytes + epubBytes + appBytes

    /** 已缓存书籍的字节数，是「已缓存 / 未缓存」筛选与卡片进度条的判据。 */
    val sizeByBookUrl: Map<String, Long> by lazy {
        books.associate { it.bookUrl to it.sizeBytes }
    }

    val cachedChapterTotal: Int by lazy { books.sumOf { it.chapterCount } }
}

/**
 * 书架管理页内嵌的缓存管理面板的数据来源。
 *
 * 统计口径与磁盘布局一一对应:
 * - 书籍正文/图片/音频清单 = `externalFiles/book_cache/<book.getFolderName()>`
 *   (见 `BookHelp.kt:59` 的 `cacheFolderName` 与 `BookHelp.kt:64` 的 `cachePath`)
 * - 有声书音频实体 = `externalFiles/audio_cache`
 *   (见 `ExoPlayerHelper.kt:121` 的 `SimpleCache(File(appCtx.externalFiles, "audio_cache"), ...)`)
 * - 本地书籍解析缓存 = `externalFiles/epub`
 *   (见 `BookHelp.kt:61` 的 `cacheEpubFolderName`)
 * - 应用缓存 = `context.cacheDir`
 */
internal class BookshelfCacheViewModel(application: Application) : BaseViewModel(application) {

    var state by mutableStateOf(
        BookshelfCacheState(
            paceManualEnabled = AppConfig.cachePaceManualEnabled,
            paceMinInterval = AppConfig.readingPaceMinInterval,
            paceMaxInterval = AppConfig.readingPaceMaxInterval,
        )
    )
        private set

    fun refresh() {
        // 带上当前节奏设置,避免统计刷新时把用户正在拖动的草稿重置掉。
        val previous = state
        execute {
            scan(previous)
        }.onSuccess {
            state = it
        }
    }

    /**
     * 从引擎同步缓存任务的运行/暂停态。
     *
     * 运行态的唯一事实源是 [CacheBook],此处只做镜像;Activity 观察
     * `EventBus.UP_DOWNLOAD`(引擎与服务都会投递)后调用。
     */
    fun syncDownloadState() {
        val running = CacheBook.isRun
        val isPaused = CacheBook.isUserPaused
        val states = CacheBook.bookStates
        val active = CacheBook.activeBookProgress
        if (state.downloading != running || state.paused != isPaused ||
            state.bookStates != states || state.activeBooks != active
        ) {
            state = state.copy(
                downloading = running,
                paused = isPaused,
                bookStates = states,
                activeBooks = active,
            )
        }
    }

    /**
     * 单独暂停/继续一本书的缓存。
     *
     * 与 [pauseCache] 不同: 后者停整个派发队列,前者只让这本书退出派发,
     * 其它书照常下载。
     */
    fun setBookPaused(bookUrl: String, paused: Boolean) {
        CacheBook.setBookPaused(bookUrl, paused)
        syncDownloadState()
    }

    /** 为单本书排队缓存(书卡环形按钮的主操作)。 */
    fun cacheBook(book: Book, start: Int, end: Int) {
        CacheBook.start(context, book, start, end)
        syncDownloadState()
    }

    /** 暂停派发新章节;已在下载中的章节会自然跑完。 */
    fun pauseCache() {
        CacheBook.setUserPaused(true)
        syncDownloadState()
    }

    fun resumeCache() {
        CacheBook.setUserPaused(false)
        syncDownloadState()
    }

    /** 停止整个缓存任务,等价于通知栏的「取消」。 */
    fun stopCache() {
        CacheBook.stop(context)
        syncDownloadState()
    }

    /** 开关缓存间隔的手动设置；关闭时回到内置默认节奏。 */
    fun setPaceManualEnabled(enabled: Boolean) {
        AppConfig.cachePaceManualEnabled = enabled
        state = state.copy(paceManualEnabled = enabled)
    }

    fun setPaceMinIntervalDraft(value: Int) {
        state = state.copy(paceMinInterval = value)
    }

    fun setPaceMaxIntervalDraft(value: Int) {
        state = state.copy(paceMaxInterval = value)
    }

    /** 落盘手动间隔,并保证最小间隔不大于最大间隔。 */
    fun savePaceIntervals() {
        var min = state.paceMinInterval
        var max = state.paceMaxInterval
        if (min > max) {
            val swap = min
            min = max
            max = swap
        }
        AppConfig.readingPaceMinInterval = min
        AppConfig.readingPaceMaxInterval = max
        state = state.copy(paceMinInterval = min, paceMaxInterval = max)
    }

    /** 与旧「清理缓存」入口完全等价: 书缓存 + cacheDir + 无关联角色卡。 */
    fun clearAll() {
        execute {
            BookCacheManager.clearAll()
            FileUtils.delete(context.cacheDir.absolutePath)
            clearUnlinkedCharacterProfiles()
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
            refresh()
        }
    }

    /**
     * 只清除已删除书籍留下的残留缓存与解压临时文件。
     *
     * 刻意不复用 `BookHelp.clearInvalidCache()`: 那个函数在 `BookHelp.kt:122-124`
     * 还会顺带删掉 `filesDir` 下的 shareBookSource.json / shareRssSource.json /
     * books.json（用户导出的分享文件），副作用超出「清理残留」的语义。
     */
    fun clearInvalid() {
        execute {
            val linkedFolderNames = HashSet<String>()
            val linkedOriginNames = HashSet<String>()
            appDb.bookDao.all.forEach { book ->
                linkedFolderNames.add(book.getFolderName())
                if (book.isEpub) linkedOriginNames.add(book.originName)
            }
            // 书籍正文/图片/音频清单: externalFiles/book_cache/<folderName>
            File(BookHelp.cachePath).listFiles()?.forEach { child ->
                if (child.name !in linkedFolderNames) {
                    FileUtils.delete(child.absolutePath)
                }
            }
            // 本地书籍解析缓存: externalFiles/epub/<originName>（见 BookHelp.kt:61）
            File(context.externalFiles, "epub").listFiles()?.forEach { child ->
                if (child.name !in linkedOriginNames) {
                    FileUtils.delete(child.absolutePath)
                }
            }
            FileUtils.delete(ArchiveUtils.TEMP_PATH)
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
            refresh()
        }
    }

    fun clearBook(bookUrl: String) {
        execute {
            appDb.bookDao.getBook(bookUrl)?.let(BookCacheManager::clear)
        }.onSuccess {
            context.toastOnUi(R.string.clear_cache_success)
            refresh()
        }
    }

    private fun clearUnlinkedCharacterProfiles() {
        val linkedWorkKeys = appDb.bookDao.all
            .map { BookCharacterProfile.workKey(it.name, it.author) }
            .toHashSet()
        appDb.bookCharacterDao.getProfiles()
            .filterNot { it.workKey in linkedWorkKeys }
            .forEach { appDb.bookCharacterDao.deleteProfile(it.workKey) }
    }

    private fun scan(previous: BookshelfCacheState): BookshelfCacheState {
        val entries = ArrayList<BookshelfCacheEntry>()
        var bookBytes = 0L
        for (book in appDb.bookDao.all) {
            val dir = File(BookHelp.cachePath, book.getFolderName())
            val folderSize = dir.sizeBytes()
            // 有声书音频实体不在 book_cache 里,而是 externalFiles/audio_cache,
            // 按 audiobook:<md5(folderName)>: 前缀归属到这本书。
            val audioSize = if (book.isAudio) AudioDownloadCache.bookCacheSize(book) else 0L
            val size = folderSize + audioSize
            if (size <= 0L) continue
            bookBytes += folderSize
            val chapterCount = dir.listFiles()
                ?.count { it.isFile && it.extension == "nb" }
                ?: 0
            entries += BookshelfCacheEntry(
                bookUrl = book.bookUrl,
                name = book.name,
                author = book.author,
                sizeBytes = size,
                chapterCount = chapterCount,
                totalChapterNum = book.totalChapterNum,
                isAudio = book.isAudio,
                isEpub = book.isEpub,
            )
        }
        entries.sortByDescending { it.sizeBytes }
        return BookshelfCacheState(
            loading = false,
            books = entries,
            bookBytes = bookBytes,
            audioBytes = File(context.externalFiles, "audio_cache").sizeBytes(),
            epubBytes = File(context.externalFiles, "epub").sizeBytes(),
            appBytes = context.cacheDir.sizeBytes(),
            paceManualEnabled = previous.paceManualEnabled,
            paceMinInterval = previous.paceMinInterval,
            paceMaxInterval = previous.paceMaxInterval,
            downloading = previous.downloading,
            paused = previous.paused,
            bookStates = CacheBook.bookStates,
            activeBooks = CacheBook.activeBookProgress,
        )
    }
}

private fun File.sizeBytes(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    var total = 0L
    listFiles()?.forEach { child ->
        total += if (child.isDirectory) child.sizeBytes() else child.length()
    }
    return total
}
